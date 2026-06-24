package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.SyncManager
import com.example.data.TaskRepository
import com.example.ui.TaskScreen
import com.example.ui.TaskViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize local SQLite Database and DAOs via Room
        val database = AppDatabase.getDatabase(this)
        val taskDao = database.taskDao()
        val syncLogDao = database.syncLogDao()

        // 2. Initialize Repository and SyncManager
        val repository = TaskRepository(taskDao, syncLogDao)
        val syncManager = SyncManager(repository, lifecycleScope)

        // 3. Create ViewModel using custom Factory
        val viewModelFactory = TaskViewModel.Factory(repository, syncManager)
        val viewModel: TaskViewModel by viewModels { viewModelFactory }

        setContent {
            MyApplicationTheme {
                TaskScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
