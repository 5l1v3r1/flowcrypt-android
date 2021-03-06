/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.model.node

import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.flowcrypt.email.DoesNotNeedMailserver
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Denis Bondarenko
 * Date: 5/17/19
 * Time: 5:38 PM
 * E-mail: DenBond7@gmail.com
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
@DoesNotNeedMailserver
class PublicKeyMsgBlockTest {
  @Test
  fun testParcelable() {
    val original = PublicKeyMsgBlock(
        "content",
        true,
        NodeKeyDetails(
            isFullyDecrypted = false,
            isFullyEncrypted = false,
            privateKey = "privateKey",
            publicKey = "pubKey",
            users = listOf("Hello<hello@example.com>"),
            ids = listOf(KeyId(
                "fingerprint",
                "longId",
                "shortId",
                "keywords"
            )),
            created = 12,
            algo = Algo(
                "algorithm",
                12,
                2048,
                "curve"),
            passphrase = "passphrase",
            errorMsg = "errorMsg"))

    val parcel = Parcel.obtain()
    original.writeToParcel(parcel, original.describeContents())
    parcel.setDataPosition(0)

    val createdFromParcel = PublicKeyMsgBlock.CREATOR.createFromParcel(parcel)
    Assert.assertTrue(null, original == createdFromParcel)
  }
}