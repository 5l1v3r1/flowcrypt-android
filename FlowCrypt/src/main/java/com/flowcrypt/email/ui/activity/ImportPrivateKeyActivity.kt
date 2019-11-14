/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.idling.CountingIdlingResource
import com.flowcrypt.email.R
import com.flowcrypt.email.api.retrofit.response.base.ApiResponse
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails
import com.flowcrypt.email.database.dao.KeysDao
import com.flowcrypt.email.database.dao.source.ContactsDaoSource
import com.flowcrypt.email.database.dao.source.KeysDaoSource
import com.flowcrypt.email.database.dao.source.UserIdEmailsKeysDaoSource
import com.flowcrypt.email.jetpack.viewmodel.SubmitPubKeyViewModel
import com.flowcrypt.email.model.KeyDetails
import com.flowcrypt.email.model.PgpContact
import com.flowcrypt.email.security.KeyStoreCryptoManager
import com.flowcrypt.email.security.KeysStorageImpl
import com.flowcrypt.email.ui.activity.base.BaseImportKeyActivity
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.UIUtil
import com.google.android.gms.common.util.CollectionUtils
import com.google.android.material.snackbar.Snackbar

/**
 * This activity describes a logic of import private keys.
 *
 * @author Denis Bondarenko
 * Date: 20.07.2017
 * Time: 16:59
 * E-mail: DenBond7@gmail.com
 */

class ImportPrivateKeyActivity : BaseImportKeyActivity() {
  @get:VisibleForTesting
  var countingIdlingResource: CountingIdlingResource? = null
    private set
  private var privateKeysFromEmailBackups: ArrayList<NodeKeyDetails>? = null
  private lateinit var submitPubKeyViewModel: SubmitPubKeyViewModel
  private val unlockedKeys: MutableList<NodeKeyDetails> = ArrayList()
  private val keyStoreCryptoManager = KeyStoreCryptoManager.getInstance(this)
  private var keyDetailsType: KeyDetails.Type = KeyDetails.Type.EMAIL

  private var layoutSyncStatus: View? = null
  private var buttonImportBackup: Button? = null

  private var isLoadPrivateKeysRequestSent: Boolean = false

  override val contentViewResourceId: Int
    get() = R.layout.activity_import_private_key

  override val isPrivateKeyMode: Boolean
    get() = true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (isSyncEnabled && GeneralUtil.isConnected(this)) {
      textViewProgressText.setText(R.string.loading_backups)
      UIUtil.exchangeViewVisibility(this, true, layoutProgress, layoutContentView)
      countingIdlingResource = CountingIdlingResource(
          GeneralUtil.genIdlingResourcesName(ImportPrivateKeyActivity::class.java), GeneralUtil.isDebugBuild())
    } else {
      hideImportButton()
      UIUtil.exchangeViewVisibility(this, false, layoutProgress, layoutContentView)
    }

    setupSubmitPubKeyViewModel()
  }

  override fun initViews() {
    super.initViews()
    this.layoutSyncStatus = findViewById(R.id.layoutSyncStatus)
    this.buttonImportBackup = findViewById(R.id.buttonImportBackup)
    this.buttonImportBackup!!.setOnClickListener(this)
  }

  override fun onSyncServiceConnected() {
    if (!isLoadPrivateKeysRequestSent) {
      isLoadPrivateKeysRequestSent = true
      loadPrivateKeys(R.id.syns_load_private_keys)

      if (countingIdlingResource != null) {
        countingIdlingResource!!.increment()
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun onReplyReceived(requestCode: Int, resultCode: Int, obj: Any?) {
    when (requestCode) {
      R.id.syns_load_private_keys -> {
        if (privateKeysFromEmailBackups == null) {
          val keys = obj as ArrayList<NodeKeyDetails>?
          if (keys != null) {
            if (keys.isNotEmpty()) {
              this.privateKeysFromEmailBackups = keys

              val uniqueKeysLongIds = filterKeys()

              if (this.privateKeysFromEmailBackups!!.isEmpty()) {
                hideImportButton()
              } else {
                buttonImportBackup!!.text = resources.getQuantityString(
                    R.plurals.import_keys, uniqueKeysLongIds.size)
                textViewTitle.text = resources.getQuantityString(
                    R.plurals.you_have_backups_that_was_not_imported, uniqueKeysLongIds.size)
              }
            } else {
              hideImportButton()
            }
          } else {
            hideImportButton()
          }
          UIUtil.exchangeViewVisibility(this, false, layoutProgress, layoutContentView)
        }
        if (!countingIdlingResource!!.isIdleNow) {
          countingIdlingResource!!.decrement()
        }
      }
    }
  }

  override fun onErrorHappened(requestCode: Int, errorType: Int, e: Exception) {
    when (requestCode) {
      R.id.syns_load_private_keys -> {
        hideImportButton()
        UIUtil.exchangeViewVisibility(this, false, layoutProgress, layoutSyncStatus)
        UIUtil.showSnackbar(rootView, getString(R.string.error_occurred_while_receiving_private_keys),
            getString(android.R.string.ok), View.OnClickListener {
          layoutSyncStatus?.visibility = View.GONE
          UIUtil.exchangeViewVisibility(this@ImportPrivateKeyActivity,
              false, layoutProgress, layoutContentView)
        })
        if (!countingIdlingResource!!.isIdleNow) {
          countingIdlingResource!!.decrement()
        }
      }
    }
  }

  override fun onClick(v: View) {
    when (v.id) {
      R.id.buttonImportBackup -> {
        unlockedKeys.clear()
        if (!CollectionUtils.isEmpty(privateKeysFromEmailBackups)) {
          keyDetailsType = KeyDetails.Type.EMAIL
          startActivityForResult(CheckKeysActivity.newIntent(this, privateKeysFromEmailBackups!!, KeyDetails.Type.EMAIL, null,
              getString(R.string.continue_), getString(R.string.choose_another_key)), REQUEST_CODE_CHECK_PRIVATE_KEYS)
        }
      }

      else -> {
        when (v.id) {
          R.id.buttonLoadFromFile, R.id.buttonLoadFromClipboard -> unlockedKeys.clear()
        }
        super.onClick(v)
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_CHECK_PRIVATE_KEYS -> {
        isCheckingClipboardEnabled = false

        when (resultCode) {
          Activity.RESULT_OK -> {
            val keys: List<NodeKeyDetails>? = data?.getParcelableArrayListExtra(
                CheckKeysActivity.KEY_EXTRA_UNLOCKED_PRIVATE_KEYS)

            keys?.let {
              unlockedKeys.clear()
              unlockedKeys.addAll(it)
              account?.let { accountDao -> submitPubKeyViewModel.submitPubKey(accountDao, unlockedKeys) }
            }
          }
        }
      }
      else -> super.onActivityResult(requestCode, resultCode, data)
    }
  }

  override fun onKeyFound(type: KeyDetails.Type, keyDetailsList: ArrayList<NodeKeyDetails>) {
    val keysDaoSource = KeysDaoSource()
    var areFreshKeysExisted = false
    var arePrivateKeysExisted = false

    for (key in keyDetailsList) {
      if (key.isPrivate) {
        arePrivateKeysExisted = true
      }

      val longId = key.longId ?: continue
      if (!keysDaoSource.hasKey(this, longId)) {
        areFreshKeysExisted = true
      }
    }

    if (!arePrivateKeysExisted) {
      showInfoSnackbar(rootView, getString(R.string.file_has_wrong_pgp_structure, getString(R
          .string.private_)), Snackbar.LENGTH_LONG)
      return
    }

    if (!areFreshKeysExisted) {
      showInfoSnackbar(rootView, getString(R.string.the_key_already_added), Snackbar.LENGTH_LONG)
      return
    }

    when (type) {
      KeyDetails.Type.FILE -> {
        keyDetailsType = KeyDetails.Type.FILE
        val fileName = GeneralUtil.getFileNameFromUri(this, keyImportModel!!.fileUri)
        val bottomTitle = resources.getQuantityString(R.plurals.file_contains_some_amount_of_keys,
            keyDetailsList.size, fileName, keyDetailsList.size)
        val posBtnTitle = getString(R.string.continue_)
        val intent = CheckKeysActivity.newIntent(this, keyDetailsList, keyDetailsType,
            bottomTitle, posBtnTitle, null, getString(R.string.choose_another_key), true)
        startActivityForResult(intent, REQUEST_CODE_CHECK_PRIVATE_KEYS)
      }

      KeyDetails.Type.CLIPBOARD -> {
        keyDetailsType = KeyDetails.Type.CLIPBOARD
        val title = resources.getQuantityString(R.plurals.loaded_private_keys_from_clipboard,
            keyDetailsList.size, keyDetailsList.size)
        val clipboardIntent = CheckKeysActivity.newIntent(this, keyDetailsList, keyDetailsType, title,
            getString(R.string.continue_), null, getString(R.string.choose_another_key), true)
        startActivityForResult(clipboardIntent,
            REQUEST_CODE_CHECK_PRIVATE_KEYS)
      }

      else -> {
      }
    }
  }

  private fun hideImportButton() {
    buttonImportBackup!!.visibility = View.GONE
    val marginLayoutParams = buttonLoadFromFile
        .layoutParams as ViewGroup.MarginLayoutParams
    marginLayoutParams.topMargin = resources.getDimensionPixelSize(R.dimen
        .margin_top_first_button)
    buttonLoadFromFile.requestLayout()
  }

  private fun filterKeys(): Set<String> {
    val connector = KeysStorageImpl.getInstance(this)

    val iterator = privateKeysFromEmailBackups!!.iterator()
    val uniqueKeysLongIds = HashSet<String>()

    while (iterator.hasNext()) {
      val privateKey = iterator.next()
      uniqueKeysLongIds.add(privateKey.longId!!)
      if (connector.getPgpPrivateKey(privateKey.longId!!) != null) {
        iterator.remove()
        uniqueKeysLongIds.remove(privateKey.longId!!)
      }
    }
    return uniqueKeysLongIds
  }

  private fun setupSubmitPubKeyViewModel() {
    submitPubKeyViewModel = ViewModelProvider(this).get(SubmitPubKeyViewModel::class.java)
    val observer = Observer<Result<ApiResponse>?> {
      it?.let {
        when (it.status) {
          Result.Status.LOADING -> {
            textViewProgressText.setText(R.string.submitting_pub_key)
            UIUtil.exchangeViewVisibility(this, true, layoutProgress, layoutContentView)
          }

          Result.Status.SUCCESS -> {
            handleSuccessSubmit()
          }

          Result.Status.ERROR -> {
            UIUtil.exchangeViewVisibility(this, false, layoutProgress, layoutContentView)
            showSnackbar(rootView, it.data?.apiError?.msg
                ?: getString(R.string.unknown_error), getString(R.string.retry),
                Snackbar.LENGTH_INDEFINITE, View.OnClickListener {
              account?.let { accountDao -> submitPubKeyViewModel.submitPubKey(accountDao, unlockedKeys) }
            })
          }

          Result.Status.EXCEPTION -> {
            UIUtil.exchangeViewVisibility(this, false, layoutProgress, layoutContentView)
            showSnackbar(rootView, it.exception?.message
                ?: getString(R.string.unknown_error), getString(R.string.retry),
                Snackbar.LENGTH_INDEFINITE, View.OnClickListener {
              account?.let { accountDao -> submitPubKeyViewModel.submitPubKey(accountDao, unlockedKeys) }
            })
          }
        }
      }
    }

    submitPubKeyViewModel.submitPubKeyLiveData.observe(this, observer)
  }

  private fun handleSuccessSubmit() {
    try {
      textViewProgressText.setText(R.string.saving_prv_keys)
      encryptAndSaveKeysToDatabase()
    } catch (e: Exception) {
      UIUtil.exchangeViewVisibility(this, false, layoutProgress, layoutContentView)
      showSnackbar(rootView, e.message ?: getString(R.string.unknown_error),
          getString(R.string.retry), Snackbar.LENGTH_INDEFINITE, View.OnClickListener {
        handleSuccessSubmit()
      })
    }
  }

  private fun encryptAndSaveKeysToDatabase() {
    //maybe it'd be better to move some logic to a new ViewModel
    val keysDaoSource = KeysDaoSource()
    val userIdEmailsKeysDaoSource = UserIdEmailsKeysDaoSource()

    for (keyDetails in unlockedKeys) {
      if (!keysDaoSource.hasKey(this, keyDetails.longId!!)) {
        val passphrase = if (keyDetails.isDecrypted == true) "" else keyDetails.passphrase!!
        val keysDao = KeysDao.generateKeysDao(keyStoreCryptoManager, keyDetailsType,
            keyDetails, passphrase)
        val uri = keysDaoSource.addRow(this, keysDao)

        uri?.let {
          val contactsDaoSource = ContactsDaoSource()
          val pairs: List<Pair<String, String>> = genPairs(keyDetails, keyDetails.pgpContacts, contactsDaoSource)

          for (pair in pairs) {
            userIdEmailsKeysDaoSource.addRow(this, pair.first, pair.second)
          }
        }
      }
    }

    setResult(Activity.RESULT_OK)
    finish()
  }

  private fun genPairs(keyDetails: NodeKeyDetails, contacts: List<PgpContact>,
                       daoSource: ContactsDaoSource): List<Pair<String, String>> {
    val pairs = java.util.ArrayList<Pair<String, String>>()
    for (pgpContact in contacts) {
      pgpContact.pubkey = keyDetails.publicKey
      val temp = daoSource.getPgpContact(this, pgpContact.email)
      if (GeneralUtil.isEmailValid(pgpContact.email) && temp == null) {
        ContactsDaoSource().addRow(this, pgpContact)
        //todo-DenBond7 Need to resolve a situation with different public keys.
        //For example we can have a situation when we have to different public
        // keys with the same email
      }

      pairs.add(Pair.create(keyDetails.longId, pgpContact.email))
    }
    return pairs
  }

  companion object {
    private const val REQUEST_CODE_CHECK_PRIVATE_KEYS = 100
  }
}
