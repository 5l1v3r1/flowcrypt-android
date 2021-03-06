/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.model

import android.os.Parcel
import android.os.Parcelable

/**
 * This class describes a details information about auth settings for some IMAP and SMTP servers.
 *
 * @author DenBond7
 * Date: 14.09.2017.
 * Time: 15:11.
 * E-mail: DenBond7@gmail.com
 */
data class AuthCredentials constructor(val email: String,
                                       val username: String,
                                       var password: String,
                                       val imapServer: String,
                                       val imapPort: Int = 143,
                                       val imapOpt: SecurityType.Option = SecurityType.Option.NONE,
                                       val smtpServer: String,
                                       val smtpPort: Int = 25,
                                       val smtpOpt: SecurityType.Option = SecurityType.Option.NONE,
                                       val hasCustomSignInForSmtp: Boolean = false,
                                       val smtpSigInUsername: String? = null,
                                       var smtpSignInPassword: String? = null) : Parcelable {
  constructor(source: Parcel) : this(
      source.readString()!!,
      source.readString()!!,
      source.readString()!!,
      source.readString()!!,
      source.readInt(),
      source.readParcelable(SecurityType.Option::class.java.classLoader)!!,
      source.readString()!!,
      source.readInt(),
      source.readParcelable(SecurityType.Option::class.java.classLoader)!!,
      source.readByte() != 0.toByte(),
      source.readString(),
      source.readString()
  )

  override fun describeContents() = 0

  override fun writeToParcel(dest: Parcel, flags: Int) {
    with(dest) {
      writeString(email)
      writeString(username)
      writeString(password)
      writeString(imapServer)
      writeInt(imapPort)
      writeParcelable(imapOpt, flags)
      writeString(smtpServer)
      writeInt(smtpPort)
      writeParcelable(smtpOpt, flags)
      writeInt((if (hasCustomSignInForSmtp) 1 else 0))
      writeString(smtpSigInUsername)
      writeString(smtpSignInPassword)
    }
  }

  companion object {
    @JvmField
    @Suppress("unused")
    val CREATOR: Parcelable.Creator<AuthCredentials> = object : Parcelable.Creator<AuthCredentials> {
      override fun createFromParcel(source: Parcel): AuthCredentials = AuthCredentials(source)
      override fun newArray(size: Int): Array<AuthCredentials?> = arrayOfNulls(size)
    }
  }
}
