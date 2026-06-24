package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.NetworkCondition
import com.example.data.SyncManager
import com.example.data.Task
import com.example.data.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class TaskViewModel(
    private val repository: TaskRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val logs: StateFlow<List<com.example.data.SyncLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val networkCondition: StateFlow<NetworkCondition> = syncManager.networkCondition
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing

    fun addTask(
        title: String,
        description: String,
        category: String,
        priority: String,
        dueDate: Long
    ) {
        viewModelScope.launch {
            val task = Task(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim(),
                category = category,
                priority = priority,
                dueDate = dueDate,
                isCompleted = false,
                isSynced = false,
                isDeletedLocal = false
            )
            repository.insertTask(task)
            repository.addLog("Local task added: '${task.title}' (Pending sync)", "INFO")
            
            // Trigger sync if online
            if (syncManager.networkCondition.value != NetworkCondition.OFFLINE) {
                syncManager.syncNow()
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(
                isCompleted = !task.isCompleted,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateTask(updatedTask)
            val statusStr = if (updatedTask.isCompleted) "completed" else "uncompleted"
            repository.addLog("Local task '$statusStr': '${updatedTask.title}' (Pending sync)", "INFO")

            // Trigger sync if online
            if (syncManager.networkCondition.value != NetworkCondition.OFFLINE) {
                syncManager.syncNow()
            }
        }
    }

    fun editTask(
        task: Task,
        title: String,
        description: String,
        category: String,
        priority: String,
        dueDate: Long
    ) {
        viewModelScope.launch {
            val updatedTask = task.copy(
                title = title.trim(),
                description = description.trim(),
                category = category,
                priority = priority,
                dueDate = dueDate,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateTask(updatedTask)
            repository.addLog("Local task updated: '${updatedTask.title}' (Pending sync)", "INFO")

            // Trigger sync if online
            if (syncManager.networkCondition.value != NetworkCondition.OFFLINE) {
                syncManager.syncNow()
            }
        }
    }

    fun deleteTask(id: String, title: String) {
        viewModelScope.launch {
            repository.softDeleteTask(id)
            repository.addLog("Local task soft-deleted: '$title' (Deletion pending sync)", "INFO")

            // Trigger sync if online
            if (syncManager.networkCondition.value != NetworkCondition.OFFLINE) {
                syncManager.syncNow()
            }
        }
    }

    fun setNetworkCondition(condition: NetworkCondition) {
        syncManager.setNetworkCondition(condition)
    }

    fun triggerSync() {
        syncManager.syncNow()
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.addLog("Sync logs cleared.", "INFO")
        }
    }

    fun simulateRemoteActivity() {
        syncManager.simulateRemoteCloudActivity()
    }

    class Factory(
        private val repository: TaskRepository,
        private val syncManager: SyncManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
                return TaskViewModel(repository, syncManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
