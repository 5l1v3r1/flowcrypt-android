/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.request.node

import com.flowcrypt.email.api.retrofit.node.NodeService
import com.flowcrypt.email.api.retrofit.request.model.node.PrivateKeyInfo
import com.flowcrypt.email.model.PgpKeyInfo
import com.google.gson.annotations.Expose
import retrofit2.Response

/**
 * Using this class we can create a request to decrypt an encrypted file using the given private keys.
 *
 * @author Denis Bondarenko
 * Date: 1/15/19
 * Time: 4:32 PM
 * E-mail: DenBond7@gmail.com
 */
class DecryptFileRequest(override val data: ByteArray,
                         pgpKeyInfos: List<PgpKeyInfo>) :
    BaseNodeRequest() {

  @Expose
  private val keys: List<PrivateKeyInfo> = pgpKeyInfos.map { PrivateKeyInfo(it.private!!, it.longid, it.passphrase) }

  override val endpoint: String = "decryptFile"

  override fun getResponse(nodeService: NodeService): Response<*> {
    return nodeService.decryptFile(this).execute()
  }
}
