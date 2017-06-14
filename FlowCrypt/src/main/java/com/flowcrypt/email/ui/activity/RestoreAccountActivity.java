/*
 * Business Source License 1.0 © 2017 FlowCrypt Limited (tom@cryptup.org). Use limitations apply. See https://github.com/FlowCrypt/flowcrypt-android/tree/master/src/LICENSE
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity;

import android.accounts.Account;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.View;

import com.flowcrypt.email.BuildConfig;
import com.flowcrypt.email.R;
import com.flowcrypt.email.model.results.LoaderResult;
import com.flowcrypt.email.ui.activity.base.BaseAuthenticationActivity;
import com.flowcrypt.email.ui.activity.fragment.RestoreAccountFragment;
import com.flowcrypt.email.ui.loader.LoadPrivateKeysFromMailAsyncTaskLoader;
import com.flowcrypt.email.util.UIUtil;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;

import java.util.List;

/**
 * This class described restore an account functionality.
 *
 * @author DenBond7
 *         Date: 05.01.2017
 *         Time: 01:37
 *         E-mail: DenBond7@gmail.com
 */
public class RestoreAccountActivity extends BaseAuthenticationActivity
        implements LoaderManager.LoaderCallbacks<LoaderResult> {

    public static final String KEY_EXTRA_PRIVATE_KEYS = BuildConfig.APPLICATION_ID
            + ".KEY_EXTRA_PRIVATE_KEYS";

    private View restoreAccountView;
    private View layoutProgress;
    private Account account;
    private List<String> privateKeys;
    private boolean isThrowErrorIfDuplicateFound;

    @Override
    public View getRootView() {
        return null;
    }

    @Override
    public void handleSignInResult(GoogleSignInResult googleSignInResult, boolean isOnStartCall) {
        if (googleSignInResult.isSuccess()) {
            GoogleSignInAccount googleSignInAccount = googleSignInResult.getSignInAccount();
            if (googleSignInAccount != null) {
                account = googleSignInAccount.getAccount();
                if (privateKeys == null) {
                    getSupportLoaderManager().initLoader(R.id.loader_id_load_gmail_backups, null,
                            this);
                } else {
                    showContent();
                    updateKeysOnRestoreAccountFragment();
                }
            }
        }
    }

    @Override
    public int getContentViewResourceId() {
        return R.layout.activity_restore_account;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null && getIntent().hasExtra(KEY_EXTRA_PRIVATE_KEYS)) {
            this.privateKeys = getIntent().getStringArrayListExtra(KEY_EXTRA_PRIVATE_KEYS);
            this.isThrowErrorIfDuplicateFound = true;
        }

        initViews();
    }

    @Override
    public Loader<LoaderResult> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case R.id.loader_id_load_gmail_backups:
                showProgress();
                return new LoadPrivateKeysFromMailAsyncTaskLoader(this, account);

            default:
                return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onLoadFinished(Loader<LoaderResult> loader, LoaderResult loaderResult) {
        switch (loader.getId()) {
            case R.id.loader_id_load_gmail_backups:
                if (loaderResult != null) {
                    if (loaderResult.getResult() != null) {
                        List<String> stringList = (List<String>) loaderResult.getResult();
                        if (stringList != null) {
                            if (!stringList.isEmpty()) {
                                this.privateKeys = stringList;
                                showContent();
                                updateKeysOnRestoreAccountFragment();
                            } else {
                                finish();
                                startActivity(new Intent(this, CreateOrImportKeyActivity.class));
                            }
                        } else {
                            showNoBackupsSnackbar();
                        }
                    } else {
                        showNoBackupsSnackbar();
                    }
                } else {
                    showNoBackupsSnackbar();
                }
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<LoaderResult> loader) {

    }

    /**
     * Update privateKeys list in RestoreAccountFragment.
     */
    private void updateKeysOnRestoreAccountFragment() {
        RestoreAccountFragment restoreAccountFragment = (RestoreAccountFragment)
                getSupportFragmentManager()
                        .findFragmentById(R.id.restoreAccountFragment);

        if (restoreAccountFragment != null) {
            restoreAccountFragment.setPrivateKeys(privateKeys, isThrowErrorIfDuplicateFound);
        }
    }

    /**
     * Shows the progress UI and hides the restore account form.
     */
    private void showProgress() {
        if (restoreAccountView != null) {
            restoreAccountView.setVisibility(View.GONE);
        }

        if (layoutProgress != null) {
            layoutProgress.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Shows restore account form and hides the progress UI.
     */
    private void showContent() {
        if (layoutProgress != null) {
            layoutProgress.setVisibility(View.GONE);
        }

        if (restoreAccountView != null) {
            restoreAccountView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Shows no backups snackbar and "refresh" action button.
     */
    private void showNoBackupsSnackbar() {
        UIUtil.showSnackbar(layoutProgress, getString(R.string.no_backups_found),
                getString(R.string.refresh), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getSupportLoaderManager().restartLoader(R.id
                                .loader_id_load_gmail_backups, null, RestoreAccountActivity
                                .this);
                    }
                });
    }

    private void initViews() {
        restoreAccountView = findViewById(R.id.restoreAccountView);
        layoutProgress = findViewById(R.id.layoutProgress);
    }

}
