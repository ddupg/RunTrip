package com.ddupg.runtrip.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddupg.runtrip.data.model.TrainingActivity
import com.ddupg.runtrip.data.model.TrainingSport
import com.ddupg.runtrip.data.repository.TrainingRepository
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TrainingRoute(repository: TrainingRepository, onConnect: () -> Unit) {
    val model: TrainingViewModel = viewModel(factory = TrainingViewModel.Factory(repository))
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                zone = ZoneId.systemDefault()
                model.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    TrainingScreen(state, model::selectFilter, model::update, onConnect, zone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    state: TrainingUiState,
    onFilter: (TrainingSport?) -> Unit,
    onUpdate: () -> Unit,
    onConnect: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val days = remember(state.records, state.filter, zone) { trainingDays(state.records, state.filter, zone) }
    Scaffold(topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            title = { Text("训练", style = MaterialTheme.typography.titleLarge) },
            actions = {
                if (state.connected) TextButton(onClick = onUpdate, enabled = !state.updating) { Text("更新") }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.connected) {
                TrainingFilters(state.filter, onFilter)
                state.progress?.let { progress ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("正在获取 ${progress.start} 至 ${progress.end} 的记录…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.error?.let { error ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    if (!state.connected) TextButton(onClick = onUpdate) { Text("重试") }
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                !state.connected -> Column(
                    Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("连接数据来源", style = MaterialTheme.typography.titleMedium)
                    Text("在「我」中连接后查看训练记录", modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onConnect, modifier = Modifier.padding(top = 12.dp)) { Text("前往「我」连接") }
                }
                days.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (state.updating) "正在获取训练记录…" else "暂无训练记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    days.forEach { day ->
                        item(key = "date-${day.date}") {
                            Text(trainingDateLabel(day.date, LocalDate.now(zone)),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                        }
                        items(day.activities, key = { it.id }) { activity -> TrainingRow(activity, zone) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingFilters(selected: TrainingSport?, onFilter: (TrainingSport?) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(null to "全部", TrainingSport.RUNNING to "跑步", TrainingSport.CYCLING to "骑行", TrainingSport.SWIMMING to "游泳")
            .forEach { (sport, label) ->
                val active = selected == sport
                Column(Modifier.widthIn(min = 48.dp).selectable(active, role = Role.Tab, onClick = { onFilter(sport) }),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.bodyLarge,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(width = 24.dp, height = 2.dp)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background))
                }
            }
    }
}

@Composable
private fun TrainingRow(activity: TrainingActivity, zone: ZoneId) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(when (activity.subtype.sport) {
                TrainingSport.RUNNING -> Icons.AutoMirrored.Outlined.DirectionsRun
                TrainingSport.CYCLING -> Icons.AutoMirrored.Outlined.DirectionsBike
                TrainingSport.SWIMMING -> Icons.Outlined.Pool
            }, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(trainingSubtypeLabel(activity.subtype), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(trainingTime(activity, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(trainingDistance(activity), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("时长 ${trainingDuration(activity.durationSeconds)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
