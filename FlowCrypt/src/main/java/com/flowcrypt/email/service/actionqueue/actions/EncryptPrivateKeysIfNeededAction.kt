/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.service.actionqueue.actions

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.preference.PreferenceManager
import com.flowcrypt.email.Constants
import com.flowcrypt.email.api.retrofit.node.NodeCallsExecutor
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.database.dao.source.AccountDaoSource
import com.flowcrypt.email.database.entity.KeyEntity
import com.flowcrypt.email.security.KeyStoreCryptoManager
import com.flowcrypt.email.security.KeysStorageImpl
import com.flowcrypt.email.ui.notifications.SystemNotificationManager
import com.flowcrypt.email.util.SharedPreferencesHelper
import com.flowcrypt.email.util.exception.ExceptionUtil
import com.flowcrypt.email.util.exception.NodeException
import com.google.android.gms.common.util.CollectionUtils
import com.google.gson.annotations.SerializedName

/**
 * This [Action] checks all available private keys are they encrypted. If not we will try to encrypt a key and
 * save to the local database.
 *
 * @author Denis Bondarenko
 * Date: 2/25/19
 * Time: 4:03 PM
 * E-mail: DenBond7@gmail.com
 */
data class EncryptPrivateKeysIfNeededAction @JvmOverloads constructor(override var id: Long = 0,
                                                                      override var email: String? = null,
                                                                      override val version: Int = 0) : Action {
  @SerializedName(Action.TAG_NAME_ACTION_TYPE)
  override val type: Action.Type = Action.Type.ENCRYPT_PRIVATE_KEYS

  override fun run(context: Context) {
    val keyEntities = KeysStorageImpl.getInstance(context).getAllPgpPrivateKeys()
    val modifiedKeyEntities = mutableListOf<KeyEntity>()
    val roomDatabase = FlowCryptRoomDatabase.getDatabase(context)

    if (keyEntities.isEmpty()) {
      return
    }

    for (keyEntity in keyEntities) {
      val passphrase = keyEntity.passphrase ?: continue

      val keyDetailsList = NodeCallsExecutor.parseKeys(keyEntity.privateKeyAsString)
      if (keyDetailsList.isEmpty() || keyDetailsList.size != 1) {
        ExceptionUtil.handleError(
            IllegalArgumentException("An error occurred during the key parsing| 1: "
                + if (CollectionUtils.isEmpty(keyDetailsList)) "Empty results" else "Size = " + keyDetailsList.size))
        continue
      }

      val keyDetails = keyDetailsList.first()

      if (keyDetails.isFullyEncrypted == true) {
        continue
      }

      try {
        val encryptedKeyResult = NodeCallsExecutor.encryptKey(keyDetails.privateKey!!, passphrase)

        if (encryptedKeyResult.encryptedKey.isNullOrEmpty()) {
          ExceptionUtil.handleError(IllegalArgumentException("An error occurred during the key encryption"))
          continue
        }

        val encryptedKeyDetailsList = NodeCallsExecutor.parseKeys(encryptedKeyResult.encryptedKey)
        if (encryptedKeyDetailsList.isEmpty() || encryptedKeyDetailsList.size != 1) {
          ExceptionUtil.handleError(IllegalArgumentException("An error occurred during the key parsing| 2"))
          continue
        }

        val keyDetailsWithPgpEncryptedInfo = encryptedKeyDetailsList.first()
        val modifiedKeyEntity = keyEntity.copy(
            privateKey = KeyStoreCryptoManager.encrypt(keyDetailsWithPgpEncryptedInfo.privateKey).toByteArray(),
            publicKey = keyDetailsWithPgpEncryptedInfo.publicKey?.toByteArray()
                ?: keyEntity.publicKey)
        modifiedKeyEntities.add(modifiedKeyEntity)
      } catch (e: NodeException) {
        if (e.nodeError?.msg == "Error: Pass phrase length seems way too low! Pass phrase strength should be properly checked before encrypting a key.") {
          val currentAccount = AccountDaoSource().getActiveAccountInformation(context)
          SystemNotificationManager(context).showPassphraseTooLowNotification(currentAccount)
        }
      }
    }

    roomDatabase.keysDao().update(modifiedKeyEntities)

    SharedPreferencesHelper.setBoolean(PreferenceManager
        .getDefaultSharedPreferences(context), Constants.PREF_KEY_IS_CHECK_KEYS_NEEDED, false)
  }

  constructor(source: Parcel) : this(
      source.readLong(),
      source.readString(),
      source.readInt()
  )

  override fun describeContents(): Int {
    return 0
  }

  override fun writeToParcel(dest: Parcel, flags: Int) =
      with(dest) {
        writeLong(id)
        writeString(email)
        writeInt(version)
      }

  companion object {
    @JvmField
    val CREATOR: Parcelable.Creator<EncryptPrivateKeysIfNeededAction> =
        object : Parcelable.Creator<EncryptPrivateKeysIfNeededAction> {
          override fun createFromParcel(source: Parcel): EncryptPrivateKeysIfNeededAction =
              EncryptPrivateKeysIfNeededAction(source)

          override fun newArray(size: Int): Array<EncryptPrivateKeysIfNeededAction?> = arrayOfNulls(size)
        }
  }
}
