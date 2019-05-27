/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.service.attachment;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.widget.Toast;

import com.flowcrypt.email.BuildConfig;
import com.flowcrypt.email.Constants;
import com.flowcrypt.email.R;
import com.flowcrypt.email.api.email.model.AttachmentInfo;
import com.flowcrypt.email.api.email.model.LocalFolder;
import com.flowcrypt.email.api.email.protocol.ImapProtocolUtil;
import com.flowcrypt.email.api.email.protocol.OpenStoreHelper;
import com.flowcrypt.email.api.retrofit.node.NodeRetrofitHelper;
import com.flowcrypt.email.api.retrofit.node.NodeService;
import com.flowcrypt.email.api.retrofit.request.node.DecryptFileRequest;
import com.flowcrypt.email.api.retrofit.response.node.DecryptedFileResult;
import com.flowcrypt.email.database.dao.source.AccountDao;
import com.flowcrypt.email.database.dao.source.AccountDaoSource;
import com.flowcrypt.email.database.dao.source.imap.ImapLabelsDaoSource;
import com.flowcrypt.email.model.PgpKeyInfo;
import com.flowcrypt.email.security.KeysStorageImpl;
import com.flowcrypt.email.util.GeneralUtil;
import com.flowcrypt.email.util.LogsUtil;
import com.flowcrypt.email.util.exception.ExceptionUtil;
import com.flowcrypt.email.util.exception.FlowCryptLimitException;
import com.flowcrypt.email.util.exception.ManualHandledException;
import com.google.android.gms.common.util.CollectionUtils;
import com.sun.mail.imap.IMAPFolder;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.mail.Folder;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import retrofit2.Response;

/**
 * This service will be use to download email attachments. To start load an attachment just run service via the intent
 * {@link AttachmentDownloadManagerService#newIntent(Context, AttachmentInfo)}
 * <p>
 * This service can do:
 * <p>
 * <ul>
 * <li>Can download simultaneously 3 attachments. The other attachments will be added to the queue.</li>
 * <li>All loading attachments will visible in the status bar</li>
 * <li>The user can stop loading an attachment at any time.</li>
 * </ul>
 *
 * @author Denis Bondarenko
 * Date: 16.08.2017
 * Time: 10:29
 * E-mail: DenBond7@gmail.com
 */

public class AttachmentDownloadManagerService extends Service {
  public static final String ACTION_START_DOWNLOAD_ATTACHMENT = BuildConfig.APPLICATION_ID +
      ".ACTION_START_DOWNLOAD_ATTACHMENT";
  public static final String ACTION_CANCEL_DOWNLOAD_ATTACHMENT = BuildConfig.APPLICATION_ID +
      ".ACTION_CANCEL_DOWNLOAD_ATTACHMENT";
  public static final String ACTION_RETRY_DOWNLOAD_ATTACHMENT = BuildConfig.APPLICATION_ID +
      ".ACTION_RETRY_DOWNLOAD_ATTACHMENT";

  public static final String EXTRA_KEY_ATTACHMENT_INFO = GeneralUtil.generateUniqueExtraKey
      ("EXTRA_KEY_ATTACHMENT_INFO", AttachmentDownloadManagerService.class);

  private static final String TAG = AttachmentDownloadManagerService.class.getSimpleName();

  private volatile Looper looper;
  private volatile AttachmentDownloadManagerService.ServiceWorkerHandler workerHandler;

  private Messenger replyMessenger;
  private AttachmentNotificationManager attsNotificationManager;

  public AttachmentDownloadManagerService() {
    this.replyMessenger = new Messenger(new AttachmentDownloadManagerService.ReplyHandler(this));
  }

  public static Intent newIntent(Context context, AttachmentInfo attInfo) {
    Intent intent = new Intent(context, AttachmentDownloadManagerService.class);
    intent.setAction(ACTION_START_DOWNLOAD_ATTACHMENT);
    intent.putExtra(EXTRA_KEY_ATTACHMENT_INFO, attInfo);
    return intent;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    LogsUtil.d(TAG, "onCreate");

    HandlerThread handlerThread = new HandlerThread(TAG);
    handlerThread.start();

    looper = handlerThread.getLooper();
    workerHandler = new AttachmentDownloadManagerService.ServiceWorkerHandler(looper, replyMessenger);
    attsNotificationManager = new AttachmentNotificationManager(getApplicationContext());
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    LogsUtil.d(TAG, "onStartCommand |intent =" + intent + "|flags = " + flags + "|startId = " + startId);
    if (intent != null && !TextUtils.isEmpty(intent.getAction())) {
      AttachmentInfo attInfo = intent.getParcelableExtra(EXTRA_KEY_ATTACHMENT_INFO);
      switch (intent.getAction()) {
        case ACTION_CANCEL_DOWNLOAD_ATTACHMENT:
          cancelDownloadAtt(attInfo);
          break;

        case ACTION_RETRY_DOWNLOAD_ATTACHMENT:
        case ACTION_START_DOWNLOAD_ATTACHMENT:
          if (attInfo != null) {
            addDownloadTaskToQueue(getApplicationContext(), attInfo);
          }
          break;

        default:
          checkAndStopIfNeeded();
          break;
      }
    }

    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    LogsUtil.d(TAG, "onDestroy");
    releaseResources();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void stopService() {
    stopSelf();
    LogsUtil.d(TAG, "Trying to stop the service");
  }

  private void releaseResources() {
    Message msg = workerHandler.obtainMessage();
    msg.what = AttachmentDownloadManagerService.ServiceWorkerHandler.MESSAGE_RELEASE_RESOURCES;
    workerHandler.sendMessage(msg);
  }

  private void cancelDownloadAtt(AttachmentInfo attInfo) {
    attsNotificationManager.loadingCanceledByUser(attInfo);
    Message msg = workerHandler.obtainMessage();
    msg.what = AttachmentDownloadManagerService.ServiceWorkerHandler.MESSAGE_CANCEL_DOWNLOAD;
    msg.obj = attInfo;
    workerHandler.sendMessage(msg);
  }

  private void addDownloadTaskToQueue(Context context, AttachmentInfo attInfo) {
    Message msg = workerHandler.obtainMessage();
    msg.what = AttachmentDownloadManagerService.ServiceWorkerHandler.MESSAGE_START_DOWNLOAD;
    msg.obj = new DownloadAttachmentTaskRequest(context, attInfo);
    workerHandler.sendMessage(msg);
  }

  private void checkAndStopIfNeeded() {
    Message msg = workerHandler.obtainMessage();
    msg.what = AttachmentDownloadManagerService.ServiceWorkerHandler.MESSAGE_CHECK_AND_STOP_IF_NEEDED;
    workerHandler.sendMessage(msg);
  }

  private interface OnDownloadAttachmentListener {
    void onAttDownloaded(AttachmentInfo attInfo, Uri uri);

    void onCanceled(AttachmentInfo attInfo);

    void onError(AttachmentInfo attInfo, Exception e);

    void onProgress(AttachmentInfo attInfo, int progressInPercentage, long timeLeft);
  }

  /**
   * The incoming handler realization. This handler will be used to communicate with current
   * service and the worker thread.
   */
  private static class ReplyHandler extends Handler {
    static final int MESSAGE_EXCEPTION_HAPPENED = 1;
    static final int MESSAGE_TASK_ALREADY_EXISTS = 2;
    static final int MESSAGE_ATTACHMENT_DOWNLOAD = 3;
    static final int MESSAGE_DOWNLOAD_STARTED = 4;
    static final int MESSAGE_ATTACHMENT_ADDED_TO_QUEUE = 5;
    static final int MESSAGE_PROGRESS = 6;
    static final int MESSAGE_RELEASE_RESOURCES = 8;
    static final int MESSAGE_DOWNLOAD_CANCELED = 9;
    static final int MESSAGE_STOP_SERVICE = 10;

    private final WeakReference<AttachmentDownloadManagerService> weakRef;

    ReplyHandler(AttachmentDownloadManagerService attDownloadManagerService) {
      this.weakRef = new WeakReference<>(attDownloadManagerService);
    }

    @Override
    public void handleMessage(Message message) {
      if (weakRef.get() != null) {
        AttachmentDownloadManagerService attDownloadManagerService = weakRef.get();
        AttachmentNotificationManager notificationManager = attDownloadManagerService.attsNotificationManager;

        DownloadAttachmentTaskResult taskResult = (DownloadAttachmentTaskResult) message.obj;
        AttachmentInfo attInfo = taskResult.getAttInfo();
        Uri uri = taskResult.getUri();

        switch (message.what) {
          case MESSAGE_EXCEPTION_HAPPENED:
            notificationManager.errorHappened(attDownloadManagerService, attInfo, taskResult.getException());
            break;

          case MESSAGE_TASK_ALREADY_EXISTS:
            String msg = attDownloadManagerService.getString(R.string.template_attachment_already_loading,
                attInfo.getName());
            Toast.makeText(attDownloadManagerService, msg, Toast.LENGTH_SHORT).show();
            break;

          case MESSAGE_ATTACHMENT_DOWNLOAD:
            notificationManager.downloadCompleted(attDownloadManagerService, attInfo, uri);
            LogsUtil.d(TAG, attInfo.getName() + " is downloaded");
            break;

          case MESSAGE_ATTACHMENT_ADDED_TO_QUEUE:
            notificationManager.attachmentAddedToLoadQueue(attDownloadManagerService, attInfo);
            break;

          case MESSAGE_PROGRESS:
            notificationManager.updateLoadingProgress(attDownloadManagerService, attInfo,
                taskResult.getProgressInPercentage(), taskResult.getTimeLeft());
            break;

          case MESSAGE_RELEASE_RESOURCES:
            attDownloadManagerService.looper.quit();
            break;

          case MESSAGE_DOWNLOAD_CANCELED:
            notificationManager.loadingCanceledByUser(attInfo);
            LogsUtil.d(TAG, attInfo.getName() + " was canceled");
            break;

          case MESSAGE_STOP_SERVICE:
            attDownloadManagerService.stopService();
            break;
        }

        if (taskResult.isLast()) {
          attDownloadManagerService.stopService();
        }
      }
    }
  }

  /**
   * This handler will be used by the instance of {@link HandlerThread} to receive message from
   * the UI thread.
   */
  private static class ServiceWorkerHandler extends Handler implements OnDownloadAttachmentListener {
    static final int MESSAGE_START_DOWNLOAD = 1;
    static final int MESSAGE_CANCEL_DOWNLOAD = 2;
    static final int MESSAGE_RELEASE_RESOURCES = 3;
    static final int MESSAGE_CHECK_AND_STOP_IF_NEEDED = 4;
    /**
     * Maximum number of simultaneous downloads
     */
    private static final int QUEUE_SIZE = 3;
    private ExecutorService executorService;
    private Messenger messenger;

    private volatile HashMap<String, AttachmentInfo> attsInfoMap;
    private volatile HashMap<String, Future<?>> futureMap;

    ServiceWorkerHandler(Looper looper, Messenger messenger) {
      super(looper);
      this.messenger = messenger;
      this.executorService = Executors.newFixedThreadPool(QUEUE_SIZE);
      this.attsInfoMap = new HashMap<>();
      this.futureMap = new HashMap<>();
    }

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MESSAGE_START_DOWNLOAD:
          DownloadAttachmentTaskRequest taskRequest = (DownloadAttachmentTaskRequest) msg.obj;

          AttachmentInfo attInfo = taskRequest.getAttInfo();
          Context context = taskRequest.getContext();

          try {
            if (attsInfoMap.get(attInfo.getId()) == null) {
              attsInfoMap.put(attInfo.getId(), attInfo);
              AttDownloadRunnable attDownloadRunnable = new AttDownloadRunnable(context, attInfo);
              attDownloadRunnable.setListener(this);
              futureMap.put(attInfo.getUniqueStringId(), executorService.submit(attDownloadRunnable));
              DownloadAttachmentTaskResult result = new DownloadAttachmentTaskResult.Builder()
                  .setAttInfo(attInfo)
                  .setException(null)
                  .setUri(null)
                  .build();
              messenger.send(Message.obtain(null, ReplyHandler.MESSAGE_ATTACHMENT_ADDED_TO_QUEUE, result));
            } else {
              taskAlreadyExists(attInfo);
            }
          } catch (Exception e) {
            e.printStackTrace();
            ExceptionUtil.handleError(e);
            try {
              DownloadAttachmentTaskResult result = new DownloadAttachmentTaskResult.Builder()
                  .setAttInfo(attInfo)
                  .setException(e)
                  .build();
              messenger.send(Message.obtain(null, ReplyHandler.MESSAGE_EXCEPTION_HAPPENED, result));
            } catch (RemoteException remoteException) {
              remoteException.printStackTrace();
            }
          }
          break;

        case MESSAGE_CANCEL_DOWNLOAD:
          AttachmentInfo canceledAttInfo = (AttachmentInfo) msg.obj;
          Future future = futureMap.get(canceledAttInfo.getUniqueStringId());
          if (future != null) {
            if (!future.isDone()) {
              //if this thread hasn't run yet
              onCanceled(canceledAttInfo);
            }

            future.cancel(true);
          }

          attsInfoMap.remove(canceledAttInfo.getId());
          break;

        case MESSAGE_RELEASE_RESOURCES:
          if (executorService != null) {
            executorService.shutdown();
          }

          try {
            DownloadAttachmentTaskResult result = new DownloadAttachmentTaskResult.Builder().build();
            messenger.send(Message.obtain(null, ReplyHandler.MESSAGE_RELEASE_RESOURCES, result));
          } catch (RemoteException e) {
            e.printStackTrace();
            ExceptionUtil.handleError(e);
          }
          break;

        case MESSAGE_CHECK_AND_STOP_IF_NEEDED:
          if (CollectionUtils.isEmpty(futureMap.values())) {
            try {
              DownloadAttachmentTaskResult result = new DownloadAttachmentTaskResult.Builder().build();
              messenger.send(Message.obtain(null, ReplyHandler.MESSAGE_STOP_SERVICE, result));
            } catch (RemoteException e) {
              e.printStackTrace();
              ExceptionUtil.handleError(e);
            }
          }
          break;
      }
    }

    @Override
    public void onError(AttachmentInfo attInfo, Exception e) {
      attsInfoMap.remove(attInfo.getId());
      futureMap.remove(attInfo.getUniqueStringId());
      try {
        DownloadAttachmentTaskResult result = new DownloadAttachmentTaskResult.Builder()
            .setAttInfo(attInfo)
            .setException(e)
            .setLast(isLast())
            .build();
        messenger.send(Message.obtain(null, ReplyHandler.MESSAGE_EXCEPTION_HAPPENED, result));
      } catch (RemoteException remoteException) {
        remoteException.printStackTrace();
      }
    }

    @Override
    public void onProgress(AttachmentInfo attInfo, int progressInPercentage, long timeLeft) {
      try {
        DownloadAttachmentTaskResult result = new DownloadAttachmentTaskResult.Builder()
            .setAttInfo(attInfo)
            .setProgress(progressInPercentage)
            .setTimeLeft(timeLeft)
            .build();
        messenger.send(Message.obtain(null, ReplyHandler.MESSAGE_PROGRESS, result));
      } catch (RemoteException remoteException) {
        remoteException.printStackTrace();
      }
    }

    @Override
    public void onAttDownloaded(AttachmentInfo attInfo, Uri uri) {
      attsInfoMap.remove(attInfo.getId());
      futureMap.remove(attInfo.getUniqueStringId());
      try {
        DownloadAttachmentTaskResult result = new DownloadAttachmentTaskResult.Builder()
            .setAttInfo(attInfo)
            .setUri(uri)
            .setLast(isLast())
            .build();
        messenger.send(Message.obtain(null, ReplyHandler.MESSAGE_ATTACHMENT_DOWNLOAD, result));
      } catch (RemoteException remoteException) {
        remoteException.printStackTrace();
      }
    }

    @Override
    public void onCanceled(AttachmentInfo attInfo) {
      attsInfoMap.remove(attInfo.getId());
      futureMap.remove(attInfo.getUniqueStringId());
      try {
        DownloadAttachmentTaskResult result = new DownloadAttachmentTaskResult.Builder()
            .setAttInfo(attInfo)
            .setLast(isLast())
            .build();
        messenger.send(Message.obtain(null, ReplyHandler.MESSAGE_DOWNLOAD_CANCELED, result));
      } catch (RemoteException remoteException) {
        remoteException.printStackTrace();
      }
    }

    private void taskAlreadyExists(AttachmentInfo attInfo) {
      try {
        DownloadAttachmentTaskResult result = new DownloadAttachmentTaskResult.Builder()
            .setAttInfo(attInfo)
            .setLast(isLast())
            .build();
        messenger.send(Message.obtain(null, ReplyHandler.MESSAGE_TASK_ALREADY_EXISTS, result));
      } catch (RemoteException e) {
        e.printStackTrace();
      }
    }

    private boolean isLast() {
      return CollectionUtils.isEmpty(futureMap.values());
    }
  }

  private static class AttDownloadRunnable implements Runnable {
    private static final int MIN_UPDATE_PROGRESS_INTERVAL = 500;
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 16;
    private AttachmentInfo att;
    private Context context;
    private OnDownloadAttachmentListener listener;

    AttDownloadRunnable(Context context, AttachmentInfo att) {
      this.context = context;
      this.att = att;
    }

    @Override
    public void run() {
      if (GeneralUtil.isDebugBuild()) {
        Thread.currentThread().setName(AttDownloadRunnable.class.getSimpleName() + "|" + att.getName());
      }

      File attFile = prepareAttFile();

      try {
        checkFileSize();

        if (att.getUri() != null) {
          InputStream inputStream = context.getContentResolver().openInputStream(att.getUri());
          if (inputStream != null) {
            FileUtils.copyInputStreamToFile(inputStream, attFile);
            attFile = decryptFileIfNeeded(context, attFile);

            if (attFile == null) {
              throw new NullPointerException("Error. The file is missing");
            }

            att.setName(attFile.getName());

            if (!Thread.currentThread().isInterrupted()) {
              if (listener != null) {
                Uri uri = FileProvider.getUriForFile(context, Constants.FILE_PROVIDER_AUTHORITY, attFile);
                listener.onAttDownloaded(att, uri);
              }
            }

            return;
          }
        }

        AccountDaoSource source = new AccountDaoSource();
        AccountDao account = source.getAccountInformation(context, att.getEmail());

        if (account == null) {
          if (listener != null) {
            listener.onCanceled(this.att);
            return;
          }
        }

        Session session = OpenStoreHelper.getAttsSess(context, account);
        Store store = OpenStoreHelper.openStore(context, account, session);

        LocalFolder localFolder = new ImapLabelsDaoSource().getFolder(context, att.getEmail(), att.getFolder());
        if (localFolder == null) {
          if (source.getAccountInformation(context, att.getEmail()) == null) {
            if (listener != null) {
              listener.onCanceled(this.att);

              if (store != null) {
                store.close();
              }
            }
            return;
          } else {
            throw new ManualHandledException("Folder \"" + att.getFolder() + "\" not found in the local cache");
          }
        }

        IMAPFolder remoteFolder = (IMAPFolder) store.getFolder(localFolder.getFullName());
        remoteFolder.open(Folder.READ_ONLY);

        javax.mail.Message msg = remoteFolder.getMessageByUID(att.getUid());

        if (msg == null) {
          throw new ManualHandledException(context.getString(R.string.no_message_with_this_attachment));
        }

        Part att = ImapProtocolUtil.getAttPartById(remoteFolder, msg.getMessageNumber(), msg, this.att.getId());

        if (att != null) {
          InputStream inputStream = att.getInputStream();
          downloadFile(attFile, inputStream);

          attFile = decryptFileIfNeeded(context, attFile);

          if (attFile == null) {
            throw new NullPointerException("Error. The file is missing");
          }

          this.att.setName(attFile.getName());

          if (listener != null) {
            if (Thread.currentThread().isInterrupted()) {
              removeNotCompletedAtt(attFile);
              listener.onCanceled(this.att);
            } else {
              Uri uri = FileProvider.getUriForFile(context, Constants.FILE_PROVIDER_AUTHORITY, attFile);
              listener.onAttDownloaded(this.att, uri);
            }
          }
        } else throw new ManualHandledException(context.getString(R.string.attachment_not_found));

        remoteFolder.close(false);
        store.close();
      } catch (Exception e) {
        e.printStackTrace();
        ExceptionUtil.handleError(e);
        removeNotCompletedAtt(attFile);
        if (listener != null) {
          listener.onError(att, e);
        }
      }
    }

    void setListener(OnDownloadAttachmentListener listener) {
      this.listener = listener;
    }

    private void downloadFile(File attFile, InputStream inputStream) throws IOException {
      try (OutputStream outputStream = FileUtils.openOutputStream(attFile)) {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        double count = 0;
        double size = this.att.getEncodedSize();
        int numberOfReadBytes;
        int lastPercentage = 0;
        int currentPercentage = 0;
        long startTime;
        long elapsedTime;
        long lastUpdateTime = startTime = System.currentTimeMillis();
        updateProgress(currentPercentage, 0);
        while (IOUtils.EOF != (numberOfReadBytes = inputStream.read(buffer))) {
          if (!Thread.currentThread().isInterrupted()) {
            outputStream.write(buffer, 0, numberOfReadBytes);
            count += numberOfReadBytes;
            currentPercentage = (int) ((count / size) * 100f);
            boolean isUpdateNeeded = System.currentTimeMillis() - lastUpdateTime >= MIN_UPDATE_PROGRESS_INTERVAL;
            if (currentPercentage - lastPercentage >= 1 && isUpdateNeeded) {
              lastPercentage = currentPercentage;
              lastUpdateTime = System.currentTimeMillis();
              elapsedTime = lastUpdateTime - startTime;
              long predictLoadingTime = (long) (elapsedTime * size / count);
              updateProgress(currentPercentage, predictLoadingTime - elapsedTime);
            }
          } else {
            break;
          }
        }

        updateProgress(100, 0);
      } finally {
        if (Thread.currentThread().isInterrupted()) {
          removeNotCompletedAtt(attFile);
          if (listener != null) {
            listener.onCanceled(this.att);
          }
        }
      }
    }

    /**
     * Check is decrypted file has size not more than
     * {@link Constants#MAX_ATTACHMENT_SIZE_WHICH_CAN_BE_DECRYPTED}. If the file greater then
     * {@link Constants#MAX_ATTACHMENT_SIZE_WHICH_CAN_BE_DECRYPTED} we throw an exception. This is only for files
     * with the "pgp" extension.
     */
    private void checkFileSize() throws FlowCryptLimitException {
      if ("pgp".equalsIgnoreCase(FilenameUtils.getExtension(att.getName()))) {
        if (att.getEncodedSize() > Constants.MAX_ATTACHMENT_SIZE_WHICH_CAN_BE_DECRYPTED) {
          String errorMsg = context.getString(R.string.template_warning_max_attachments_size_for_decryption,
              FileUtils.byteCountToDisplaySize(Constants.MAX_ATTACHMENT_SIZE_WHICH_CAN_BE_DECRYPTED));
          throw new FlowCryptLimitException(errorMsg);
        }
      }
    }

    /**
     * Do decryption of the downloaded file if it need.
     *
     * @param context Interface to global information about an application environment;
     * @param file    The downloaded file which can be encrypted.
     * @return The decrypted or the original file.
     */
    private File decryptFileIfNeeded(Context context, File file) throws Exception {
      if (file == null || !file.exists()) {
        return null;
      }

      if (!"pgp".equalsIgnoreCase(FilenameUtils.getExtension(file.getName()))) {
        return file;
      }

      try (InputStream inputStream = new FileInputStream(file)) {
        DecryptedFileResult result = getDecryptedFileResult(context, inputStream);
        byte[] decryptedBytes = result.getDecryptedBytes();

        File decryptedFile = new File(file.getParent(), file.getName().substring(0, file.getName().lastIndexOf(".")));

        boolean isInnerExceptionHappened = false;

        try (OutputStream outputStream = FileUtils.openOutputStream(decryptedFile)) {
          IOUtils.write(decryptedBytes, outputStream);
          return decryptedFile;
        } catch (IOException e) {
          if (!decryptedFile.delete()) {
            LogsUtil.d(TAG, "Cannot delete file: " + file);
          }

          isInnerExceptionHappened = true;
          throw e;
        } finally {
          if (!isInnerExceptionHappened) {
            if (!file.delete()) {
              LogsUtil.d(TAG, "Cannot delete file: " + file);
            }
          }
        }
      }
    }

    private DecryptedFileResult getDecryptedFileResult(Context context, InputStream inputStream) throws Exception {
      KeysStorageImpl keysStorage = KeysStorageImpl.getInstance(context);
      List<PgpKeyInfo> pgpKeyInfoList = keysStorage.getAllPgpPrivateKeys();
      List<String> passphrases = new ArrayList<>();

      for (PgpKeyInfo pgpKeyInfo : pgpKeyInfoList) {
        passphrases.add(keysStorage.getPassphrase(pgpKeyInfo.getLongid()));
      }

      NodeService nodeService = NodeRetrofitHelper.getRetrofit().create(NodeService.class);
      DecryptFileRequest request = new DecryptFileRequest(IOUtils.toByteArray(inputStream), pgpKeyInfoList,
          passphrases);

      Response<DecryptedFileResult> response = nodeService.decryptFile(request).execute();
      DecryptedFileResult result = response.body();

      if (result == null) {
        throw new NullPointerException("Node.js returned an empty result");
      }

      if (result.getError() != null) {
        throw new Exception(result.getError().getMsg());
      }

      return result;
    }

    /**
     * Remove the file which not downloaded fully.
     *
     * @param attachmentFile The file which will be removed.
     */
    private void removeNotCompletedAtt(File attachmentFile) {
      if (attachmentFile != null && attachmentFile.exists()) {
        if (!attachmentFile.delete()) {
          LogsUtil.d(TAG, "Cannot delete a file: " + attachmentFile);
        } else {
          LogsUtil.d(TAG, "Canceled attachment \"" + attachmentFile + "\" was deleted");
        }
      }
    }

    private void updateProgress(int currentPercentage, long timeLeft) {
      if (listener != null && !Thread.currentThread().isInterrupted()) {
        listener.onProgress(att, currentPercentage, timeLeft);
      }
    }

    /**
     * Create the local file where we will write an input stream from the IMAP server.
     *
     * @return A new created file.
     */
    private File prepareAttFile() {
      return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), att.getName());
    }
  }
}
