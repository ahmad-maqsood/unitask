package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Persists group tasks, done marks, and group courses per user in Room,
 * allowing full offline functionality on the Deadlines tab and local alarms.
 */
@Entity(
    tableName = "group_cache",
    primaryKeys = ["userId", "groupId"]
)
data class GroupCacheEntity(
    val userId: String,
    val groupId: String,
    val groupName: String,
    val tasksJson: String,
    val doneJson: String,
    val coursesJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface GroupCacheDao {
    @Query("SELECT * FROM group_cache WHERE userId = :userId")
    suspend fun getForUser(userId: String): List<GroupCacheEntity>

    @Query("SELECT * FROM group_cache WHERE userId = :userId")
    fun observeForUser(userId: String): Flow<List<GroupCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entities: List<GroupCacheEntity>)

    @Query("DELETE FROM group_cache WHERE userId = :userId AND groupId NOT IN (:keepGroupIds)")
    suspend fun deleteGroupsNotIn(userId: String, keepGroupIds: List<String>)

    @Query("DELETE FROM group_cache WHERE userId = :userId AND groupId = :groupId")
    suspend fun deleteGroup(userId: String, groupId: String)

    @Query("DELETE FROM group_cache WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Query("DELETE FROM group_cache")
    suspend fun clearAll()

    @Query("UPDATE group_cache SET doneJson = :doneJson WHERE userId = :userId AND groupId = :groupId")
    suspend fun updateDone(userId: String, groupId: String, doneJson: String)

    @Query("SELECT * FROM group_cache WHERE userId = :userId AND groupId = :groupId LIMIT 1")
    suspend fun getGroup(userId: String, groupId: String): GroupCacheEntity?
}
