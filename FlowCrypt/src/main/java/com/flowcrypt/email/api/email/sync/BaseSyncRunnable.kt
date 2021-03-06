/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.sync

import android.content.Context
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.protocol.OpenStoreHelper
import com.flowcrypt.email.api.email.sync.tasks.SyncTask
import com.flowcrypt.email.database.dao.source.AccountDao
import com.flowcrypt.email.util.LogsUtil
import com.flowcrypt.email.util.exception.ExceptionUtil
import com.flowcrypt.email.util.exception.ManualHandledException
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Store

/**
 * @author Denis Bondarenko
 *         Date: 10/17/19
 *         Time: 3:21 PM
 *         E-mail: DenBond7@gmail.com
 */
abstract class BaseSyncRunnable constructor(val account: AccountDao, val syncListener: SyncListener) : Runnable {
  //todo-denbond7 review this class
  protected val tag: String = javaClass.simpleName

  protected var sess: Session? = null
  protected var store: Store? = null

  /**
   * Check available connection to the store.
   * Must be called from non-main thread.
   *
   * @return trus if connected, false otherwise.
   */
  internal val isConnected: Boolean
    get() = store?.isConnected == true

  internal fun resetConnIfNeeded(task: SyncTask?) {
    val activeStore = store ?: return

    if (task?.resetConnection == true) {
      disconnect(task)
      return
    }

    if (account.authCreds != null) {
      if (!activeStore.urlName.username.equals(account.authCreds.username, ignoreCase = true)) {
        disconnect(task)
      }
    } else {
      throw ManualHandledException(syncListener.context.getString(R.string.device_not_supported_key_store_error))
    }
  }

  private fun disconnect(task: SyncTask?) {
    LogsUtil.d(tag, "Connection was reset!")
    task?.let { syncListener.onActionProgress(account, it.ownerKey, it.requestCode, R.id.progress_id_resetting_connection) }
    closeConn()
    sess = null
  }

  internal fun closeConn() {
    try {
      val activeStore = store ?: return
      activeStore.close()
    } catch (e: MessagingException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      LogsUtil.d(tag, "This exception occurred when we try disconnect from the store.")
    }

  }

  internal fun openConnToStore() {
    patchingSecurityProvider(syncListener.context)
    sess = OpenStoreHelper.getAccountSess(syncListener.context, account)
    store = OpenStoreHelper.openStore(syncListener.context, account, sess!!)
  }

  /**
   * To update a device's security provider, use the ProviderInstaller class.
   *
   *
   * When you call installIfNeeded(), the ProviderInstaller does the following:
   *  * If the device's Provider is successfully updated (or is already up-to-date), the method returns
   * normally.
   *  * If the device's Google Play services library is out of date, the method throws
   * GooglePlayServicesRepairableException. The app can then catch this exception and show the user an
   * appropriate dialog box to update Google Play services.
   *  * If a non-recoverable error occurs, the method throws GooglePlayServicesNotAvailableException to indicate
   * that it is unable to update the Provider. The app can then catch the exception and choose an appropriate
   * course of action, such as displaying the standard fix-it flow diagram.
   *
   *
   * If installIfNeeded() needs to install a new Provider, this can take anywhere from 30-50 milliseconds (on
   * more recent devices) to 350 ms (on older devices). If the security provider is already up-to-date, the
   * method takes a negligible amount of time.
   *
   *
   * Details here https://developer.android.com/training/articles/security-gms-provider.html#patching
   *
   * @param context Interface to global information about an application environment;
   */
  private fun patchingSecurityProvider(context: Context) {
    try {
      ProviderInstaller.installIfNeeded(context)
    } catch (e: GooglePlayServicesRepairableException) {
      e.printStackTrace()
    } catch (e: GooglePlayServicesNotAvailableException) {
      e.printStackTrace()
    }
  }
}