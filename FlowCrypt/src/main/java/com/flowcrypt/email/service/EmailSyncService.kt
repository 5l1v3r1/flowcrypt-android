/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.OperationApplicationException
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.FoldersManager
import com.flowcrypt.email.api.email.JavaEmailConstants
import com.flowcrypt.email.api.email.MsgsCacheManager
import com.flowcrypt.email.api.email.model.LocalFolder
import com.flowcrypt.email.api.email.sync.EmailSyncManager
import com.flowcrypt.email.api.email.sync.SyncErrorTypes
import com.flowcrypt.email.api.email.sync.SyncListener
import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.database.dao.source.AccountDao
import com.flowcrypt.email.database.dao.source.AccountDaoSource
import com.flowcrypt.email.database.entity.AttachmentEntity
import com.flowcrypt.email.database.entity.LabelEntity
import com.flowcrypt.email.database.entity.MessageEntity
import com.flowcrypt.email.model.EmailAndNamePair
import com.flowcrypt.email.ui.activity.SearchMessagesActivity
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.LogsUtil
import com.flowcrypt.email.util.exception.ExceptionUtil
import com.sun.mail.imap.IMAPFolder
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.FolderClosedException
import javax.mail.MessagingException
import javax.mail.StoreClosedException
import javax.mail.internet.InternetAddress

/**
 * This the email synchronization service. This class is responsible for the logic of
 * synchronization work. Using this service we can asynchronous download information and send
 * data to the IMAP server.
 *
 * @author DenBond7
 * Date: 14.06.2017
 * Time: 12:18
 * E-mail: DenBond7@gmail.com
 */
class EmailSyncService : BaseService(), SyncListener {
  /**
   * This [Messenger] is responsible for the receive intents from other client and
   * handles them.
   */
  private var messenger: Messenger? = null

  private val replyToMessengers: MutableMap<String, Messenger> = HashMap()

  private lateinit var emailSyncManager: EmailSyncManager
  private lateinit var connectionBroadcastReceiver: BroadcastReceiver
  private lateinit var notificationManager: MessagesNotificationManager

  private var isServiceStarted: Boolean = false

  override val context: Context
    get() = this.applicationContext

  override fun onCreate() {
    super.onCreate()
    LogsUtil.d(TAG, "onCreate")

    notificationManager = MessagesNotificationManager(this)

    val account: AccountDao? = AccountDaoSource().getActiveAccountInformation(this)
    account?.let {
      emailSyncManager = EmailSyncManager(it, this)
      messenger = Messenger(IncomingHandler(emailSyncManager, replyToMessengers))
    }

    connectionBroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        handleConnectivityAction(context, intent)
      }
    }

    registerReceiver(connectionBroadcastReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    LogsUtil.d(TAG, "onStartCommand |intent =$intent|flags = $flags|startId = $startId")
    isServiceStarted = true

    if (intent != null && intent.action != null) {
      when (intent.action) {
        else -> if (::emailSyncManager.isInitialized) {
          emailSyncManager.beginSync()
        }
      }
    } else if (::emailSyncManager.isInitialized) {
      emailSyncManager.beginSync()
    }

    return super.onStartCommand(intent, flags, startId)
  }

  override fun onDestroy() {
    super.onDestroy()
    LogsUtil.d(TAG, "onDestroy")

    if (::emailSyncManager.isInitialized) {
      emailSyncManager.stopSync()
    }

    unregisterReceiver(connectionBroadcastReceiver)
  }

  override fun onUnbind(intent: Intent): Boolean {
    LogsUtil.d(TAG, "onUnbind:$intent")
    return super.onUnbind(intent)
  }

  override fun onRebind(intent: Intent) {
    super.onRebind(intent)
    LogsUtil.d(TAG, "onRebind:$intent")
  }

  override fun onBind(intent: Intent): IBinder? {
    LogsUtil.d(TAG, "onBind:$intent")

    if (!isServiceStarted) {
      startEmailSyncService(context)
    }
    return messenger?.binder
  }

  override fun onMsgWithBackupToKeyOwnerSent(account: AccountDao, ownerKey: String, requestCode: Int, isSent: Boolean) {
    try {
      if (isSent) {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK)
      } else {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_ERROR_BACKUP_NOT_SENT)
      }
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    }

  }

  override fun onPrivateKeysFound(account: AccountDao, keys: List<NodeKeyDetails>, ownerKey: String, requestCode: Int) {
    try {
      sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK, keys)
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
    }
  }

  override fun onMsgSent(account: AccountDao, ownerKey: String, requestCode: Int, isSent: Boolean) {
    try {
      if (isSent) {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK)
      } else {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_ERROR_MESSAGE_WAS_NOT_SENT)
      }
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    }

  }

  override fun onMsgsMoved(account: AccountDao, srcFolder: IMAPFolder, destFolder: IMAPFolder,
                           msgs: List<javax.mail.Message>, ownerKey: String, requestCode: Int) {
    //Todo-denbond7 Not implemented yet.
  }

  override fun onMsgMoved(account: AccountDao, srcFolder: IMAPFolder, destFolder: IMAPFolder,
                          msg: javax.mail.Message?, ownerKey: String, requestCode: Int) {
    try {
      if (msg != null) {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK)
      } else {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_ERROR_MESSAGE_NOT_EXISTS)
      }
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    }
  }

  override fun onMsgDetailsReceived(account: AccountDao, localFolder: LocalFolder,
                                    remoteFolder: IMAPFolder, uid: Long, id: Long, msg: javax.mail.Message?,
                                    ownerKey: String, requestCode: Int) {
    try {
      if (MsgsCacheManager.isMsgExist(id.toString())) {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK)
      } else {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_ERROR_MESSAGE_NOT_FOUND)
      }
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    } catch (e: MessagingException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    } catch (e: IOException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    }
  }

  override fun onMsgsReceived(account: AccountDao, localFolder: LocalFolder,
                              remoteFolder: IMAPFolder, msgs: Array<javax.mail.Message>, ownerKey: String,
                              requestCode: Int) {
    LogsUtil.d(TAG, "onMessagesReceived: imapFolder = " + remoteFolder.fullName + " message count: " + msgs.size)
    try {
      val email = account.email
      val folder = localFolder.fullName
      val roomDatabase = FlowCryptRoomDatabase.getDatabase(this@EmailSyncService)

      val isEncryptedModeEnabled = AccountDaoSource().isEncryptedModeEnabled(this, email)
      val msgEntities = MessageEntity.genMessageEntities(
          context = this,
          email = email,
          label = folder,
          folder = remoteFolder,
          msgs = msgs,
          isNew = false,
          areAllMsgsEncrypted = isEncryptedModeEnabled
      )

      roomDatabase.msgDao().insertWithReplace(msgEntities)

      if (!isEncryptedModeEnabled) {
        emailSyncManager.identifyEncryptedMsgs(ownerKey, R.id.syns_identify_encrypted_messages, localFolder)
      }

      if (msgs.isNotEmpty()) {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_NEED_UPDATE, localFolder)
      } else {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK, localFolder)
      }

      try {
        //we should handle any exceptions here to prevent showing messages
        val savedMsgUIDsSet = msgEntities.map { it.uid }.toSet()
        val attachments = mutableListOf<AttachmentEntity>()
        for (msg in msgs) {
          if (remoteFolder.getUID(msg) in savedMsgUIDsSet) {
            val uid = remoteFolder.getUID(msg)
            attachments.addAll(EmailUtil.getAttsInfoFromPart(msg).mapNotNull {
              AttachmentEntity.fromAttInfo(it.apply {
                this.email = account.email
                this.folder = localFolder.fullName
                this.uid = uid.toInt()
              })
            })
          }
        }

        roomDatabase.attachmentDao().insertWithReplace(attachments)
        updateLocalContactsIfNeeded(remoteFolder, msgs)
      } catch (e: Exception) {
        e.printStackTrace()
        ExceptionUtil.handleError(e)
      }
    } catch (e: MessagingException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    }
  }

  override fun onNewMsgsReceived(account: AccountDao, localFolder: LocalFolder,
                                 remoteFolder: IMAPFolder, newMsgs: Array<javax.mail.Message>,
                                 msgsEncryptionStates: Map<Long, Boolean>, ownerKey: String,
                                 requestCode: Int) {
    LogsUtil.d(TAG, "onMessagesReceived:message count: " + newMsgs.size)
    try {
      val email = account.email
      val folderName = localFolder.fullName

      val folderType = FoldersManager.getFolderType(localFolder)
      val isNew = !GeneralUtil.isAppForegrounded() && folderType === FoldersManager.FolderType.INBOX

      val msgEntities = MessageEntity.genMessageEntities(
          context = this,
          email = email,
          label = folderName,
          folder = remoteFolder,
          msgs = newMsgs,
          msgsEncryptionStates = msgsEncryptionStates,
          isNew = isNew,
          areAllMsgsEncrypted = false
      )

      val roomDatabase = FlowCryptRoomDatabase.getDatabase(applicationContext)
      roomDatabase.msgDao().insertWithReplace(msgEntities)

      if (newMsgs.isNotEmpty()) {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_NEED_UPDATE)
      } else {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK)
      }

      if (!GeneralUtil.isAppForegrounded()) {
        val detailsList = roomDatabase.msgDao().getNewMsgs(email, folderName)
        val uidListOfUnseenMsgs = roomDatabase.msgDao().getUIDOfUnseenMsgs(email, folderName)
        notificationManager.notify(this, account, localFolder, detailsList, uidListOfUnseenMsgs, false)
      }
    } catch (e: MessagingException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    }
  }

  override fun onSearchMsgsReceived(account: AccountDao, localFolder: LocalFolder, remoteFolder: IMAPFolder,
                                    msgs: Array<javax.mail.Message>, ownerKey: String, requestCode: Int) {
    LogsUtil.d(TAG, "onSearchMessagesReceived: message count: " + msgs.size)
    val email = account.email
    try {
      val isEncryptedModeEnabled = AccountDaoSource().isEncryptedModeEnabled(this, email)
      val searchLabel = SearchMessagesActivity.SEARCH_FOLDER_NAME

      val msgEntities = MessageEntity.genMessageEntities(
          context = this,
          email = email,
          label = searchLabel,
          folder = remoteFolder,
          msgs = msgs,
          isNew = false,
          areAllMsgsEncrypted = isEncryptedModeEnabled
      )

      FlowCryptRoomDatabase.getDatabase(context).msgDao().insertWithReplace(msgEntities)

      if (!isEncryptedModeEnabled) {
        emailSyncManager.identifyEncryptedMsgs(ownerKey, R.id.syns_identify_encrypted_messages, localFolder)
      }

      if (msgs.isNotEmpty()) {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_NEED_UPDATE)
      } else {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK)
      }

      updateLocalContactsIfNeeded(remoteFolder, msgs)
    } catch (e: MessagingException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    }
  }

  override fun onRefreshMsgsReceived(account: AccountDao, localFolder: LocalFolder,
                                     remoteFolder: IMAPFolder, newMsgs: Array<javax.mail.Message>,
                                     updateMsgs: Array<javax.mail.Message>, ownerKey: String, requestCode: Int) {
    LogsUtil.d(TAG, "onRefreshMessagesReceived: imapFolder = " + remoteFolder.fullName + " newMessages " +
        "count: " + newMsgs.size + ", updateMessages count = " + updateMsgs.size)
    val email = account.email
    val folderName = localFolder.fullName

    try {
      val roomDatabase = FlowCryptRoomDatabase.getDatabase(applicationContext)

      val mapOfUIDAndMsgFlags = roomDatabase.msgDao().getMapOfUIDAndMsgFlags(email, folderName)
      val msgsUIDs = HashSet(mapOfUIDAndMsgFlags.keys)
      val deleteCandidatesUIDs = EmailUtil.genDeleteCandidates(msgsUIDs, remoteFolder, updateMsgs)

      roomDatabase.msgDao().deleteByUIDs(account.email, folderName, deleteCandidatesUIDs)

      val folderType = FoldersManager.getFolderType(localFolder)
      if (!GeneralUtil.isAppForegrounded() && folderType === FoldersManager.FolderType.INBOX) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          for (uid in deleteCandidatesUIDs) {
            notificationManager.cancel(uid.toInt())
          }
        } else {
          val detailsList = roomDatabase.msgDao().getNewMsgs(email, folderName)
          val uidListOfUnseenMsgs = roomDatabase.msgDao().getUIDOfUnseenMsgs(email, folderName)
          notificationManager.notify(this, account, localFolder, detailsList, uidListOfUnseenMsgs, false)
        }
      }

      val newCandidates = EmailUtil.genNewCandidates(msgsUIDs, remoteFolder, newMsgs)

      val isEncryptedModeEnabled = AccountDaoSource().isEncryptedModeEnabled(this, email)
      val isNew = !GeneralUtil.isAppForegrounded() && folderType === FoldersManager.FolderType.INBOX

      val msgEntities = MessageEntity.genMessageEntities(
          context = this,
          email = email,
          label = folderName,
          folder = remoteFolder,
          msgs = newCandidates,
          isNew = isNew,
          areAllMsgsEncrypted = isEncryptedModeEnabled
      )

      roomDatabase.msgDao().insertWithReplace(msgEntities)

      if (!isEncryptedModeEnabled) {
        emailSyncManager.identifyEncryptedMsgs(ownerKey, R.id.syns_identify_encrypted_messages, localFolder)
      }

      val updateCandidates = EmailUtil.genUpdateCandidates(mapOfUIDAndMsgFlags, remoteFolder, updateMsgs)
          .map { remoteFolder.getUID(it) to it.flags }.toMap()
      roomDatabase.msgDao().updateFlags(account.email, folderName, updateCandidates)

      if (newMsgs.isNotEmpty() || updateMsgs.isNotEmpty()) {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_NEED_UPDATE)
      } else {
        sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK)
      }

      updateLocalContactsIfNeeded(remoteFolder, newCandidates)
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      if (e is StoreClosedException || e is FolderClosedException) {
        onError(account, SyncErrorTypes.ACTION_FAILED_SHOW_TOAST, e, ownerKey, requestCode)
      }
    } catch (e: MessagingException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      if (e is StoreClosedException || e is FolderClosedException) {
        onError(account, SyncErrorTypes.ACTION_FAILED_SHOW_TOAST, e, ownerKey, requestCode)
      }
    } catch (e: OperationApplicationException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      if (e is StoreClosedException || e is FolderClosedException) {
        onError(account, SyncErrorTypes.ACTION_FAILED_SHOW_TOAST, e, ownerKey, requestCode)
      }
    }
  }

  override fun onFoldersInfoReceived(account: AccountDao, folders: Array<Folder>, ownerKey: String, requestCode: Int) {
    LogsUtil.d(TAG, "onFoldersInfoReceived:" + folders.contentToString())
    val email = account.email

    val foldersManager = FoldersManager(account.email)
    for (folder in folders) {
      try {
        val imapFolder = folder as IMAPFolder
        foldersManager.addFolder(imapFolder, folder.getName())
      } catch (e: MessagingException) {
        e.printStackTrace()
        ExceptionUtil.handleError(e)
      }
    }

    val localFolder = LocalFolder(account.email, JavaEmailConstants.FOLDER_OUTBOX,
        JavaEmailConstants.FOLDER_OUTBOX, listOf(JavaEmailConstants.FOLDER_FLAG_HAS_NO_CHILDREN), false, 0, "")

    foldersManager.addFolder(localFolder)
    val roomDatabase = FlowCryptRoomDatabase.getDatabase(applicationContext)

    val existedLabels = roomDatabase.labelDao().getLabels(email)
    val freshLabels = mutableListOf<LabelEntity>()
    for (folder in foldersManager.allFolders) {
      freshLabels.add(LabelEntity.genLabel(email, folder))
    }

    if (existedLabels.isEmpty()) {
      roomDatabase.labelDao().insert(freshLabels)
    } else {
      roomDatabase.labelDao().update(existedLabels, freshLabels)
    }

    try {
      sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK)
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
    }
  }

  override fun onError(account: AccountDao, errorType: Int, e: Exception, ownerKey: String, requestCode: Int) {
    Log.e(TAG, "onError: errorType$errorType| e =$e")
    try {
      if (replyToMessengers.containsKey(ownerKey)) {
        val messenger = replyToMessengers[ownerKey]
        messenger!!.send(Message.obtain(null, REPLY_ERROR, requestCode, errorType, e))
        ExceptionUtil.handleError(e)
      }
    } catch (remoteException: RemoteException) {
      remoteException.printStackTrace()
    }
  }

  override fun onActionProgress(account: AccountDao, ownerKey: String, requestCode: Int,
                                resultCode: Int, value: Int) {
    LogsUtil.d(TAG,
        "onActionProgress: account$account| ownerKey =$ownerKey| requestCode =$requestCode")
    try {
      if (replyToMessengers.containsKey(ownerKey)) {
        val messenger = replyToMessengers[ownerKey]
        messenger!!.send(Message.obtain(null, REPLY_ACTION_PROGRESS, requestCode, resultCode, value))
      }
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
    }
  }

  override fun onMsgChanged(account: AccountDao, localFolder: LocalFolder, remoteFolder: IMAPFolder,
                            msg: javax.mail.Message, ownerKey: String, requestCode: Int) {
    val email = account.email
    val folderName = localFolder.fullName
    val folderType = FoldersManager.getFolderType(localFolder)

    if (!GeneralUtil.isAppForegrounded() && folderType === FoldersManager.FolderType.INBOX) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
          if (msg.flags.contains(Flags.Flag.SEEN)) {
            notificationManager.cancel(remoteFolder.getUID(msg).toInt())
          }
        } catch (e: MessagingException) {
          e.printStackTrace()
        }

      } else {
        val roomDatabase = FlowCryptRoomDatabase.getDatabase(applicationContext)
        val newMsgs = roomDatabase.msgDao().getNewMsgs(email, folderName)
        val uidListOfUnseenMsgs = roomDatabase.msgDao().getUIDOfUnseenMsgs(email, folderName)
        notificationManager.notify(this, account, localFolder, newMsgs, uidListOfUnseenMsgs, true)
      }
    }
  }

  override fun onIdentificationToEncryptionCompleted(account: AccountDao, localFolder: LocalFolder,
                                                     remoteFolder: IMAPFolder, ownerKey: String, requestCode: Int) {
    val email = account.email
    val folderName = localFolder.fullName
    val folderType = FoldersManager.getFolderType(localFolder)

    if (folderType === FoldersManager.FolderType.INBOX && !GeneralUtil.isAppForegrounded()) {
      val roomDatabase = FlowCryptRoomDatabase.getDatabase(applicationContext)

      val detailsList = roomDatabase.msgDao().getNewMsgs(email, folderName)
      val uidListOfUnseenMsgs = roomDatabase.msgDao().getUIDOfUnseenMsgs(email, folderName)

      notificationManager.notify(this, account, localFolder, detailsList, uidListOfUnseenMsgs, false)
    }
  }

  override fun onAttsInfoReceived(account: AccountDao, localFolder: LocalFolder, remoteFolder: IMAPFolder, uid: Long,
                                  ownerKey: String, requestCode: Int) {
    try {
      sendReply(ownerKey, requestCode, REPLY_RESULT_CODE_ACTION_OK)
    } catch (e: RemoteException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
      onError(account, SyncErrorTypes.UNKNOWN_ERROR, e, ownerKey, requestCode)
    }
  }

  fun handleConnectivityAction(context: Context, intent: Intent) {
    if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.action!!, ignoreCase = true)) {
      val connectivityManager = context.getSystemService(Context
          .CONNECTIVITY_SERVICE) as ConnectivityManager

      val networkInfo = connectivityManager.activeNetworkInfo
      if (GeneralUtil.isConnected(this)) {
        LogsUtil.d(TAG, "networkInfo = $networkInfo")
        if (::emailSyncManager.isInitialized) {
          emailSyncManager.beginSync()
        }
      }
    }
  }

  /**
   * Send a reply to the called component.
   *
   * @param key         The key which identify the reply to [Messenger]
   * @param requestCode The unique request code for the reply to [android.os.Messenger].
   * @param resultCode  The result code of the some action. Can take the following values:
   *
   *  * [EmailSyncService.REPLY_RESULT_CODE_ACTION_OK]
   *  * [EmailSyncService.REPLY_RESULT_CODE_NEED_UPDATE]
   *
   * and different errors.
   * @param obj         The object which will be send to the request [Messenger].
   * @throws RemoteException
   */
  private fun sendReply(key: String, requestCode: Int, resultCode: Int, obj: Any? = null) {
    if (replyToMessengers.containsKey(key)) {
      val messenger = replyToMessengers[key]
      messenger!!.send(Message.obtain(null, REPLY_OK, requestCode, resultCode, obj))
    }
  }

  /**
   * Update information about contacts in the local database if current messages from the
   * Sent folder.
   *
   * @param imapFolder The folder where messages exist.
   * @param messages   The received messages.
   */
  private fun updateLocalContactsIfNeeded(imapFolder: IMAPFolder, messages: Array<javax.mail.Message>) {
    try {
      val isSentFolder = listOf(*imapFolder.attributes).contains("\\Sent")

      if (isSentFolder) {
        val emailAndNamePairs = ArrayList<EmailAndNamePair>()
        for (message in messages) {
          emailAndNamePairs.addAll(getEmailAndNamePairs(message))
        }

        EmailAndNameUpdaterService.enqueueWork(this, emailAndNamePairs)
      }
    } catch (e: MessagingException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
    }
  }

  /**
   * Generate a list of [EmailAndNamePair] objects from the input message.
   * This information will be retrieved from "to" and "cc" headers.
   *
   * @param msg The input [javax.mail.Message].
   * @return <tt>[List]</tt> of EmailAndNamePair objects, which contains information
   * about
   * emails and names.
   * @throws MessagingException when retrieve information about recipients.
   */
  private fun getEmailAndNamePairs(msg: javax.mail.Message): List<EmailAndNamePair> {
    val pairs = ArrayList<EmailAndNamePair>()

    val addressesTo = msg.getRecipients(javax.mail.Message.RecipientType.TO)
    if (addressesTo != null) {
      for (address in addressesTo) {
        val internetAddress = address as InternetAddress
        pairs.add(EmailAndNamePair(internetAddress.address, internetAddress.personal))
      }
    }

    val addressesCC = msg.getRecipients(javax.mail.Message.RecipientType.CC)
    if (addressesCC != null) {
      for (address in addressesCC) {
        val internetAddress = address as InternetAddress
        pairs.add(EmailAndNamePair(internetAddress.address, internetAddress.personal))
      }
    }

    return pairs
  }

  /**
   * The incoming handler realization. This handler will be used to communicate with current
   * service and other Android components.
   */
  private class IncomingHandler internal constructor(emailSyncManager: EmailSyncManager, replyToMessengersWeakRef:
  MutableMap<String, Messenger>) : Handler() {
    private val gmailSynsManagerWeakRef = WeakReference(emailSyncManager)
    private val replyToMessengersWeakRef = WeakReference(replyToMessengersWeakRef)

    @Suppress("UNCHECKED_CAST")
    override fun handleMessage(msg: Message) {
      if (gmailSynsManagerWeakRef.get() != null) {
        val emailSyncManager = gmailSynsManagerWeakRef.get()
        var action: Action? = null
        var ownerKey: String? = null
        var uniqueId: String? = null
        var requestCode = -1

        if (msg.obj is Action) {
          action = msg.obj as Action
          ownerKey = action.ownerKey
          requestCode = action.requestCode
          uniqueId = action.uniqueId
        }

        when (msg.what) {
          MESSAGE_ADD_REPLY_MESSENGER -> {
            val replyToMessengersForAdd = replyToMessengersWeakRef.get()

            if (replyToMessengersForAdd != null && action != null) {
              replyToMessengersForAdd[ownerKey!!] = msg.replyTo
            }
          }

          MESSAGE_REMOVE_REPLY_MESSENGER -> {
            val replyToMessengersForRemove = replyToMessengersWeakRef.get()

            if (replyToMessengersForRemove != null && action != null) {
              replyToMessengersForRemove.remove(ownerKey)
            }
          }

          MESSAGE_UPDATE_LABELS -> if (emailSyncManager != null && action != null) {
            emailSyncManager.updateLabels(ownerKey!!, requestCode)
          }

          MESSAGE_LOAD_MESSAGES -> if (emailSyncManager != null && action != null) {
            val localFolder = action.`object` as LocalFolder
            emailSyncManager.loadMsgs(ownerKey!!, requestCode, localFolder, msg.arg1, msg.arg2)
          }

          MESSAGE_LOAD_NEXT_MESSAGES -> if (emailSyncManager != null && action != null) {
            val localFolder = action.`object` as LocalFolder
            emailSyncManager.loadNextMsgs(ownerKey!!, requestCode, localFolder, msg.arg1)
          }

          MESSAGE_REFRESH_MESSAGES -> if (emailSyncManager != null && action != null) {
            val refreshLocalFolder = action.`object` as LocalFolder
            emailSyncManager.refreshMsgs(ownerKey!!, requestCode, refreshLocalFolder)
          }

          MESSAGE_LOAD_MESSAGE_DETAILS -> if (emailSyncManager != null && action != null) {
            val localFolder = action.`object` as LocalFolder
            emailSyncManager.loadMsgDetails(ownerKey!!, requestCode, action.uniqueId, localFolder,
                msg.arg1, msg.arg2, action.resetConnection)
          }

          MESSAGE_MOVE_MESSAGE -> if (emailSyncManager != null && action != null) {
            val localFolders = action.`object` as Array<LocalFolder?>

            val emailDomain = emailSyncManager.account.accountType

            if (localFolders.size != 2) {
              throw IllegalArgumentException(emailDomain!! + "|Can't move the message. Folders are null.")
            }

            if (localFolders[0] == null) {
              throw IllegalArgumentException(emailDomain!! + "|Can't move the message. The source folder is null.")
            }

            if (localFolders[1] == null) {
              throw IllegalArgumentException(emailDomain!! + "|Cannot move the message. The dest folder is null.")
            }

            emailSyncManager.moveMsg(ownerKey!!, requestCode, localFolders[0]!!, localFolders[1]!!, msg.arg1)
          }

          MESSAGE_LOAD_PRIVATE_KEYS -> if (emailSyncManager != null && action != null) {
            emailSyncManager.loadPrivateKeys(ownerKey!!, requestCode)
          }

          MESSAGE_SEND_MESSAGE_WITH_BACKUP -> if (emailSyncManager != null && action != null) {
            emailSyncManager.sendMsgWithBackup(ownerKey!!, requestCode)
          }

          MESSAGE_SEARCH_MESSAGES -> if (emailSyncManager != null && action != null) {
            val localFolderWhereWeDoSearch = action.`object` as LocalFolder
            emailSyncManager.searchMsgs(ownerKey!!, requestCode, localFolderWhereWeDoSearch, msg.arg1)
          }

          MESSAGE_CANCEL_ALL_TASKS -> if (emailSyncManager != null && action != null) {
            //emailSyncManager.cancelAllSyncTasks()
          }

          MESSAGE_LOAD_ATTS_INFO -> if (emailSyncManager != null && action != null) {
            val localFolder = action.`object` as LocalFolder
            emailSyncManager.loadAttsInfo(ownerKey!!, requestCode, localFolder, msg.arg1)
          }

          MESSAGE_CANCEL_LOAD_MESSAGE_DETAILS -> {
            uniqueId?.let { emailSyncManager?.cancelLoadMsgDetails(it) }
          }

          MESSAGE_DELETE_MSGS -> emailSyncManager?.deleteMsgs()

          MESSAGE_ARCHIVE_MSGS -> emailSyncManager?.archiveMsgs()

          MESSAGE_CHANGE_MSGS_READ_STATE -> emailSyncManager?.changeMsgsReadState()

          MESSAGE_MOVE_MSGS_TO_INBOX -> emailSyncManager?.moveMsgsToINBOX()

          else -> super.handleMessage(msg)
        }
      }
    }
  }

  companion object {
    const val REPLY_RESULT_CODE_ACTION_OK = 0
    const val REPLY_RESULT_CODE_ACTION_ERROR_MESSAGE_NOT_FOUND = 1
    const val REPLY_RESULT_CODE_ACTION_ERROR_BACKUP_NOT_SENT = 2
    const val REPLY_RESULT_CODE_ACTION_ERROR_MESSAGE_WAS_NOT_SENT = 3
    const val REPLY_RESULT_CODE_ACTION_ERROR_MESSAGE_NOT_EXISTS = 4
    const val REPLY_RESULT_CODE_NEED_UPDATE = 2

    const val MESSAGE_ADD_REPLY_MESSENGER = 1
    const val MESSAGE_REMOVE_REPLY_MESSENGER = 2
    const val MESSAGE_UPDATE_LABELS = 3
    const val MESSAGE_LOAD_MESSAGES = 4
    const val MESSAGE_LOAD_NEXT_MESSAGES = 5
    const val MESSAGE_REFRESH_MESSAGES = 6
    const val MESSAGE_LOAD_MESSAGE_DETAILS = 7
    const val MESSAGE_MOVE_MESSAGE = 8
    const val MESSAGE_LOAD_PRIVATE_KEYS = 9
    const val MESSAGE_SEND_MESSAGE_WITH_BACKUP = 10
    const val MESSAGE_SEARCH_MESSAGES = 11
    const val MESSAGE_CANCEL_ALL_TASKS = 12
    const val MESSAGE_LOAD_ATTS_INFO = 13
    const val MESSAGE_CANCEL_LOAD_MESSAGE_DETAILS = 14
    const val MESSAGE_DELETE_MSGS = 15
    const val MESSAGE_ARCHIVE_MSGS = 16
    const val MESSAGE_CHANGE_MSGS_READ_STATE = 17
    const val MESSAGE_MOVE_MSGS_TO_INBOX = 18

    private val TAG = EmailSyncService::class.java.simpleName

    /**
     * This method can bu used to start [EmailSyncService].
     *
     * @param context Interface to global information about an application environment.
     */
    @JvmStatic
    fun startEmailSyncService(context: Context) {
      val startEmailServiceIntent = Intent(context, EmailSyncService::class.java)
      context.startService(startEmailServiceIntent)
    }

    /**
     * This method can bu used to start [EmailSyncService] with the action [.ACTION_SWITCH_ACCOUNT].
     *
     * @param context Interface to global information about an application environment.
     */
    @JvmStatic
    fun switchAccount(context: Context) {
      NotificationManagerCompat.from(context).cancelAll()
      val intent = Intent(context, EmailSyncService::class.java)
      context.stopService(intent)
      context.startService(intent)
    }
  }
}
