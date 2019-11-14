/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.flowcrypt.email.R
import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails
import com.flowcrypt.email.database.dao.source.AccountDao
import com.flowcrypt.email.database.dao.source.ContactsDaoSource
import com.flowcrypt.email.model.KeyDetails
import com.flowcrypt.email.model.PgpContact
import com.flowcrypt.email.ui.activity.base.BaseImportKeyActivity
import com.flowcrypt.email.util.GeneralUtil
import java.util.*

/**
 * This activity describes a logic of import public keys.
 *
 * @author Denis Bondarenko
 * Date: 03.08.2017
 * Time: 12:35
 * E-mail: DenBond7@gmail.com
 */

class ImportPublicKeyActivity : BaseImportKeyActivity() {

  private var pgpContact: PgpContact? = null

  override val contentViewResourceId: Int
    get() = R.layout.activity_import_public_key_for_pgp_contact

  override val isPrivateKeyMode: Boolean
    get() = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent != null && intent.hasExtra(KEY_EXTRA_PGP_CONTACT)) {
      this.pgpContact = intent.getParcelableExtra(KEY_EXTRA_PGP_CONTACT)
    } else {
      finish()
    }
  }

  override fun onKeyFound(type: KeyDetails.Type, keyDetailsList: ArrayList<NodeKeyDetails>) {
    if (keyDetailsList.isNotEmpty()) {
      if (keyDetailsList.size == 1) {
        updateInformationAboutPgpContact(keyDetailsList[0])
        setResult(Activity.RESULT_OK)
        finish()
      } else {
        showInfoSnackbar(rootView, getString(R.string.select_only_one_key))
      }
    } else {
      showInfoSnackbar(rootView, getString(R.string.unknown_error))
    }
  }

  private fun updateInformationAboutPgpContact(keyDetails: NodeKeyDetails) {
    val contactsDaoSource = ContactsDaoSource()

    val pgpContactFromKey = keyDetails.primaryPgpContact

    pgpContact!!.pubkey = pgpContactFromKey.pubkey
    contactsDaoSource.updatePgpContact(this, pgpContact)

    if (!pgpContact!!.email.equals(pgpContactFromKey.email, ignoreCase = true)) {
      contactsDaoSource.addRow(this, pgpContactFromKey)
    }
  }

  companion object {
    val KEY_EXTRA_PGP_CONTACT = GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_PGP_CONTACT",
        ImportPublicKeyActivity::class.java)

    fun newIntent(context: Context?, accountDao: AccountDao, title: String, pgpContact: PgpContact): Intent {
      val intent = newIntent(context = context, accountDao = accountDao, title = title,
          throwErrorIfDuplicateFoundEnabled = false, cls = ImportPublicKeyActivity::class.java)
      intent.putExtra(KEY_EXTRA_PGP_CONTACT, pgpContact)
      return intent
    }
  }
}
