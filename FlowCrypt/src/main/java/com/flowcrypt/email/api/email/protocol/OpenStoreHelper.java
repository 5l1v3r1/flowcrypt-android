/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.protocol;

import android.content.Context;

import com.flowcrypt.email.api.email.EmailUtil;
import com.flowcrypt.email.api.email.JavaEmailConstants;
import com.flowcrypt.email.api.email.gmail.GmailConstants;
import com.flowcrypt.email.api.email.model.AuthCredentials;
import com.flowcrypt.email.api.email.model.SecurityType;
import com.flowcrypt.email.database.dao.source.AccountDao;
import com.flowcrypt.email.util.LogsUtil;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.api.services.gmail.GmailScopes;
import com.sun.mail.gimap.GmailSSLStore;

import java.io.IOException;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

/**
 * This util class help generate Store classes.
 *
 * @author DenBond7
 * Date: 03.05.2017
 * Time: 18:00
 * E-mail: DenBond7@gmail.com
 */

public class OpenStoreHelper {

  private static final String TAG = OpenStoreHelper.class.getSimpleName();

  /**
   * Open and connect to the store using gimaps protocol.
   *
   * @param context     Interface to global information about an application environment.
   * @param token       An OAuth2 access token;
   * @param accountName An account name which use to create connection;
   * @return <tt>GmailSSLStore</tt> A GmailSSLStore object based on properties for
   * gimaps.
   */

  public static GmailSSLStore openAndConnectToGimapsStore(Context context, String token, String accountName) throws
      MessagingException {
    GmailSSLStore gmailSSLStore = (GmailSSLStore) getGmailSess(context).getStore(JavaEmailConstants.PROTOCOL_GIMAPS);
    gmailSSLStore.connect(GmailConstants.GMAIL_IMAP_SERVER, accountName, token);
    return gmailSSLStore;
  }

  /**
   * Open and connect to the store using gimaps protocol.
   *
   * @param context            Interface to global information about an application environment.
   * @param session            The sess which will be used for connection.
   * @param account            The object which contains information about an email account.
   * @param isResetTokenNeeded True if need reset token.
   * @return <tt>GmailSSLStore</tt> A GmailSSLStore object based on properties for
   * gimaps.
   */

  public static GmailSSLStore openAndConnectToGimapsStore(Context context, Session session, AccountDao account,
                                                          boolean isResetTokenNeeded)
      throws MessagingException, IOException, GoogleAuthException {
    GmailSSLStore gmailSSLStore;
    if (account != null) {
      gmailSSLStore = (GmailSSLStore) session.getStore(JavaEmailConstants.PROTOCOL_GIMAPS);
      if (account.getAccount() != null) {
        try {
          String token = GoogleAuthUtil.getToken(context, account.getAccount(),
              JavaEmailConstants.OAUTH2 + GmailScopes.MAIL_GOOGLE_COM);

          if (isResetTokenNeeded) {
            LogsUtil.d(TAG, "Refresh Gmail token");
            GoogleAuthUtil.clearToken(context, token);
            token = GoogleAuthUtil.getToken(context, account.getAccount(),
                JavaEmailConstants.OAUTH2 + GmailScopes.MAIL_GOOGLE_COM);
          }

          gmailSSLStore.connect(GmailConstants.GMAIL_IMAP_SERVER, account.getEmail(), token);
        } catch (AuthenticationFailedException e) {
          e.printStackTrace();
          if (!isResetTokenNeeded) {
            return openAndConnectToGimapsStore(context, session, account, true);
          } else throw e;
        }
      } else throw new NullPointerException("Account can't be a null!");
    } else throw new NullPointerException("AccountDao can't be a null!");
    return gmailSSLStore;
  }

  /**
   * Generate a sess for gimaps protocol.
   *
   * @param context Interface to global information about an application environment;
   * @return <tt>Session</tt> A new sess for gimaps protocol based on properties for gimaps.
   */
  public static Session getGmailSess(Context context) {
    Session session = Session.getInstance(PropertiesHelper.generateGmailProperties());
    session.setDebug(EmailUtil.hasEnabledDebug(context));
    return session;
  }

  /**
   * Generate a sess which will be use for download attachments.
   *
   * @param context Interface to global information about an application environment;
   * @param account An input {@link AccountDao};
   * @return <tt>Session</tt> A new sess based on for download attachments.
   */
  public static Session getAttsSess(Context context, AccountDao account) {
    if (account != null) {
      switch (account.getAccountType()) {
        case AccountDao.ACCOUNT_TYPE_GOOGLE:
          return getAttGmailSess(context);

        default:
          Session session = Session.getInstance(PropertiesHelper.genDownloadAttsProps(account.getAuthCreds()));
          session.setDebug(EmailUtil.hasEnabledDebug(context));
          return session;
      }
    } else throw new NullPointerException("AccountDao must not be a null!");
  }

  /**
   * Generate a sess for gimaps protocol which will be use for download attachments.
   *
   * @param context Interface to global information about an application environment;
   * @return <tt>Session</tt> A new sess for gimaps protocol based on properties for gimaps.
   */
  public static Session getAttGmailSess(Context context) {
    Session session = Session.getInstance(PropertiesHelper.genGmailAttsProperties());
    session.setDebug(EmailUtil.hasEnabledDebug(context));
    return session;
  }

  /**
   * Prepare {@link Session} object for the input {@link AccountDao}.
   *
   * @param context Interface to global information about an application environment;
   * @param account An input {@link AccountDao};
   * @return A generated {@link Session}
   */
  public static Session getAccountSess(Context context, AccountDao account) {
    if (account != null) {
      switch (account.getAccountType()) {
        case AccountDao.ACCOUNT_TYPE_GOOGLE:
          return getGmailSess(context);

        default:
          Session session = Session.getInstance(PropertiesHelper.genProps(account.getAuthCreds()));
          session.setDebug(EmailUtil.hasEnabledDebug(context));
          return session;
      }
    } else throw new NullPointerException("AccountDao must not be a null!");
  }

  public static Store openStore(Context context, AccountDao account, Session session) throws
      MessagingException, IOException, GoogleAuthException {
    if (account != null) {
      switch (account.getAccountType()) {
        case AccountDao.ACCOUNT_TYPE_GOOGLE:
          return openAndConnectToGimapsStore(context, session, account, false);

        default:
          AuthCredentials authCreds = account.getAuthCreds();
          Store store = authCreds.getImapOpt() == SecurityType.Option.NONE
              ? session.getStore(JavaEmailConstants.PROTOCOL_IMAP)
              : session.getStore(JavaEmailConstants.PROTOCOL_IMAPS);

          store.connect(authCreds.getImapServer(), authCreds.getUsername(), authCreds.getPassword());
          return store;
      }
    } else throw new NullPointerException("AccountDao must not be a null!");
  }
}
