/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jetpack.viewmodel

import android.accounts.Account
import android.app.Application
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.flowcrypt.email.api.email.gmail.GmailApiHelper
import com.flowcrypt.email.api.retrofit.ApiRepository
import com.flowcrypt.email.api.retrofit.FlowcryptApiRepository
import com.flowcrypt.email.api.retrofit.request.model.PostLookUpEmailsModel
import com.flowcrypt.email.api.retrofit.response.attester.LookUpEmailsResponse
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.database.dao.source.AccountDao
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.util.exception.ExceptionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*


/**
 * This [ViewModel] does job of receiving information about an array of public
 * keys from "https://flowcrypt.com/attester/lookup/email".
 *
 * @author Denis Bondarenko
 * Date: 13.11.2017
 * Time: 15:13
 * E-mail: DenBond7@gmail.com
 */

class AccountKeysInfoViewModel(application: Application) : AccountViewModel(application) {
  private val repository: ApiRepository = FlowcryptApiRepository()
  val accountKeysInfoLiveData = MediatorLiveData<Result<LookUpEmailsResponse>>()
  private val initLiveData = Transformations
      .switchMap(activeAccountLiveData) { accountEntity ->
        liveData {
          emit(Result.loading())
          val result: Result<LookUpEmailsResponse> = getResult(accountEntity)
          emit(result)
        }
      }
  private val refreshingLiveData = MutableLiveData<Result<LookUpEmailsResponse>>()

  init {
    accountKeysInfoLiveData.addSource(initLiveData) { accountKeysInfoLiveData.value = it }
    accountKeysInfoLiveData.addSource(refreshingLiveData) { accountKeysInfoLiveData.value = it }
  }

  /**
   * Get available Gmail aliases for an input [AccountDao].
   *
   * @param account The [AccountDao] object which contains information about an email account.
   * @return The list of available Gmail aliases.
   */
  private suspend fun getAvailableGmailAliases(account: Account): Collection<String> = withContext(Dispatchers.IO) {
    val emails = ArrayList<String>()

    try {
      val gmail = GmailApiHelper.generateGmailApiService(getApplication(), account)
      val aliases = gmail.users().settings().sendAs().list(GmailApiHelper.DEFAULT_USER_ID).execute()
      for (alias in aliases.sendAs) {
        if (alias.verificationStatus != null) {
          emails.add(alias.sendAsEmail)
        }
      }
    } catch (e: IOException) {
      e.printStackTrace()
      ExceptionUtil.handleError(e)
    }

    return@withContext emails
  }

  fun refreshData() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        refreshingLiveData.postValue(Result.loading())
        val accountEntity = activeAccountLiveData.value
            ?: roomDatabase.accountDao().getActiveAccount()
        refreshingLiveData.postValue(getResult(accountEntity))
      }
    }
  }

  private suspend fun getResult(accountEntity: AccountEntity?): Result<LookUpEmailsResponse> {
    return if (accountEntity != null) {
      val emails = ArrayList<String>()
      emails.add(accountEntity.email)

      if (accountEntity.account?.type == AccountDao.ACCOUNT_TYPE_GOOGLE) {
        emails.addAll(getAvailableGmailAliases(accountEntity.account))
      }

      repository.postLookUpEmails(getApplication(), PostLookUpEmailsModel(emails))
    } else {
      Result.exception(NullPointerException("AccountDao is null!"))
    }
  }
}
