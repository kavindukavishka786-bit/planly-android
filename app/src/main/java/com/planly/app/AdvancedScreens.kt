package com.planly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun TaskFilterMenu(label: String, values: List<String>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label: $selected" },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
            Text(if (selected == "All projects" || selected == "Any priority") label else selected, fontSize = 11.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore, null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

@Composable fun ProgressScreen(data: AppData) {
    val p = LocalPlanly.current
    val today = LocalDate.now()
    val week = remember(data.tasks, today) {
        (6 downTo 0).map { days -> val date = today.minusDays(days.toLong())
            date to data.tasks.count { task -> task.done && task.completedAt > 0 &&
                Instant.ofEpochMilli(task.completedAt).atZone(ZoneId.systemDefault()).toLocalDate() == date }
        }
    }
    val max = week.maxOf { it.second }.coerceAtLeast(1)
    val completed = data.tasks.count { it.done }
    val now = System.currentTimeMillis()
    val overdue = data.tasks.count { !it.done && it.dueAt < now }
    val focusMinutes = remember(data.sessions, today) { data.sessions.filter {
        Instant.ofEpochMilli(it.finishedAt).atZone(ZoneId.systemDefault()).toLocalDate() >= today.minusDays(6)
    }.sumOf { it.minutes } }
    val projects = remember(data.tasks) { data.tasks.groupBy { it.project }.toSortedMap() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("Small steps add up.", color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Your progress, without the pressure.", color = p.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Completed", completed.toString(), p.mint, Modifier.weight(1f))
                StatCard("Overdue", overdue.toString(), p.peach, Modifier.weight(1f))
                StatCard("Focus min · 7 days", focusMinutes.toString(), p.lavender, Modifier.weight(1f))
            }
        }
        item {
            Surface(color = p.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Completed in the last 7 days", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        week.forEach { (date, count) ->
                            Column(Modifier.weight(1f).semantics { contentDescription = "$date: $count completed tasks" }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(count.toString(), fontSize = 12.sp, color = p.secondaryText)
                                Box(Modifier.height(94.dp).fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.width(22.dp).height((6 + 80 * count.toFloat() / max).dp)
                                        .background(if (date == today) p.green else p.mint, RoundedCornerShape(8.dp)))
                                }
                                Text(date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, color = p.secondaryText, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    Text("Tracks completions recorded from this update onward.", color = p.secondaryText, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp))
                }
            }
        }
        item { Text("Project progress", color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
        if (projects.isEmpty()) item { Text("Add a task to see your project progress here.", color = p.secondaryText, fontSize = 13.sp) }
        items(projects.entries.toList(), key = { it.key }) { (name, tasks) ->
            val done = tasks.count { it.done }
            Surface(color = p.surface, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("$done / ${tasks.size}", color = p.secondaryText, fontSize = 12.sp)
                    }
                    LinearProgressIndicator(progress = { done.toFloat() / tasks.size }, color = p.green, trackColor = p.mint,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
        }
        item { Text("Recent focus sessions", color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
        if (data.sessions.isEmpty()) item { Text("Finish a focus session to start your history.", color = p.secondaryText, fontSize = 13.sp) }
        items(data.sessions.takeLast(5).reversed(), key = { it.id }) { session ->
            Surface(color = p.lavender, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(session.task.ifBlank { "Gentle focus" }, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("${session.minutes} min · ${Instant.ofEpochMilli(session.finishedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))}",
                        color = p.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable private fun StatCard(title: String, value: String, color: Color, modifier: Modifier) {
    val p = LocalPlanly.current
    Surface(color = color, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(Modifier.heightIn(min = 94.dp).padding(13.dp)) {
            Text(value, color = p.text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(title, color = p.secondaryText, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 7.dp))
        }
    }
}
