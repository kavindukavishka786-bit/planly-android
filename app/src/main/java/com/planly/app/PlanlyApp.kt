@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.planly.app

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat get() = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dayFormat get() = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
private fun Task.timeLabel() = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).format(timeFormat)
private val pages = listOf("Today", "Calendar", "Tasks", "Settings")
private val destinations = pages + listOf("Focus", "AI planner", "Progress", "App Health")
private fun pageIcon(page: String): ImageVector = when (page) {
    "Today" -> Icons.Outlined.Home
    "Calendar" -> Icons.Outlined.CalendarMonth
    "Tasks" -> Icons.Outlined.TaskAlt
    "Focus" -> Icons.Outlined.Timer
    "AI planner" -> Icons.Outlined.AutoAwesome
    "Progress" -> Icons.Outlined.BarChart
    "App Health" -> Icons.Outlined.HealthAndSafety
    else -> Icons.Outlined.Settings
}

@Composable fun PlanlyApp(openTask: String?, onOpened: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val data by store.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("Today") }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val owner = LocalLifecycleOwner.current
    var permissionsVersion by remember { mutableIntStateOf(0) }
    var notificationPurpose by rememberSaveable { mutableStateOf("Tasks") }
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val prefs = store.state.value.preferences
        when (notificationPurpose) {
            "Tasks" -> store.preferences(prefs.copy(reminders = granted))
            "Breaks" -> store.preferences(prefs.copy(screenBreaks = granted))
            else -> scope.launch(kotlinx.coroutines.Dispatchers.IO) { store.reschedule(force = true) }
        }
        permissionsVersion++
        if (!granted) message("Notifications are off. You can enable them in Android Settings.")
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            permissionsVersion++; scope.launch(kotlinx.coroutines.Dispatchers.IO) { store.reschedule() }
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(openTask) { if (openTask != null) { detail = openTask; onOpened() } }
    LaunchedEffect(data.focusEnd) {
        if (data.focusEnd > 0) {
            delay((data.focusEnd - System.currentTimeMillis()).coerceAtLeast(0))
            if (store.finishFocus()) message("Focus session complete. Take a short break.")
        }
    }
    BackHandler(drawer.isOpen || detail != null || page != "Today") {
        when { drawer.isOpen -> scope.launch { drawer.close() }; detail != null -> detail = null; else -> page = "Today" }
    }
    val task = data.tasks.find { it.id == detail }
    PlanlyTheme(data.preferences.theme) {
        val p = LocalPlanly.current
        ModalNavigationDrawer(drawerState = drawer, drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(290.dp), drawerContainerColor = p.green, drawerContentColor = p.onGreen) {
                Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text("planly", color = p.onGreen, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(30.dp))
                    Surface(shape = CircleShape, color = p.onGreen, modifier = Modifier.size(64.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(data.preferences.name.firstOrNull()?.uppercase() ?: "P", color = p.green, fontSize = 24.sp) }
                    }
                    Spacer(Modifier.height(20.dp)); Text("Welcome back,", color = p.onGreen, fontSize = 13.sp)
                    Text(data.preferences.name.ifBlank { "Your space" }, color = p.onGreen, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(30.dp))
                    destinations.forEach { name ->
                        TextButton(onClick = { detail = null; page = name; scope.launch { drawer.close() } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = p.onGreen)) {
                            Icon(pageIcon(name), null); Spacer(Modifier.width(14.dp)); Text(name, modifier = Modifier.weight(1f), fontSize = 15.sp)
                        }
                    }
                    Spacer(Modifier.height(30.dp)); Text("Small steps. Steady days.", color = p.onGreen, fontSize = 12.sp)
                }
            }
        }) {
            Scaffold(containerColor = p.background, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
                TopAppBar(title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (task == null && page == "Today") { PlanlyMark(); Spacer(Modifier.width(9.dp)) }
                        Text(if (task != null) "Task" else if (page == "Today") "Planly" else page, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.background, navigationIconContentColor = p.text, actionIconContentColor = p.text),
                    navigationIcon = { IconButton(onClick = { if (detail != null) detail = null else scope.launch { drawer.open() } }) {
                        Icon(if (detail != null) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Menu, if (detail != null) "Back" else "Open menu") } },
                    actions = {
                        IconButton(onClick = { detail = null; page = if (page == "Today") "Settings" else "App Health" }, modifier = Modifier.semantics { contentDescription = if (page == "Today") "Open settings" else "Open App Health" }) {
                            if (page == "Today") Box(Modifier.size(34.dp).background(p.peach, CircleShape), contentAlignment = Alignment.Center) {
                                Text(data.preferences.name.firstOrNull()?.uppercase() ?: "P", color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            } else Icon(Icons.Outlined.NotificationsNone, "Notification settings")
                        }
                        IconButton(onClick = { editId = task?.id; if (page == "Today") day = LocalDate.now().toString(); editorOpen = true }) {
                            Icon(if (task != null) Icons.Outlined.Edit else Icons.Outlined.Add, if (task != null) "Edit task" else "Add task") }
                    })
            }, bottomBar = {
                GlassPanel(glass = data.preferences.glass, modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        pages.forEach { name ->
                            val selected = page == name && task == null
                            val tint by animateColorAsState(if (selected) p.green else p.secondaryText, tween(220), label = "Tab color")
                            val fill by animateColorAsState(if (selected) p.mint else Color.Transparent, tween(220), label = "Tab highlight")
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).selectable(selected, role = Role.Tab, onClick = { detail = null; page = name })
                                .heightIn(min = 56.dp).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(34.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(pageIcon(name), null, Modifier.size(22.dp), tint = tint)
                                }
                                Text(name, color = tint, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }) { inset ->
                Box(Modifier.fillMaxSize().padding(inset)) {
                    AnimatedContent(targetState = page to task?.id, modifier = Modifier.fillMaxSize(), transitionSpec = {
                        val direction = if (targetState.second != null) 1 else if (initialState.second != null) -1
                            else if (destinations.indexOf(targetState.first) >= destinations.indexOf(initialState.first)) 1 else -1
                        if (data.preferences.reduceMotion) fadeIn(tween(90)) togetherWith fadeOut(tween(70))
                        else (fadeIn(tween(220)) + slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 18 * direction }) togetherWith
                            (fadeOut(tween(150)) + slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 24 * direction })
                    }, label = "Navigation") { screen ->
                        val shownTask = data.tasks.find { it.id == screen.second }
                        if (shownTask != null) TaskDetail(shownTask, onToggle = { store.toggle(shownTask.id) }, onDelete = { deleteId = shownTask.id },
                            onChecklist = { store.toggleChecklist(shownTask.id, it) }, onFocus = {
                            store.resetFocus(shownTask.title); detail = null; page = "Focus"
                        }) else when (screen.first) {
                            "Today" -> TodayScreen(data, store, onTask = { detail = it }, onAdd = { editId = null; day = LocalDate.now().toString(); editorOpen = true },
                                onCalendar = { date -> day = date.toString(); page = "Calendar" }, onAI = { page = "AI planner" }, onFocus = { page = "Focus" }, onProgress = { page = "Progress" })
                            "Calendar" -> CalendarScreen(data, LocalDate.parse(day), onDay = { day = it.toString() }, onTask = { detail = it }, onAdd = { editId = null; editorOpen = true })
                            "Tasks" -> TasksScreen(data, onTask = { detail = it }, onAdd = { editId = null; editorOpen = true })
                            "Settings" -> SettingsScreen(data.preferences, permissionsVersion,
                                onChange = { store.preferences(it) }, onNotifications = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { notificationPurpose = "Tasks"; askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                    else store.preferences(data.preferences.copy(reminders = enabled))
                                }, onBreakNotifications = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { notificationPurpose = "Breaks"; askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                    else store.preferences(data.preferences.copy(screenBreaks = enabled))
                                }, onHealth = { page = "App Health" }, onMessage = ::message)
                            "Focus" -> FocusScreen(data, store)
                            "AI planner" -> AIScreen(data.preferences, store, onSettings = { page = "Settings" }, onMessage = ::message)
                            "Progress" -> ProgressScreen(data)
                            "App Health" -> AppHealthScreen(data.preferences, permissionsVersion, onNotifications = {
                                notificationPurpose = "Health"
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else scope.launch(kotlinx.coroutines.Dispatchers.IO) { store.reschedule(force = true) }
                            }, onAiSettings = { page = "Settings" }, onMessage = ::message)
                        }
                    }
                }
            }
        }
        if (editorOpen) TaskEditor(data.tasks.find { it.id == editId }, LocalDate.parse(day), onDismiss = { editorOpen = false }, onSave = { saved ->
            store.put(saved); editorOpen = false; message("Task saved.")
        })
        if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete this task?") },
            text = { Text("This removes the task and its reminder.") }, confirmButton = { TextButton(onClick = {
                store.remove(deleteId!!); deleteId = null; detail = null; message("Task deleted.")
            }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep task") } })
    }
}

@Composable private fun GlassPanel(glass: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPlanly.current
    val brush = Brush.linearGradient(if (glass) listOf(p.highlight, p.surface, p.mint) else listOf(p.surface, p.surface))
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = p.surface, border = BorderStroke(1.dp, p.outline), shadowElevation = if (glass) 3.dp else 0.dp) {
        Column(Modifier.background(brush), content = content)
    }
}
@Composable private fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = LocalPlanly.current.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (action != null) TextButton(onClick = onAction) { Text(action, color = LocalPlanly.current.green, fontSize = 12.sp) }
    }
}
@Composable private fun TaskRow(task: Task, onOpen: () -> Unit, onToggle: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val p = LocalPlanly.current
    val fill by animateColorAsState(if (task.done) p.mint else p.surface, tween(240), label = "Task completion")
    Surface(shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, p.outline.copy(alpha = .65f)), color = fill, modifier = modifier.fillMaxWidth().animateContentSize(tween(240))) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onToggle != null) Checkbox(checked = task.done, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = p.green, uncheckedColor = p.secondaryText, checkmarkColor = p.onGreen))
            Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(8.dp)) {
                Text(task.title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None)
                Spacer(Modifier.height(5.dp))
                Text("${task.timeLabel()} · ${task.minutes} min · ${task.project}", color = p.secondaryText, fontSize = 11.sp)
                if (task.checklist.isNotEmpty() || task.repeat != "Off") {
                    val parts = listOfNotNull(if (task.checklist.isNotEmpty()) "${task.checklist.count { it.done }}/${task.checklist.size} checklist" else null,
                        if (task.repeat != "Off") "Repeats ${task.repeat.lowercase()}" else null)
                    Text(parts.joinToString(" · "), color = p.green, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                }
                if (task.done) Text("Completed", color = p.green, fontSize = 11.sp)
            }
            IconButton(onClick = onOpen) { Icon(Icons.Outlined.ChevronRight, "View task", tint = p.secondaryText) }
        }
    }
}
@Composable private fun EmptyTasks(onAdd: () -> Unit) {
    val p = LocalPlanly.current
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Spa, null, tint = p.green, modifier = Modifier.size(38.dp))
        Spacer(Modifier.height(14.dp)); Text("A little room to begin.", color = p.text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Text("Add one small step for your day.", color = p.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onAdd, modifier = Modifier.padding(top = 18.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Add your first task") }
    }
}
@Composable private fun TodayScreen(data: AppData, store: AppStore, onTask: (String) -> Unit, onAdd: () -> Unit,
    onCalendar: (LocalDate) -> Unit, onAI: () -> Unit, onFocus: () -> Unit, onProgress: () -> Unit) {
    val p = LocalPlanly.current
    val today = LocalDate.now()
    val tasks = remember(data.tasks, today) { data.tasks.filter { TaskRules.dayOf(it) == today }.sortedBy { it.dueAt } }
    val done = tasks.count { it.done }
    val progress by animateFloatAsState(if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size, tween(400), label = "Daily progress")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("A calmer day, one step at a time.", color = p.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp))
            val greeting = when (LocalTime.now().hour) { in 0..11 -> "Good morning,"; in 12..17 -> "Good afternoon,"; else -> "Good evening," }
            WelcomeGarden(greeting, data.preferences.name, onAdd)
        }
        item {
            SectionTitle("Your week", "Calendar", { onCalendar(today) })
            val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val tones = listOf(p.mint, p.sky, p.butter, p.peach, p.lavender, p.mint, p.sky)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(7) { i ->
                    val date = start.plusDays(i.toLong()); val selected = date == today
                    Surface(onClick = { onCalendar(date) }, shape = RoundedCornerShape(18.dp), color = tones[i],
                        border = if (selected) BorderStroke(1.5.dp, p.green) else null, modifier = Modifier.width(48.dp)) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date.dayOfMonth.toString(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = p.text)
                            Text(date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, color = p.secondaryText, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("Quick access")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAccessCard("Gentle focus", "One thing at a time", Icons.Outlined.Spa, p.lavender, Modifier.weight(1f), onFocus)
                QuickAccessCard("AI planner", "Big goals, small steps", Icons.Outlined.AutoAwesome, p.mint, Modifier.weight(1f), onAI)
            }
        }
        item {
            Surface(onClick = onProgress, color = p.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, p.outline.copy(alpha = .65f))) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = p.green,
                            trackColor = p.mint, strokeWidth = 4.dp)
                        Icon(Icons.Outlined.Check, null, tint = p.green, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$done of ${tasks.size} steps complete", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${tasks.filterNot { it.done }.sumOf { it.minutes }} min remaining · At your pace", color = p.secondaryText,
                            fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                    Icon(Icons.Outlined.ChevronRight, "View progress", tint = p.green)
                }
            }
        }
        item { SectionTitle("Today's small steps", "Add", onAdd) }
        if (tasks.isEmpty()) item { EmptyTasks(onAdd) }
        items(tasks, key = { it.id }) { t -> TaskRow(t, { onTask(t.id) }, { store.toggle(t.id) }, Modifier.animateItem()) }
    }
}
@Composable private fun CalendarScreen(data: AppData, day: LocalDate, onDay: (LocalDate) -> Unit, onTask: (String) -> Unit, onAdd: () -> Unit) {
    val p = LocalPlanly.current
    val tasks = data.tasks.filter { TaskRules.dayOf(it) == day }.sortedBy { it.dueAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp, 12.dp, 24.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(day.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = p.text, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { onDay(day.minusDays(7)) }) { Icon(Icons.Outlined.ChevronLeft, "Previous week", tint = p.green) }
            IconButton(onClick = { onDay(day.plusDays(7)) }) { Icon(Icons.Outlined.ChevronRight, "Next week", tint = p.green) }
        } }
        item {
            val start = day.minusDays((day.dayOfWeek.value - 1).toLong())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..6).forEach { i -> val date = start.plusDays(i.toLong()); val selected = date == day
                    Surface(color = if (selected) p.green else p.mint, shape = RoundedCornerShape(15.dp), modifier = Modifier.width(48.dp).height(74.dp).clickable { onDay(date) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(date.dayOfMonth.toString(), fontSize = 18.sp, color = if (selected) p.onGreen else p.text, fontWeight = FontWeight.Medium)
                            Text(date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, color = if (selected) p.onGreen else p.secondaryText, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        item { SectionTitle(day.format(dayFormat), "Today", { onDay(LocalDate.now()) }) }
        if (tasks.isEmpty()) item { EmptyTasks(onAdd) }
        items(tasks, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 18.dp).size(9.dp).background(p.green, CircleShape))
                    Box(Modifier.width(1.dp).height(85.dp).background(p.outline))
                }
                Spacer(Modifier.width(10.dp))
                Surface(color = if (t == tasks.first()) p.green else p.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).clickable { onTask(t.id) }) {
                    val foreground = if (t == tasks.first()) p.onGreen else p.text
                    Column(Modifier.padding(18.dp)) {
                        Text(t.timeLabel(), color = foreground, fontSize = 11.sp)
                        Text(t.title, color = foreground, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                        if (t.notes.isNotBlank()) Text(t.notes, color = if (t == tasks.first()) p.onGreen else p.secondaryText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text("${t.project} · ${t.minutes} min${if (t.done) " · Completed" else ""}", color = foreground, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}
@Composable private fun TasksScreen(data: AppData, onTask: (String) -> Unit, onAdd: () -> Unit) {
    val p = LocalPlanly.current
    var filter by rememberSaveable { mutableStateOf("Active") }
    var query by rememberSaveable { mutableStateOf("") }
    var project by rememberSaveable { mutableStateOf("All projects") }
    var priority by rememberSaveable { mutableStateOf("Any priority") }
    var sort by rememberSaveable { mutableStateOf("Due date") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(filter) { now = System.currentTimeMillis(); if (filter == "Overdue") while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val tasks = remember(data.tasks, filter, query, project, priority, sort, now) {
        TaskRules.search(data.tasks, filter, query, project, priority, sort, now)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            OutlinedTextField(query, { query = it.take(100) }, placeholder = { Text("Search tasks, notes or checklist", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Active", "Done", "Overdue", "All").forEachIndexed { i, name ->
                val selected = filter == name
                FilterChip(selected = selected, onClick = { filter = name }, label = { Text(name) },
                    shape = RoundedCornerShape(20.dp), colors = FilterChipDefaults.filterChipColors(
                        containerColor = listOf(p.mint, p.lavender, p.peach, p.sky)[i], labelColor = p.text,
                        selectedContainerColor = p.green, selectedLabelColor = p.onGreen))
            } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TaskFilterMenu("Project", listOf("All projects") + data.tasks.map { it.project }.distinct().sorted(), project, Modifier.weight(1f)) { project = it }
                TaskFilterMenu("Priority", listOf("Any priority", "High", "Normal", "Low"), priority, Modifier.weight(1f)) { priority = it }
                TaskFilterMenu("Sort", listOf("Due date", "Priority", "Shortest"), sort, Modifier.weight(1f)) { sort = it }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))) {
                CozyLandscape(Modifier.matchParentSize())
                Column(Modifier.padding(22.dp)) {
                    Text("Make room for\nwhat matters.", color = p.text, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                    Text("Your goals, at your pace.", color = p.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
                    FilledTonalButton(onClick = onAdd) { Icon(Icons.Outlined.Add, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("New task", fontSize = 12.sp) }
                }
            }
        }
        item { SectionTitle(if (query.isBlank()) "Your ${filter.lowercase()} tasks" else "Search results") }
        if (tasks.isEmpty()) item {
            if (data.tasks.isEmpty()) EmptyTasks(onAdd) else Column(Modifier.padding(vertical = 20.dp)) {
                Text("No tasks match these filters.", color = p.secondaryText, fontSize = 14.sp)
                TextButton(onClick = { query = ""; project = "All projects"; priority = "Any priority"; filter = "All" }) { Text("Clear filters") }
            }
        }
        items(tasks, key = { it.id }) { t -> Column(Modifier.animateItem()) {
            Text(TaskRules.dayOf(t).format(dayFormat), color = p.secondaryText, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            TaskRow(t, { onTask(t.id) })
        } }
    }
}
@Composable private fun TaskDetail(task: Task, onToggle: () -> Unit, onDelete: () -> Unit, onChecklist: (String) -> Unit, onFocus: () -> Unit) {
    val p = LocalPlanly.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Surface(color = p.mint, shape = RoundedCornerShape(10.dp)) { Text(task.project, color = p.green, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
        Text(task.title, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold, color = p.text, modifier = Modifier.padding(vertical = 18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Schedule, null, tint = p.green); Spacer(Modifier.width(8.dp)); Text("${TaskRules.dayOf(task).format(dayFormat)} · ${task.timeLabel()}", color = p.green, fontSize = 13.sp) }
        Spacer(Modifier.height(30.dp)); SectionTitle("Description")
        Text(task.notes.ifBlank { "No notes yet. Tap Edit to add a few details." }, color = p.secondaryText, fontSize = 14.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 12.dp))
        if (task.checklist.isNotEmpty()) {
            Spacer(Modifier.height(24.dp)); SectionTitle("Checklist · ${task.checklist.count { it.done }}/${task.checklist.size}")
            task.checklist.forEach { item ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChecklist(item.id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.done, onCheckedChange = { onChecklist(item.id) })
                    Text(item.title, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f), textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None)
                }
            }
        }
        Spacer(Modifier.height(28.dp)); SectionTitle("Task details")
        DetailLine("Time estimate", "${task.minutes} min"); DetailLine("Priority", task.priority)
        DetailLine("Repeat", task.repeat)
        DetailLine("Reminder", if (task.reminderMinutes < 0) "Off" else if (task.reminderMinutes == 0) "At task time" else "${task.reminderMinutes} min before")
        Spacer(Modifier.height(20.dp))
        Button(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (task.done) "Mark incomplete" else "Mark complete") }
        OutlinedButton(onClick = onFocus, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start focus") }
        TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Delete task", color = MaterialTheme.colorScheme.error) }
    }
}
@Composable private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = LocalPlanly.current.secondaryText, fontSize = 13.sp); Text(value, color = LocalPlanly.current.text, fontSize = 13.sp) }
    HorizontalDivider(color = LocalPlanly.current.outline)
}

@Composable private fun TaskEditor(original: Task?, initialDay: LocalDate, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    val context = LocalContext.current
    val originalTime = original?.let { Instant.ofEpochMilli(it.dueAt).atZone(ZoneId.systemDefault()) }
    val defaultTime = remember { LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0) }
    var title by rememberSaveable(original?.id) { mutableStateOf(original?.title ?: "") }
    var notes by rememberSaveable(original?.id) { mutableStateOf(original?.notes ?: "") }
    var minutes by rememberSaveable(original?.id) { mutableStateOf((original?.minutes ?: 15).toString()) }
    var date by rememberSaveable(original?.id) { mutableStateOf((originalTime?.toLocalDate() ?: if (initialDay == LocalDate.now()) defaultTime.toLocalDate() else initialDay).toString()) }
    var hour by rememberSaveable(original?.id) { mutableIntStateOf(originalTime?.hour ?: defaultTime.hour) }
    var minute by rememberSaveable(original?.id) { mutableIntStateOf(originalTime?.minute ?: 0) }
    var project by rememberSaveable(original?.id) { mutableStateOf(original?.project ?: "Personal") }
    var priority by rememberSaveable(original?.id) { mutableStateOf(original?.priority ?: "Normal") }
    var reminder by rememberSaveable(original?.id) { mutableIntStateOf(original?.reminderMinutes ?: 10) }
    var repeat by rememberSaveable(original?.id) { mutableStateOf(original?.repeat ?: "Off") }
    var checklistText by rememberSaveable(original?.id) { mutableStateOf(original?.checklist?.joinToString("\n") { it.title } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalPlanly.current.surface) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).animateContentSize(tween(220)).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (original == null) "A new small step" else "Edit task", color = LocalPlanly.current.text, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(title, { title = it.take(100) }, label = { Text("Task name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { val d = LocalDate.parse(date); DatePickerDialog(context, { _, y, m, n -> date = LocalDate.of(y, m + 1, n).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show() }, modifier = Modifier.weight(1f)) { Text(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM yyyy")), fontSize = 12.sp) }
                OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, false).show() }, modifier = Modifier.weight(1f)) { Text(LocalTime.of(hour, minute).format(timeFormat), fontSize = 12.sp) }
            }
            Picker("Project", listOf("Personal", "Studies", "Work", "Creative"), project) { project = it }
            Picker("Priority", listOf("Low", "Normal", "High"), priority) { priority = it }
            Picker("Repeat", TaskRules.repeatOptions, repeat) { repeat = it }
            if (repeat != "Off") Text("Completing this task creates its next future occurrence. Change Repeat on the latest active task to stop future repeats.", color = LocalPlanly.current.secondaryText, fontSize = 11.sp, lineHeight = 17.sp)
            Picker("Reminder", listOf("Off", "At task time", "10 min before", "30 min before"), when (reminder) { -1 -> "Off"; 0 -> "At task time"; 30 -> "30 min before"; else -> "10 min before" }) {
                reminder = when (it) { "Off" -> -1; "At task time" -> 0; "30 min before" -> 30; else -> 10 }
            }
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("Estimated minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(checklistText, { checklistText = it.take(2200) }, label = { Text("Checklist (one step per line)") },
                supportingText = { Text("Up to 20 steps, 100 characters each") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Button(onClick = {
                val duration = minutes.toIntOrNull() ?: 0
                error = TaskRules.validate(title, duration)
                val lines = checklistText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (error == null && (lines.size > 20 || lines.any { it.length > 100 })) error = "Use up to 20 checklist steps, each under 100 characters."
                if (error == null) {
                    val due = LocalDate.parse(date).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val base = original ?: Task(title = title.trim(), dueAt = due)
                    val checklist = lines.mapIndexed { i, text ->
                        original?.checklist?.getOrNull(i)?.takeIf { it.title == text } ?: ChecklistItem(title = text)
                    }
                    onSave(base.copy(title = title.trim(), notes = notes.trim(), project = project, dueAt = due, minutes = duration, priority = priority, reminderMinutes = reminder,
                        repeat = repeat, checklist = checklist))
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save task") }
            Text("Enable notifications in Settings to receive reminders.", color = LocalPlanly.current.secondaryText, fontSize = 11.sp)
        }
    }
}
@Composable private fun Picker(label: String, values: List<String>, selected: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = LocalPlanly.current.secondaryText); Text(selected, color = LocalPlanly.current.text); Icon(Icons.Outlined.ExpandMore, null)
    }; DropdownMenu(expanded, onDismissRequest = { expanded = false }) { values.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { onChange(v); expanded = false }) } } }
}

@Composable private fun SettingsScreen(prefs: Preferences, permissionsVersion: Int, onChange: (Preferences) -> Unit, onNotifications: (Boolean) -> Unit,
    onBreakNotifications: (Boolean) -> Unit, onHealth: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val p = LocalPlanly.current
    val secrets = remember { SecretStore(context) }
    var name by rememberSaveable(prefs.name) { mutableStateOf(prefs.name) }
    var endpoint by rememberSaveable(prefs.endpoint) { mutableStateOf(prefs.endpoint) }
    var token by remember { mutableStateOf(secrets.read()) }
    val notifications = remember(permissionsVersion) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exact = remember(permissionsVersion) { Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your look. Your pace. Your choices.", color = p.secondaryText, fontSize = 14.sp)
        OutlinedButton(onClick = onHealth, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.HealthAndSafety, null); Spacer(Modifier.width(8.dp)); Text("Check and fix issues") }
        Spacer(Modifier.height(8.dp)); SectionTitle("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Light", "Dark", "System").forEach { theme -> FilterChip(selected = prefs.theme == theme, onClick = { onChange(prefs.copy(theme = theme)) }, label = { Text(theme) }) } }
        SwitchLine("Glass highlights", "Soft depth on cards and navigation", prefs.glass) { onChange(prefs.copy(glass = it)) }
        SwitchLine("Reduced motion", "Shorter transitions with less movement", prefs.reduceMotion) { onChange(prefs.copy(reduceMotion = it)) }
        OutlinedTextField(name, { name = it.take(30) }, label = { Text("Your name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { onChange(prefs.copy(name = name.trim())); onMessage("Name saved.") }) { Text("Save name") }
        SectionTitle("Reminders")
        SwitchLine("Task notifications", if (notifications) "Reminders for tasks you schedule" else "Android notification permission is off", prefs.reminders) { onNotifications(it) }
        SwitchLine("Hourly screen breaks", "Every hour while your phone is unlocked; respects quiet hours", prefs.screenBreaks) { onBreakNotifications(it) }
        SwitchLine("Quiet hours", "Hold reminders until your quiet hours end", prefs.quietHours) { onChange(prefs.copy(quietHours = it)) }
        if (prefs.quietHours) {
            TimePreference("Quiet hours start", prefs.quietStart) { onChange(prefs.copy(quietStart = it)) }
            TimePreference("Quiet hours end", prefs.quietEnd) { onChange(prefs.copy(quietEnd = it)) }
        }
        Text(if (exact) "Precise reminders are available." else "Standard reminders may arrive late. Allow precise reminders if timing matters.", color = p.secondaryText, fontSize = 12.sp)
        if (!exact && Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
                .onFailure { onMessage("Open Android Settings → Apps → PLANLY → Alarms & reminders.") }
        }) { Text("Allow precise reminders") }
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Android notification settings") }
        SectionTitle("AI assistance")
        SwitchLine("Gemini Flash-Lite", "Review suggestions before adding tasks", prefs.aiEnabled) { onChange(prefs.copy(aiEnabled = it)) }
        Text("Only the goal you enter is sent to your configured AI server. Tasks remain on this device.", color = p.secondaryText, fontSize = 12.sp, lineHeight = 20.sp)
        OutlinedTextField(endpoint, { endpoint = it.take(500) }, label = { Text("AI server URL (HTTPS)") }, placeholder = { Text("https://your-server.example") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it.take(512) }, label = { Text("Server access token") }, supportingText = { Text("32 to 512 characters. Must match your server access token.") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val tokenProblem = if (token.isNotEmpty() || prefs.aiEnabled) AiRules.validateToken(token) else null
            if (endpoint.isNotBlank() && !AiRules.validEndpoint(endpoint)) onMessage("Enter only the HTTPS server address, without /health, /api/plan, credentials or query parameters.")
            else if (tokenProblem != null) onMessage(tokenProblem)
            else runCatching { secrets.save(token); onChange(prefs.copy(endpoint = endpoint.trim().trimEnd('/'))); onMessage("AI connection saved.") }.onFailure { onMessage("Could not save credentials. Please try again.") }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save AI connection") }
        Text("PLANLY 0.4.0 · Offline tasks · No account required", color = p.secondaryText, fontSize = 11.sp, modifier = Modifier.padding(vertical = 16.dp))
    }
}
@Composable private fun SwitchLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = LocalPlanly.current.text, fontSize = 14.sp, fontWeight = FontWeight.Medium); Text(subtitle, color = LocalPlanly.current.secondaryText, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.width(12.dp)); Switch(checked, onCheckedChange = null)
    }
}
@Composable private fun TimePreference(label: String, minute: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> onChange(h * 60 + m) }, minute / 60, minute % 60, false).show() }, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f)); Text(LocalTime.of(minute / 60, minute % 60).format(timeFormat))
    }
}
@Composable private fun FocusScreen(data: AppData, store: AppStore) {
    val p = LocalPlanly.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(data.focusEnd, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (data.focusEnd > 0) while (true) {
                now = System.currentTimeMillis()
                if (now >= data.focusEnd) break
                delay(1000)
            }
        }
    }
    val seconds = if (data.focusEnd > 0) ((data.focusEnd - now) / 1000).coerceAtLeast(0) else data.focusRemaining
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("One thing at a time.", color = p.green, fontSize = 13.sp)
        Text("A little focus.", color = p.text, fontSize = 29.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
        Text(data.focusTask.ifBlank { "Your next small step" }, color = p.secondaryText, fontSize = 14.sp, modifier = Modifier.padding(vertical = 18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45).forEach { minutes -> FilterChip(selected = data.focusDuration == minutes * 60L, enabled = data.focusEnd == 0L,
                onClick = { store.focusPreset(minutes) }, label = { Text("$minutes min") }) }
        }
        GlassPanel(data.preferences.glass, Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60), color = p.text, fontSize = 52.sp, fontWeight = FontWeight.Medium)
                Text("Focus session", color = p.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = { if (seconds <= 0) store.resetFocus(); store.focus() }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text(if (data.focusEnd > 0) "Pause session" else "Start session") }
                TextButton(onClick = { store.resetFocus() }) { Text("Reset timer") }
            }
        }
    }
}
@Composable private fun AIScreen(prefs: Preferences, store: AppStore, onSettings: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val p = LocalPlanly.current
    val scope = rememberCoroutineScope()
    var goal by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf<List<AiStep>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("GEMINI FLASH-LITE", color = p.green, fontSize = 11.sp, letterSpacing = 1.sp)
        Text("Big idea.\nSmall steps.", color = p.text, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
        Text("Describe a goal and review a manageable plan.", color = p.secondaryText, fontSize = 14.sp, lineHeight = 22.sp)
        if (!prefs.aiEnabled || prefs.endpoint.isBlank()) {
            GlassPanel(prefs.glass, Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
                Text("Connect your AI planner", color = p.text, fontWeight = FontWeight.Medium)
                Text("Add your secure AI server connection in Settings. Your tasks work offline while AI is off.", color = p.secondaryText, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
                Button(onClick = onSettings, modifier = Modifier.padding(top = 16.dp)) { Text("Open AI settings") }
            } }
        } else {
            OutlinedTextField(goal, { goal = it.take(1500) }, label = { Text("What would you like to achieve?") }, placeholder = { Text("Prepare my ICT presentation") }, minLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !loading)
            Button(enabled = !loading && goal.trim().length >= 3, onClick = {
                loading = true; error = null; steps = emptyList(); selected = emptySet()
                scope.launch { try {
                    steps = AiClient(context).generate(goal); selected = steps.indices.toSet()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    error = e.message ?: "Could not create a plan. Please try again."
                    AppDiagnostics.record(context, "AI_REQUEST_FAILED", "An AI request failed. Review AI setup and test the connection in App Health.")
                }
                finally { loading = false } }
            }, modifier = Modifier.fillMaxWidth()) { if (loading) { CircularProgressIndicator(Modifier.size(18.dp), color = p.onGreen, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }; Text(if (loading) "Planning your next steps…" else "Create task plan") }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            steps.forEachIndexed { i, step ->
                Surface(color = p.mint, shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(i in selected, onCheckedChange = { selected = if (it) selected + i else selected - i })
                    Column(Modifier.weight(1f)) { Text(step.title, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium); Text("${step.minutes} min · ${step.notes}", color = p.secondaryText, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 5.dp)) }
                } }
            }
            if (steps.isNotEmpty()) Button(enabled = selected.isNotEmpty(), onClick = {
                var start = LocalDateTime.now().plusMinutes(15)
                selected.sorted().forEach { index -> val s = steps[index]
                    store.put(Task(title = s.title, notes = s.notes, dueAt = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), minutes = s.minutes, reminderMinutes = -1))
                    start = start.plusMinutes(s.minutes.toLong() + 5)
                }
                onMessage("Added ${selected.size} tasks. Open them to adjust dates and reminders."); steps = emptyList(); selected = emptySet()
            }, modifier = Modifier.fillMaxWidth()) { Text("Add selected steps") }
            Text("AI suggestions can need changes. Only tasks you approve are added.", color = p.secondaryText, fontSize = 11.sp, lineHeight = 18.sp)
        }
    }
}
