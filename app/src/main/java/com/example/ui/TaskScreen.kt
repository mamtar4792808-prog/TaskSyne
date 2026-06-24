package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NetworkCondition
import com.example.data.SyncLog
import com.example.data.Task
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val networkCondition by viewModel.networkCondition.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf("All") }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var isConsoleExpanded by remember { mutableStateOf(true) }

    // Filtered Tasks
    val filteredTasks = remember(tasks, selectedCategory) {
        if (selectedCategory == "All") {
            tasks
        } else if (selectedCategory == "Completed") {
            tasks.filter { it.isCompleted }
        } else {
            tasks.filter { it.category == selectedCategory }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "TASK SYNC",
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // Pulsing online badge
                        LiveStatusBadge(networkCondition = networkCondition)
                    }
                },
                actions = {
                    // Sync Button with rotate animation
                    val rotationTransition = rememberInfiniteTransition(label = "sync_rotation")
                    val angle by rotationTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )

                    IconButton(
                        onClick = { viewModel.triggerSync() },
                        enabled = networkCondition != NetworkCondition.OFFLINE,
                        modifier = Modifier
                            .testTag("sync_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync Now",
                            tint = if (networkCondition == NetworkCondition.OFFLINE) Color.Gray else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.rotate(if (isSyncing) angle else 0f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                modifier = Modifier
                    .testTag("add_task_fab")
                    .padding(bottom = if (isConsoleExpanded) 120.dp else 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Simulated Network Selector Dashboard
            NetworkSelectorWidget(
                currentCondition = networkCondition,
                isSyncing = isSyncing,
                onConditionChange = { viewModel.setNetworkCondition(it) },
                onSimulateRemote = { viewModel.simulateRemoteActivity() }
            )

            // Metrics Bar
            MetricsWidget(tasks = tasks)

            // Categories list horizontal row
            CategorySelectorRow(
                selectedCategory = selectedCategory,
                onCategorySelect = { selectedCategory = it },
                tasks = tasks
            )

            // Tasks List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (filteredTasks.isEmpty()) {
                    EmptyStateWidget(selectedCategory = selectedCategory)
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
                    ) {
                        items(filteredTasks, key = { it.id }) { task ->
                            TaskItemCard(
                                task = task,
                                onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                                onEdit = { taskToEdit = task },
                                onDelete = { viewModel.deleteTask(task.id, task.title) }
                            )
                        }
                    }
                }
            }

            // Real-time Console Log Drawer
            ConsoleDrawer(
                logs = logs,
                isExpanded = isConsoleExpanded,
                onToggleExpand = { isConsoleExpanded = !isConsoleExpanded },
                onClearLogs = { viewModel.clearLogs() }
            )
        }
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        TaskFormDialog(
            title = "New Sync Task",
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title, desc, cat, prio, due ->
                viewModel.addTask(title, desc, cat, prio, due)
                showAddTaskDialog = false
            }
        )
    }

    // Edit Task Dialog
    taskToEdit?.let { task ->
        TaskFormDialog(
            title = "Edit Task",
            initialTitle = task.title,
            initialDescription = task.description,
            initialCategory = task.category,
            initialPriority = task.priority,
            initialDueDate = task.dueDate,
            onDismiss = { taskToEdit = null },
            onConfirm = { title, desc, cat, prio, due ->
                viewModel.editTask(task, title, desc, cat, prio, due)
                taskToEdit = null
            }
        )
    }
}

@Composable
fun LiveStatusBadge(networkCondition: NetworkCondition) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val (text, color) = when (networkCondition) {
        NetworkCondition.ONLINE -> "ONLINE" to SlateTertiary
        NetworkCondition.FLAKY -> "FLAKY" to PriorityMedium
        NetworkCondition.OFFLINE -> "OFFLINE" to Color.Gray
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (networkCondition == NetworkCondition.OFFLINE) 1f else alpha))
        )
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun NetworkSelectorWidget(
    currentCondition: NetworkCondition,
    isSyncing: Boolean,
    onConditionChange: (NetworkCondition) -> Unit,
    onSimulateRemote: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Simulated Environment Controls",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )

                Button(
                    onClick = onSimulateRemote,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("simulate_event_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Simulate Cloud Event",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Trigger Cloud Event", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Triple Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NetworkOptionButton(
                    condition = NetworkCondition.ONLINE,
                    label = "Always Online",
                    isSelected = currentCondition == NetworkCondition.ONLINE,
                    onClick = { onConditionChange(NetworkCondition.ONLINE) },
                    modifier = Modifier.weight(1f)
                )

                NetworkOptionButton(
                    condition = NetworkCondition.FLAKY,
                    label = "Flaky Sync",
                    isSelected = currentCondition == NetworkCondition.FLAKY,
                    onClick = { onConditionChange(NetworkCondition.FLAKY) },
                    modifier = Modifier.weight(1f)
                )

                NetworkOptionButton(
                    condition = NetworkCondition.OFFLINE,
                    label = "Offline Local",
                    isSelected = currentCondition == NetworkCondition.OFFLINE,
                    onClick = { onConditionChange(NetworkCondition.OFFLINE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun NetworkOptionButton(
    condition: NetworkCondition,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        when (condition) {
            NetworkCondition.ONLINE -> SlateTertiary.copy(alpha = 0.2f)
            NetworkCondition.FLAKY -> PriorityMedium.copy(alpha = 0.2f)
            NetworkCondition.OFFLINE -> Color.Gray.copy(alpha = 0.2f)
        }
    } else {
        MaterialTheme.colorScheme.background
    }

    val borderColor = if (isSelected) {
        when (condition) {
            NetworkCondition.ONLINE -> SlateTertiary
            NetworkCondition.FLAKY -> PriorityMedium
            NetworkCondition.OFFLINE -> Color.Gray
        }
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    val icon = when (condition) {
        NetworkCondition.ONLINE -> Icons.Default.CheckCircle
        NetworkCondition.FLAKY -> Icons.Default.Warning
        NetworkCondition.OFFLINE -> Icons.Default.Close
    }

    val testTagStr = "network_toggle_${label.lowercase().replace(" ", "_")}"

    Surface(
        modifier = modifier
            .testTag(testTagStr)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) borderColor else Color.Gray,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color.Gray
            )
        }
    }
}

@Composable
fun MetricsWidget(tasks: List<Task>) {
    val totalCount = tasks.size
    val completedCount = tasks.count { it.isCompleted }
    val pendingSyncCount = tasks.count { !it.isSynced }
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.3f)) {
                Text(
                    text = "Completion Index",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$completedCount",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "/$totalCount done",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                modifier = Modifier.weight(0.7f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "Out of Sync",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (pendingSyncCount > 0) PriorityMedium else SlateTertiary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$pendingSyncCount tasks",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pendingSyncCount > 0) PriorityMedium else SlateTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun CategorySelectorRow(
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    tasks: List<Task>
) {
    val categories = listOf("All", "Work", "Personal", "Shopping", "Health", "Completed")

    ScrollableTabRow(
        selectedTabIndex = categories.indexOf(selectedCategory),
        edgePadding = 16.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {},
        indicator = {},
        modifier = Modifier.fillMaxWidth()
    ) {
        categories.forEach { category ->
            val isSelected = category == selectedCategory
            val taskCount = when (category) {
                "All" -> tasks.size
                "Completed" -> tasks.count { it.isCompleted }
                else -> tasks.count { it.category == category }
            }

            Tab(
                selected = isSelected,
                onClick = { onCategorySelect(category) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Gray.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$taskCount",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("category_tab_$category")
            )
        }
    }
}

@Composable
fun TaskItemCard(
    task: Task,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val priorityColor = when (task.priority) {
        "High" -> PriorityHigh
        "Medium" -> PriorityMedium
        "Low" -> PriorityLow
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (task.isSynced) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            else PriorityMedium.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Task completed Checkbox with 48dp touch bounds
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onToggleComplete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.Check,
                    contentDescription = "Toggle Complete",
                    tint = if (task.isCompleted) SlateTertiary else Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Task content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.category,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Priority indicator dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(priorityColor)
                    )
                    Text(
                        text = task.priority,
                        fontSize = 9.sp,
                        color = priorityColor,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Sync Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (task.isSynced) Icons.Default.Check else Icons.Default.Refresh,
                            contentDescription = if (task.isSynced) "Synced with server" else "Pending Sync",
                            tint = if (task.isSynced) SlateTertiary else PriorityMedium,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = if (task.isSynced) "Synced" else "Offline Pending",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (task.isSynced) SlateTertiary else PriorityMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = task.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task.isCompleted) Color.Gray else Color.White,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Due date
                val sdf = remember { SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()) }
                Text(
                    text = "Due: ${sdf.format(Date(task.dueDate))}",
                    fontSize = 9.sp,
                    color = Color.LightGray.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action buttons with 48dp touch bounds
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Task",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Task",
                        tint = PriorityHigh,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateWidget(selectedCategory: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "No tasks found",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tasks in '$selectedCategory'",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Create a task locally and watch it synchronize in real-time once you connect to the cloud server.",
            fontSize = 12.sp,
            color = Color.Gray,
            lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun ConsoleDrawer(
    logs: List<SyncLog>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClearLogs: () -> Unit
) {
    val height by animateDpAsState(
        targetValue = if (isExpanded) 190.dp else 40.dp,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "console_height"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))),
        color = Color(0xFF070A13) // Extra dark monospaced background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(SlateSecondary)
                    )
                    Text(
                        text = "REAL-TIME SYNC CONSOLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isExpanded) {
                        Text(
                            text = "CLEAR CONSOLE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = PriorityHigh,
                            modifier = Modifier
                                .clickable(onClick = onClearLogs)
                                .testTag("clear_logs_button")
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Menu else Icons.Default.Menu,
                        contentDescription = "Toggle Console",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (isExpanded) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                val logListState = rememberLazyListState()

                // Scroll to top automatically when logs change
                LaunchedEffect(logs.size) {
                    if (logs.isNotEmpty()) {
                        logListState.animateScrollToItem(0)
                    }
                }

                LazyColumn(
                    state = logListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        val logColor = when (log.type) {
                            "SUCCESS" -> SlateTertiary
                            "WARNING" -> PriorityMedium
                            "ERROR" -> PriorityHigh
                            else -> SlateSecondary
                        }

                        val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
                        val timeStr = timeFormat.format(Date(log.timestamp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "$timeStr ",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = "-> ${log.message}",
                                color = logColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskFormDialog(
    title: String,
    initialTitle: String = "",
    initialDescription: String = "",
    initialCategory: String = "Work",
    initialPriority: String = "Medium",
    initialDueDate: Long = System.currentTimeMillis() + 86400000,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Long) -> Unit
) {
    var titleText by remember { mutableStateOf(initialTitle) }
    var descriptionText by remember { mutableStateOf(initialDescription) }
    var category by remember { mutableStateOf(initialCategory) }
    var priority by remember { mutableStateOf(initialPriority) }
    var dueDate by remember { mutableStateOf(initialDueDate) }

    val categories = listOf("Work", "Personal", "Shopping", "Health")
    val priorities = listOf("Low", "Medium", "High")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )

                // Title Input
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Task Title") },
                    placeholder = { Text("e.g. Sync local Spanner schema") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                // Description Input
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Description") },
                    placeholder = { Text("Details of the task...") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_desc_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                // Category selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Category", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = category == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { category = cat }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    }
                }

                // Priority selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Priority", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        priorities.forEach { prio ->
                            val isSelected = priority == prio
                            val color = when (prio) {
                                "High" -> PriorityHigh
                                "Medium" -> PriorityMedium
                                "Low" -> PriorityLow
                                else -> Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) color.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) color
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { priority = prio }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = prio,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) color else Color.Gray
                                )
                            }
                        }
                    }
                }

                // Quick Due Date offset selector (e.g., Today, Tomorrow, Next Week)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Due Date Offset", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val offsets = listOf("Today", "Tomorrow", "In 3 Days", "In 1 Week")
                        offsets.forEachIndexed { idx, label ->
                            val offsetMs = when (idx) {
                                0 -> 3600000 * 4 // +4 hours
                                1 -> 86400000 // +1 day
                                2 -> 86400000 * 3 // +3 days
                                3 -> 86400000 * 7 // +7 days
                                else -> 0L
                            }
                            val targetVal = System.currentTimeMillis() + offsetMs
                            // Rough approximation of selection
                            val isSelected = Math.abs(dueDate - targetVal) < 600000 // within 10 mins

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { dueDate = System.currentTimeMillis() + offsetMs }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dialog_cancel_button")
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (titleText.isNotBlank()) {
                                onConfirm(titleText, descriptionText, category, priority, dueDate)
                            }
                        },
                        enabled = titleText.isNotBlank(),
                        modifier = Modifier.testTag("dialog_save_button")
                    ) {
                        Text("Save Task")
                    }
                }
            }
        }
    }
}
