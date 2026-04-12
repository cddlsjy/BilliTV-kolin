package com.xycz.bilibili_live.data.local.dao

import androidx.room.*
import com.xycz.bilibili_live.data.local.entity.FollowUser
import kotlinx.coroutines.flow.Flow

/**
 * 关注用户DAO
 */
@Dao
interface FollowUserDao {
    @Query("SELECT * FROM follow_users ORDER BY addTime DESC")
    fun getAll(): Flow<List<FollowUser>>

    @Query("SELECT * FROM follow_users WHERE id = :id")
    suspend fun getById(id: String): FollowUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: FollowUser)

    @Delete
    suspend fun delete(user: FollowUser)

    @Query("DELETE FROM follow_users WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM follow_users WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
