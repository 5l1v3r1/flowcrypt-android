/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.loader;

import android.content.Context;
import android.net.Uri;

import com.flowcrypt.email.api.email.EmailUtil;
import com.flowcrypt.email.api.email.gmail.GmailApiHelper;
import com.flowcrypt.email.api.email.protocol.OpenStoreHelper;
import com.flowcrypt.email.api.email.protocol.SmtpProtocolUtil;
import com.flowcrypt.email.api.retrofit.ApiHelper;
import com.flowcrypt.email.api.retrofit.ApiService;
import com.flowcrypt.email.api.retrofit.node.NodeCallsExecutor;
import com.flowcrypt.email.api.retrofit.request.model.InitialLegacySubmitModel;
import com.flowcrypt.email.api.retrofit.request.model.TestWelcomeModel;
import com.flowcrypt.email.api.retrofit.response.attester.InitialLegacySubmitResponse;
import com.flowcrypt.email.api.retrofit.response.attester.TestWelcomeResponse;
import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails;
import com.flowcrypt.email.database.dao.KeysDao;
import com.flowcrypt.email.database.dao.source.AccountDao;
import com.flowcrypt.email.database.dao.source.ActionQueueDaoSource;
import com.flowcrypt.email.database.dao.source.KeysDaoSource;
import com.flowcrypt.email.database.dao.source.UserIdEmailsKeysDaoSource;
import com.flowcrypt.email.js.PgpContact;
import com.flowcrypt.email.js.PgpKey;
import com.flowcrypt.email.js.core.Js;
import com.flowcrypt.email.model.KeyDetails;
import com.flowcrypt.email.model.results.LoaderResult;
import com.flowcrypt.email.security.KeyStoreCryptoManager;
import com.flowcrypt.email.service.actionqueue.actions.BackupPrivateKeyToInboxAction;
import com.flowcrypt.email.service.actionqueue.actions.RegisterUserPublicKeyAction;
import com.flowcrypt.email.service.actionqueue.actions.SendWelcomeTestEmailAction;
import com.flowcrypt.email.util.exception.ExceptionUtil;
import com.google.android.gms.common.util.CollectionUtils;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListSendAsResponse;
import com.google.api.services.gmail.model.SendAs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;

import androidx.annotation.Nullable;
import androidx.loader.content.AsyncTaskLoader;
import retrofit2.Response;

/**
 * This loader does job of creating a private key and returns the private key long id as result.
 *
 * @author DenBond7
 * Date: 12.01.2018.
 * Time: 12:36.
 * E-mail: DenBond7@gmail.com
 */
public class CreatePrivateKeyAsyncTaskLoader extends AsyncTaskLoader<LoaderResult> {

  private static final int DEFAULT_KEY_SIZE = 2048;

  private final String passphrase;
  private final AccountDao account;
  private boolean isActionStarted;
  private LoaderResult data;

  public CreatePrivateKeyAsyncTaskLoader(Context context, AccountDao account, String passphrase) {
    super(context);
    this.account = account;
    this.passphrase = passphrase;
  }

  @Override
  public void onStartLoading() {
    if (data != null) {
      deliverResult(data);
    } else {
      if (!isActionStarted) {
        forceLoad();
      }
    }
  }

  @Override
  public LoaderResult loadInBackground() {
    String email = account.getEmail();
    isActionStarted = true;
    PgpKey pgpKey = null;
    try {
      pgpKey = createPgpKey();

      if (pgpKey == null) {
        return new LoaderResult(null, new NullPointerException("The generated private key is null!"));
      }

      KeyStoreCryptoManager manager = new KeyStoreCryptoManager(getContext());

      List<NodeKeyDetails> nodeKeyDetailsList = NodeCallsExecutor.parseKeys(pgpKey.armor());
      if (CollectionUtils.isEmpty(nodeKeyDetailsList) || nodeKeyDetailsList.size() != 1) {
        throw new IllegalStateException("Parse keys error");
      }

      NodeKeyDetails nodeKeyDetails = nodeKeyDetailsList.get(0);
      KeysDao keysDao = KeysDao.generateKeysDao(manager, KeyDetails.Type.NEW, nodeKeyDetails, passphrase);

      Uri uri = new KeysDaoSource().addRow(getContext(), keysDao);

      if (uri == null) {
        return new LoaderResult(null, new NullPointerException("Cannot save the generated private key"));
      }

      new UserIdEmailsKeysDaoSource().addRow(getContext(), pgpKey.getLongid(), pgpKey.getPrimaryUserId().getEmail());

      ActionQueueDaoSource daoSource = new ActionQueueDaoSource();

      if (!saveCreatedPrivateKeyAsBackupToInbox(pgpKey)) {
        daoSource.addAction(getContext(), new BackupPrivateKeyToInboxAction(email, pgpKey.getLongid()));
      }

      if (!registerUserPublicKey(pgpKey)) {
        daoSource.addAction(getContext(), new RegisterUserPublicKeyAction(email, pgpKey.toPublic().armor()));
      }

      if (!requestingTestMsgWithNewPublicKey(pgpKey)) {
        daoSource.addAction(getContext(), new SendWelcomeTestEmailAction(email, pgpKey.toPublic().armor()));
      }

      return new LoaderResult(pgpKey.getLongid(), null);
    } catch (Exception e) {
      e.printStackTrace();
      new KeysDaoSource().removeKey(getContext(), pgpKey);
      new UserIdEmailsKeysDaoSource().removeKey(getContext(), pgpKey);
      ExceptionUtil.handleError(e);
      return new LoaderResult(null, e);
    }
  }

  @Override
  public void deliverResult(@Nullable LoaderResult data) {
    this.data = data;
    super.deliverResult(data);
  }

  /**
   * Perform a backup of the armored key in INBOX.
   *
   * @return true if message was send.
   */
  private boolean saveCreatedPrivateKeyAsBackupToInbox(PgpKey pgpKey) {
    try {
      Session session = OpenStoreHelper.getAccountSess(getContext(), account);
      Transport transport = SmtpProtocolUtil.prepareSmtpTransport(getContext(), session, account);
      Message msg = EmailUtil.genMsgWithPrivateKeys(getContext(), account, session,
          EmailUtil.genBodyPartWithPrivateKey(account, pgpKey.armor()));
      transport.sendMessage(msg, msg.getAllRecipients());
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Create a private PGP key.
   *
   * @return Generated {@link PgpKey}
   * @throws IOException Some exceptions can be throw.
   */
  private PgpKey createPgpKey() throws Exception {
    PgpContact pgpContactMain = new PgpContact(account.getEmail(), account.getDisplayName());
    PgpContact[] pgpContacts;
    switch (account.getAccountType()) {
      case AccountDao.ACCOUNT_TYPE_GOOGLE:
        List<PgpContact> pgpContactList = new ArrayList<>();
        pgpContactList.add(pgpContactMain);
        Gmail gmail = GmailApiHelper.generateGmailApiService(getContext(), account);
        ListSendAsResponse aliases = gmail.users().settings().sendAs().list(GmailApiHelper.DEFAULT_USER_ID).execute();
        for (SendAs alias : aliases.getSendAs()) {
          if (alias.getVerificationStatus() != null) {
            pgpContactList.add(new PgpContact(alias.getSendAsEmail(), alias.getDisplayName()));
          }
        }
        pgpContacts = pgpContactList.toArray(new PgpContact[0]);
        break;

      default:
        pgpContacts = new PgpContact[]{pgpContactMain};
        break;
    }

    return new Js(getContext(), null).crypto_key_create(pgpContacts, DEFAULT_KEY_SIZE, passphrase);
  }

  /**
   * Registering a key with attester API.
   * Note: this will only be successful if it's the first time submitting a key for this email address, or if the
   * key being submitted has the same fingerprint as the one already recorded. If it's an error due to key
   * conflict, ignore the error.
   *
   * @param pgpKey A created PGP key.
   * @return true if no errors.
   */
  private boolean registerUserPublicKey(PgpKey pgpKey) {
    try {
      ApiService apiService = ApiHelper.getInstance(getContext()).getRetrofit().create(ApiService.class);
      InitialLegacySubmitModel model = new InitialLegacySubmitModel(account.getEmail(), pgpKey.toPublic().armor());
      Response<InitialLegacySubmitResponse> response = apiService.postInitialLegacySubmit(model).execute();
      InitialLegacySubmitResponse body = response.body();
      return body != null && (body.getApiError() == null ||
          !(body.getApiError().getCode() >= 400 && body.getApiError().getCode() < 500));
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Request a test email from FlowCrypt.
   *
   * @param pgpKey A created PGP key.
   * @return true if no errors.
   */
  private boolean requestingTestMsgWithNewPublicKey(PgpKey pgpKey) {
    try {
      ApiService apiService = ApiHelper.getInstance(getContext()).getRetrofit().create(ApiService.class);
      TestWelcomeModel model = new TestWelcomeModel(account.getEmail(), pgpKey.toPublic().armor());
      Response<TestWelcomeResponse> response = apiService.postTestWelcome(model).execute();

      TestWelcomeResponse testWelcomeResponse = response.body();
      return testWelcomeResponse != null && testWelcomeResponse.isSent();
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }
}
