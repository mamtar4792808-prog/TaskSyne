package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class NetworkCondition {
    ONLINE,
    FLAKY,
    OFFLINE
}

class SyncManager(
    private val repository: TaskRepository,
    private val scope: CoroutineScope
) {
    private val _networkCondition = MutableStateFlow(NetworkCondition.ONLINE)
    val networkCondition: StateFlow<NetworkCondition> = _networkCondition.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Simulated Server Database State
    private val serverDatabase = mutableListOf<Task>()

    init {
        // Pre-populate some tasks on the "Server" for interactive pulling demo
        serverDatabase.add(
            Task(
                id = "server-task-1",
                title = "☁️ [Cloud Task] Refactor database schema",
                description = "Optimize indexing for query performance across multiple environments.",
                category = "Work",
                priority = "High",
                dueDate = System.currentTimeMillis() + 86400000 * 2, // 2 days from now
                isCompleted = false,
                createdAt = System.currentTimeMillis() - 3600000, // 1 hour ago
                updatedAt = System.currentTimeMillis() - 3600000,
                isSynced = true
            )
        )
        serverDatabase.add(
            Task(
                id = "server-task-2",
                title = "🛒 [Cloud Task] Weekly Grocery Shopping",
                description = "Get organic milk, fresh spinach, eggs, and whole wheat bread.",
                category = "Shopping",
                priority = "Medium",
                dueDate = System.currentTimeMillis() + 86400000, // 1 day from now
                isCompleted = true,
                createdAt = System.currentTimeMillis() - 7200000, // 2 hours ago
                updatedAt = System.currentTimeMillis() - 7200000,
                isSynced = true
            )
        )

        // Add initial system logs
        scope.launch(Dispatchers.IO) {
            repository.addLog("Local database initialized successfully.", "INFO")
            repository.addLog("Network connection established: Connected to Master Sync Server.", "SUCCESS")
        }
    }

    fun setNetworkCondition(condition: NetworkCondition) {
        _networkCondition.value = condition
        scope.launch(Dispatchers.IO) {
            when (condition) {
                NetworkCondition.ONLINE -> {
                    repository.addLog("Network condition changed: ONLINE (Cloud Node Connected)", "SUCCESS")
                    // Auto-trigger sync when returning online
                    syncNow()
                }
                NetworkCondition.FLAKY -> {
                    repository.addLog("Network condition changed: FLAKY (High Packet Loss Mode enabled)", "WARNING")
                    syncNow()
                }
                NetworkCondition.OFFLINE -> {
                    repository.addLog("Network condition changed: OFFLINE (Local-Only Sandbox enabled)", "INFO")
                }
            }
        }
    }

    /**
     * Performs a bidirectional synchronization:
     * 1. Pushes local unsynced changes to the "Server"
     * 2. Pulls server changes and merges them locally using Last-Write-Wins (LWW)
     */
    fun syncNow() {
        if (_isSyncing.value) return

        scope.launch(Dispatchers.IO) {
            if (_networkCondition.value == NetworkCondition.OFFLINE) {
                repository.addLog("Synchronization aborted: Device is currently OFFLINE.", "ERROR")
                return@launch
            }

            _isSyncing.value = true
            repository.addLog("Initiating bidirectional synchronization protocol...", "INFO")
            delay(1500) // Simulate network latency

            // Determine network success based on flaky setting
            if (_networkCondition.value == NetworkCondition.FLAKY && (1..100).random() > 50) {
                repository.addLog("❌ [Network Error] Sync failed: Connection timed out (504 Gateway Timeout). Retry scheduled.", "ERROR")
                _isSyncing.value = false
                return@launch
            }

            try {
                // 1. PUSH local changes to the server
                val unsyncedLocalTasks = repository.getUnsyncedTasks()
                var pushedCount = 0
                var deletedCount = 0

                for (localTask in unsyncedLocalTasks) {
                    // Check if deleted locally
                    if (localTask.isDeletedLocal) {
                        // Remove from server database if it exists
                        serverDatabase.removeAll { it.id == localTask.id }
                        // Hard delete from local database to purge garbage
                        repository.hardDeleteTask(localTask)
                        deletedCount++
                    } else {
                        // Push or update on server
                        val existingIndex = serverDatabase.indexOfFirst { it.id == localTask.id }
                        if (existingIndex != -1) {
                            // Conflict Check (Last-Write-Wins)
                            val serverTask = serverDatabase[existingIndex]
                            if (localTask.updatedAt >= serverTask.updatedAt) {
                                serverDatabase[existingIndex] = localTask.copy(isSynced = true)
                                pushedCount++
                            } else {
                                repository.addLog("⚠️ [Conflict resolved] Server version is newer for '${localTask.title}'. Keep Server.", "WARNING")
                            }
                        } else {
                            // Fresh item
                            serverDatabase.add(localTask.copy(isSynced = true))
                            pushedCount++
                        }
                    }
                }

                // Mark pushed tasks as synced in Room
                if (unsyncedLocalTasks.isNotEmpty()) {
                    val pushedIds = unsyncedLocalTasks.filter { !it.isDeletedLocal }.map { it.id }
                    if (pushedIds.isNotEmpty()) {
                        repository.markAsSynced(pushedIds)
                    }
                }

                // 2. PULL changes from server database
                var pulledCount = 0
                val allLocalTasksFlow = repository.allTasks
                // Retrieve current active/inactive items from database in a suspension query
                // We'll perform database operations on specific items
                for (serverTask in serverDatabase) {
                    val localTask = repository.getTaskById(serverTask.id)
                    if (localTask == null) {
                        // Pull from server as a new task
                        repository.insertTask(serverTask.copy(isSynced = true))
                        pulledCount++
                    } else {
                        // Check if server version is newer
                        if (serverTask.updatedAt > localTask.updatedAt) {
                            repository.insertTask(serverTask.copy(isSynced = true))
                            pulledCount++
                        }
                    }
                }

                // Clean up any local sync deleted items
                repository.purgeSyncedDeletedTasks()

                // Generate sync report
                val logMsg = StringBuilder("Sync completed successfully: ")
                if (pushedCount > 0) logMsg.append("Pushed $pushedCount updates. ")
                if (deletedCount > 0) logMsg.append("Deleted $deletedCount items remotely. ")
                if (pulledCount > 0) logMsg.append("Pulled $pulledCount updates from server. ")
                if (pushedCount == 0 && deletedCount == 0 && pulledCount == 0) {
                    logMsg.append("Local and Cloud nodes are fully in sync.")
                }

                repository.addLog(logMsg.toString(), "SUCCESS")

            } catch (e: Exception) {
                repository.addLog("❌ Critical error during synchronization: ${e.message}", "ERROR")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Simulate another device adding a task to the Cloud Server.
     */
    fun simulateRemoteCloudActivity() {
        scope.launch(Dispatchers.IO) {
            val taskNames = listOf(
                "🏃 [Cloud Task] Evening Cardio Run",
                "💡 [Cloud Task] Prep slides for Sync Presentation",
                "📞 [Cloud Task] Sync Call with Global Infrastructure Team",
                "🔧 [Cloud Task] Server Maintenance Checklist"
            )
            val categories = listOf("Health", "Work", "Work", "Personal")
            val priorities = listOf("Medium", "High", "High", "Low")
            val descriptions = listOf(
                "Run 5k around the lake to maintain aerobic stamina.",
                "Organize offline-first architecture blueprints and Room design diagrams.",
                "Review multi-node sync speed indices and packet loss recovery logs.",
                "Audit backup logs and update container storage quotas."
            )

            val randomIndex = (0 until taskNames.size).random()
            val newTask = Task(
                id = UUID.randomUUID().toString(),
                title = taskNames[randomIndex],
                description = descriptions[randomIndex],
                category = categories[randomIndex],
                priority = priorities[randomIndex],
                dueDate = System.currentTimeMillis() + 86400000,
                isCompleted = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isSynced = true
            )

            serverDatabase.add(newTask)
            repository.addLog("📡 [Cloud Server] Remote user added a new task to Cloud Node.", "INFO")

            // If we are currently online, auto-sync this!
            if (_networkCondition.value != NetworkCondition.OFFLINE) {
                syncNow()
            }
        }
    }
}
