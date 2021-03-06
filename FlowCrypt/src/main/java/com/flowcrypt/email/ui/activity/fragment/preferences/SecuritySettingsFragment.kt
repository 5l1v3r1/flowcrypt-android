/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment.preferences

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.preference.Preference
import com.flowcrypt.email.Constants
import com.flowcrypt.email.R
import com.flowcrypt.email.database.dao.source.AccountDao
import com.flowcrypt.email.database.dao.source.AccountDaoSource
import com.flowcrypt.email.jetpack.viewmodel.PrivateKeysViewModel
import com.flowcrypt.email.ui.activity.ChangePassPhraseActivity
import com.flowcrypt.email.ui.activity.fragment.base.BasePreferenceFragment
import com.flowcrypt.email.util.UIUtil

/**
 * This fragment contains actions which related to Security options.
 *
 * @author DenBond7
 * Date: 08.08.2018.
 * Time: 10:47.
 * E-mail: DenBond7@gmail.com
 */
class SecuritySettingsFragment : BasePreferenceFragment(), Preference.OnPreferenceClickListener {
  private var account: AccountDao? = null
  private val privateKeysViewModel: PrivateKeysViewModel by viewModels()
  private var longIdsOfCurrentAccount: MutableList<String> = mutableListOf()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    privateKeysViewModel.longIdsOfCurrentAccountLiveData.observe(viewLifecycleOwner, Observer {
      longIdsOfCurrentAccount.clear()
      longIdsOfCurrentAccount.addAll(it)
    })
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.preferences_security_settings, rootKey)

    account = AccountDaoSource().getActiveAccountInformation(requireContext())

    findPreference<Preference>(Constants.PREF_KEY_SECURITY_CHANGE_PASS_PHRASE)?.onPreferenceClickListener = this
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    return when (preference.key) {
      Constants.PREF_KEY_SECURITY_CHANGE_PASS_PHRASE -> {

        if (longIdsOfCurrentAccount.isEmpty()) {
          UIUtil.showInfoSnackbar(requireView(), getString(R.string.account_has_no_associated_keys,
              getString(R.string.support_email)))
        } else {
          startActivity(ChangePassPhraseActivity.newIntent(context, account))
        }
        true
      }

      else -> false
    }
  }
}
