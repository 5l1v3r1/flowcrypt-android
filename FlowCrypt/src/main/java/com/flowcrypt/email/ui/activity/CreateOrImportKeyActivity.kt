/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.flowcrypt.email.R
import com.flowcrypt.email.database.dao.source.AccountDao
import com.flowcrypt.email.model.KeyImportModel
import com.flowcrypt.email.security.KeysStorageImpl
import com.flowcrypt.email.ui.activity.base.BaseCheckClipboardBackStackActivity
import com.flowcrypt.email.ui.activity.base.BaseImportKeyActivity
import com.flowcrypt.email.util.GeneralUtil

/**
 * This activity describes a logic for create ot import private keys.
 *
 * @author DenBond7
 * Date: 23.05.2017.
 * Time: 16:15.
 * E-mail: DenBond7@gmail.com
 */
class CreateOrImportKeyActivity : BaseCheckClipboardBackStackActivity(), View.OnClickListener {
  private var isShowAnotherAccountBtnEnabled = true
  private lateinit var account: AccountDao

  override val rootView: View
    get() = findViewById(R.id.layoutContent)

  override val isDisplayHomeAsUpEnabled: Boolean
    get() = false

  override val contentViewResourceId: Int
    get() = R.layout.activity_create_or_import_key

  override val isPrivateKeyChecking: Boolean
    get() = true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    this.isShowAnotherAccountBtnEnabled =
        intent?.getBooleanExtra(KEY_IS_SHOW_ANOTHER_ACCOUNT_BUTTON_ENABLED, true) ?: true
    val account: AccountDao? = intent.getParcelableExtra(EXTRA_KEY_ACCOUNT_DAO)

    if (account == null) {
      finish()
    } else {
      this.account = account
      initViews()
    }
  }

  override fun onClick(v: View) {
    when (v.id) {
      R.id.buttonCreateNewKey -> startActivityForResult(CreatePrivateKeyActivity.newIntent(this, account),
          REQUEST_CODE_CREATE_KEY_ACTIVITY)

      R.id.buttonImportMyKey -> {
        var keyImportModel: KeyImportModel? = null
        if (isBound) {
          keyImportModel = service.keyImportModel
        }

        startActivityForResult(BaseImportKeyActivity.newIntent(this, account, false,
            getString(R.string.import_private_key), keyImportModel, true,
            ImportPrivateKeyActivity::class.java), REQUEST_CODE_IMPORT_ACTIVITY)
      }

      R.id.buttonSelectAnotherAccount -> {
        val intent = Intent()
        intent.putExtra(EXTRA_KEY_ACCOUNT_DAO, account)
        setResult(RESULT_CODE_USE_ANOTHER_ACCOUNT, intent)
        finish()
      }

      R.id.buttonSkipSetup -> {
        setResult(Activity.RESULT_OK)
        finish()
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_IMPORT_ACTIVITY, REQUEST_CODE_CREATE_KEY_ACTIVITY -> when (resultCode) {
        Activity.RESULT_OK -> {
          setResult(Activity.RESULT_OK)
          finish()
        }
      }

      else -> super.onActivityResult(requestCode, resultCode, data)
    }
  }

  private fun initViews() {
    findViewById<View>(R.id.buttonImportMyKey)?.setOnClickListener(this)

    if (account.isRuleExist(AccountDao.DomainRule.NO_PRV_CREATE)) {
      findViewById<View>(R.id.buttonCreateNewKey)?.visibility = View.GONE
    } else {
      findViewById<View>(R.id.buttonCreateNewKey)?.setOnClickListener(this)
    }

    val buttonSelectAnotherAccount = findViewById<View>(R.id.buttonSelectAnotherAccount)
    if (isShowAnotherAccountBtnEnabled) {
      buttonSelectAnotherAccount?.visibility = View.VISIBLE
      buttonSelectAnotherAccount?.setOnClickListener(this)
    } else {
      buttonSelectAnotherAccount?.visibility = View.GONE
    }

    val buttonSkipSetup = findViewById<View>(R.id.buttonSkipSetup)
    if (account.isRuleExist(AccountDao.DomainRule.NO_PRV_CREATE)) {
      buttonSkipSetup.visibility = View.GONE
    } else {
      if (KeysStorageImpl.getInstance(application).hasKeys()) {
        buttonSkipSetup?.visibility = View.VISIBLE
        buttonSkipSetup?.setOnClickListener(this)
      } else {
        buttonSkipSetup?.visibility = View.GONE
      }
    }
  }

  companion object {
    const val RESULT_CODE_USE_ANOTHER_ACCOUNT = 10
    val EXTRA_KEY_ACCOUNT_DAO =
        GeneralUtil.generateUniqueExtraKey("EXTRA_KEY_ACCOUNT_DAO", CreateOrImportKeyActivity::class.java)

    private const val REQUEST_CODE_IMPORT_ACTIVITY = 11
    private const val REQUEST_CODE_CREATE_KEY_ACTIVITY = 12
    private val KEY_IS_SHOW_ANOTHER_ACCOUNT_BUTTON_ENABLED =
        GeneralUtil.generateUniqueExtraKey("KEY_IS_SHOW_ANOTHER_ACCOUNT_BUTTON_ENABLED",
            CreateOrImportKeyActivity::class.java)

    fun newIntent(context: Context, account: AccountDao, isShowAnotherAccountBtnEnabled: Boolean): Intent {
      val intent = Intent(context, CreateOrImportKeyActivity::class.java)
      intent.putExtra(EXTRA_KEY_ACCOUNT_DAO, account)
      intent.putExtra(KEY_IS_SHOW_ANOTHER_ACCOUNT_BUTTON_ENABLED, isShowAnotherAccountBtnEnabled)
      return intent
    }
  }
}
