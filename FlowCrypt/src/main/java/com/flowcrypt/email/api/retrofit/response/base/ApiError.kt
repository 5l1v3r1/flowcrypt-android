/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.base

import android.os.Parcel
import android.os.Parcelable

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * This POJO object describes a base error from the API.
 *
 * @author Denis Bondarenko
 * Date: 12.07.2017
 * Time: 9:26
 * E-mail: DenBond7@gmail.com
 */

data class ApiError constructor(@Expose val code: Int = 0,
                                @SerializedName("message") @Expose val msg: String? = null,
                                @Expose val internal: String? = null,
                                @Expose val stack: String? = null,
                                @Expose val type: String? = null) : Parcelable {
  constructor(source: Parcel) : this(
      source.readInt(),
      source.readString(),
      source.readString(),
      source.readString(),
      source.readString()
  )

  override fun describeContents() = 0

  override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
    writeInt(code)
    writeString(msg)
    writeString(internal)
    writeString(stack)
    writeString(type)
  }

  companion object {
    @JvmField
    val CREATOR: Parcelable.Creator<ApiError> = object : Parcelable.Creator<ApiError> {
      override fun createFromParcel(source: Parcel): ApiError = ApiError(source)
      override fun newArray(size: Int): Array<ApiError?> = arrayOfNulls(size)
    }
  }
}
