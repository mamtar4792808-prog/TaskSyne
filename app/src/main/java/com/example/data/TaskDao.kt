package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isDeletedLocal = 0 ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isSynced = 0")
    suspend fun getUnsyncedTasks(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): Task?

    @Query("UPDATE tasks SET isDeletedLocal = 1, isSynced = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteTask(id: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun hardDeleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE isDeletedLocal = 1 AND isSynced = 1")
    suspend fun purgeSyncedDeletedTasks()

    @Query("UPDATE tasks SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()
}
