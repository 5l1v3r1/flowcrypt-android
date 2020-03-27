/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.flowcrypt.email.database.entity.ActionQueueEntity

/**
 * This object describes a logic of working with [ActionQueueEntity] in the local database.
 *
 * @author Denis Bondarenko
 * Date: 30.01.2018
 * Time: 10:00
 * E-mail: DenBond7@gmail.com
 */
@Dao
interface ActionQueueDao : BaseDao<ActionQueueEntity> {
  @Query("SELECT * FROM action_queue WHERE email = :email")
  suspend fun getActionsByEmailSuspend(email: String?): List<ActionQueueEntity>

  @Query("DELETE FROM action_queue WHERE _id = :id")
  suspend fun deleteByIdSuspend(id: Long)
}
