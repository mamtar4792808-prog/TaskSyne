package com.example.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val taskDao: TaskDao,
    private val syncLogDao: SyncLogDao
) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val allLogs: Flow<List<SyncLog>> = syncLogDao.getAllLogs()

    suspend fun insertTask(task: Task) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
    }

    suspend fun softDeleteTask(id: String) {
        taskDao.softDeleteTask(id)
    }

    suspend fun hardDeleteTask(task: Task) {
        taskDao.hardDeleteTask(task)
    }

    suspend fun getTaskById(id: String): Task? {
        return taskDao.getTaskById(id)
    }

    suspend fun getUnsyncedTasks(): List<Task> {
        return taskDao.getUnsyncedTasks()
    }

    suspend fun markAsSynced(ids: List<String>) {
        taskDao.markAsSynced(ids)
    }

    suspend fun purgeSyncedDeletedTasks() {
        taskDao.purgeSyncedDeletedTasks()
    }

    suspend fun clearAllTasks() {
        taskDao.clearAllTasks()
    }

    suspend fun addLog(message: String, type: String) {
        syncLogDao.insertLog(SyncLog(message = message, type = type))
    }

    suspend fun clearLogs() {
        syncLogDao.clearLogs()
    }
}
