/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.database.dao

import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails
import com.flowcrypt.email.model.KeyDetails
import com.flowcrypt.email.security.KeyStoreCryptoManager
import com.flowcrypt.email.security.model.PrivateKeySourceType

/**
 * This class describe a key information object.
 *
 * @author DenBond7
 * Date: 13.05.2017
 * Time: 12:54
 * E-mail: DenBond7@gmail.com
 */

data class KeysDaoCompatibility constructor(var longId: String? = null,
                                            var privateKeySourceType: PrivateKeySourceType? = null,
                                            var publicKey: String? = null,
                                            var privateKey: String? = null,
                                            var passphrase: String? = null) {

  companion object {
    /**
     * Generate [KeysDaoCompatibility] using input parameters.
     * This method use [NodeKeyDetails.getLongId] for generate an algorithm parameter spec String and
     * [KeyStoreCryptoManager] for generate encrypted version of the private key and password.
     *
     * @param keyStoreCryptoManager A [KeyStoreCryptoManager] which will bu used to encrypt
     * information about a key;
     * @param type                  The private key born type
     * @param nodeKeyDetails        Key details;
     * @param passphrase            A passphrase which user provided;
     */
    @JvmStatic
    fun generateKeysDao(type: KeyDetails.Type,
                        nodeKeyDetails: NodeKeyDetails, passphrase: String): KeysDaoCompatibility {
      val keysDao = generateKeysDao(nodeKeyDetails, passphrase)

      when (type) {
        KeyDetails.Type.EMAIL -> keysDao.privateKeySourceType = PrivateKeySourceType.BACKUP

        KeyDetails.Type.FILE, KeyDetails.Type.CLIPBOARD -> keysDao.privateKeySourceType = PrivateKeySourceType.IMPORT

        KeyDetails.Type.NEW -> keysDao.privateKeySourceType = PrivateKeySourceType.NEW
      }

      return keysDao
    }

    /**
     * Generate [KeysDaoCompatibility] using input parameters.
     * This method use [NodeKeyDetails.getLongId] for generate an algorithm parameter spec String and
     * [KeyStoreCryptoManager] for generate encrypted version of the private key and password.
     *
     * @param keyStoreCryptoManager A [KeyStoreCryptoManager] which will bu used to encrypt
     * information about a key;
     * @param nodeKeyDetails        Key details;
     * @param passphrase            A passphrase which user provided;
     */
    @JvmStatic
    fun generateKeysDao(nodeKeyDetails: NodeKeyDetails,
                        passphrase: String): KeysDaoCompatibility {
      if (nodeKeyDetails.isDecrypted!!) {
        throw IllegalArgumentException("Error. The key is decrypted!")
      }

      val keysDao = KeysDaoCompatibility()

      keysDao.longId = nodeKeyDetails.longId
      keysDao.privateKey = KeyStoreCryptoManager.encrypt(nodeKeyDetails.privateKey)
      keysDao.publicKey = nodeKeyDetails.publicKey
      keysDao.passphrase = KeyStoreCryptoManager.encrypt(passphrase)
      return keysDao
    }
  }
}
