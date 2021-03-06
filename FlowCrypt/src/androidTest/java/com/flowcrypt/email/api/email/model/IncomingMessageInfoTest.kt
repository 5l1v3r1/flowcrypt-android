/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.model

import android.net.Uri
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.flowcrypt.email.Constants
import com.flowcrypt.email.DoesNotNeedMailserver
import com.flowcrypt.email.api.retrofit.response.model.node.Algo
import com.flowcrypt.email.api.retrofit.response.model.node.AttMeta
import com.flowcrypt.email.api.retrofit.response.model.node.BaseMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.node.DecryptError
import com.flowcrypt.email.api.retrofit.response.model.node.DecryptErrorDetails
import com.flowcrypt.email.api.retrofit.response.model.node.DecryptErrorMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.node.DecryptedAttMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.node.KeyId
import com.flowcrypt.email.api.retrofit.response.model.node.Longids
import com.flowcrypt.email.api.retrofit.response.model.node.MsgBlock
import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails
import com.flowcrypt.email.api.retrofit.response.model.node.PublicKeyMsgBlock
import com.flowcrypt.email.database.entity.MessageEntity
import com.flowcrypt.email.model.MessageEncryptionType
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Denis Bondarenko
 * Date: 5/14/19
 * Time: 9:45 AM
 * E-mail: DenBond7@gmail.com
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
@DoesNotNeedMailserver
class IncomingMessageInfoTest {

  @Test
  fun testParcelable() {
    val att1 = AttachmentInfo("rawData",
        "email",
        "folder",
        12,
        "fwdFolder",
        102,
        "name",
        123456,
        Constants.MIME_TYPE_BINARY_DATA,
        "1245fsdfs4597sdf4564",
        "0",
        Uri.EMPTY,
        isProtected = true,
        isForwarded = false,
        isEncryptionAllowed = true,
        orderNumber = 12)

    val att2 = AttachmentInfo("rawData",
        "email",
        "folder",
        12,
        "fwdFolder",
        102,
        "name",
        123456,
        Constants.MIME_TYPE_BINARY_DATA,
        "1245fsdfs4597sdf4564",
        "0",
        Uri.EMPTY,
        isProtected = true,
        isForwarded = false,
        isEncryptionAllowed = true,
        orderNumber = 12)

    val msgEntity = MessageEntity(
        1212121,
        "mail",
        "folder",
        122321321,
        1557815912496,
        1557815912496,
        "from_address",
        "to_address",
        "cc_address",
        "subject",
        "flags",
        "raw_message_without_attachments",
        true,
        true,
        false,
        -1,
        "attachments_directory",
        "error_msg",
        "reply_to")

    val publicKeyMsgBlock = PublicKeyMsgBlock(
        "content",
        true,
        NodeKeyDetails(isFullyDecrypted = false,
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

    val decryptErrorMsgBlock = DecryptErrorMsgBlock(
        "content",
        true,
        DecryptError(true,
            DecryptErrorDetails(
                DecryptErrorDetails.Type.FORMAT,
                "message"),
            Longids(
                listOf("message"),
                listOf("matching"),
                listOf("chosen"),
                listOf("needPassphrase")
            ),
            true))

    val decryptedAttMsgBlock = DecryptedAttMsgBlock(
        "content",
        true,
        AttMeta("name", "data", 100L, "type"),
        DecryptError(true,
            DecryptErrorDetails(
                DecryptErrorDetails.Type.FORMAT,
                "message"),
            Longids(
                listOf("message"),
                listOf("matching"),
                listOf("chosen"),
                listOf("needPassphrase")
            ),
            true))

    val original = IncomingMessageInfo(
        msgEntity,
        listOf(att1, att2),
        LocalFolder("account",
            "fullName",
            "folderAlias",
            listOf("attributes"),
            true,
            12,
            "searchQuery"),
        "text",
        "inlineSubject",
        listOf(
            BaseMsgBlock(MsgBlock.Type.UNKNOWN, "someContent", false),
            BaseMsgBlock(MsgBlock.Type.UNKNOWN, "content", false),
            publicKeyMsgBlock,
            decryptErrorMsgBlock,
            decryptedAttMsgBlock),
        null,
        MessageEncryptionType.STANDARD)

    val parcel = Parcel.obtain()
    original.writeToParcel(parcel, original.describeContents())
    parcel.setDataPosition(0)

    val createdFromParcel = IncomingMessageInfo.CREATOR.createFromParcel(parcel)
    Assert.assertTrue(null, original == createdFromParcel)
  }
}