package com.ddupg.runtrip.feature.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ddupg.runtrip.RunTripApplication
import com.ddupg.runtrip.data.model.RaceLocation
import com.ddupg.runtrip.data.region.RegionCatalog
import com.ddupg.runtrip.data.region.RegionLevel
import com.ddupg.runtrip.ui.components.runTripConfirmButtonColors
import com.ddupg.runtrip.ui.components.runTripTextButtonColors
import com.ddupg.runtrip.ui.presentation.RaceLabelDensity
import com.ddupg.runtrip.ui.presentation.RacePresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RaceLocationField(draft: RaceDraft, error: String?, enabled: Boolean, onDraftChange: (RaceDraft) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button) { showPicker = true },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("比赛地点", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (draft.location == null && draft.city.isBlank()) "请选择省、市或区县"
                        else RacePresentation.location(draft.location, draft.city, RaceLabelDensity.FULL).text,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Icon(Icons.Outlined.Place, contentDescription = null)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
        if (draft.location == null && draft.city.isNotBlank()) {
            Text("原有地点，可保留或重新选择", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (showPicker) {
        val application = LocalContext.current.applicationContext as RunTripApplication
        val catalog by produceState<RegionCatalog?>(null, application) {
            value = withContext(Dispatchers.IO) { application.regionCatalog }
        }
        val loaded = catalog
        if (loaded == null) {
            AlertDialog(
                onDismissRequest = { showPicker = false },
                title = { Text("选择比赛地点") },
                text = { CircularProgressIndicator(Modifier.size(24.dp)) },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
            )
        } else {
            RegionPickerDialog(
                catalog = loaded,
                current = draft.location,
                onDismiss = { showPicker = false },
                onSelect = {
                    onDraftChange(draft.withLocation(it))
                    showPicker = false
                },
            )
        }
    }
}

@Composable
internal fun RegionPickerDialog(
    catalog: RegionCatalog,
    current: RaceLocation?,
    onDismiss: () -> Unit,
    onSelect: (RaceLocation) -> Unit,
) {
    val initialPath = remember(catalog, current) { current?.let(catalog::pathFor).orEmpty() }
    var pathCodes by rememberSaveable { mutableStateOf(initialPath.map { it.code }) }
    val path = remember(catalog, pathCodes) {
        var children = catalog.provinces
        buildList {
            for (code in pathCodes) {
                val node = children.find { it.code == code } ?: break
                add(node)
                children = node.children
            }
        }
    }
    val options = path.lastOrNull()?.children ?: catalog.provinces
    val selected = catalog.selection(path)
    val listState = rememberLazyListState()
    LaunchedEffect(pathCodes) { listState.scrollToItem(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择比赛地点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pathCodes = emptyList() }) { Text("省份") }
                    path.forEachIndexed { index, node ->
                        Text("/")
                        TextButton(onClick = { pathCodes = pathCodes.take(index + 1) }) { Text(node.name) }
                    }
                }
                if (current != null && initialPath.isEmpty()) {
                    Text("原有地点不在当前地区列表中，请重新选择", style = MaterialTheme.typography.bodySmall)
                }
                if (selected != null) {
                    Text("已选：${RacePresentation.location(selected, "", RaceLabelDensity.FULL).text}")
                    if (options.isNotEmpty()) Text("可直接确定，或继续选择区县", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(if (path.isEmpty()) "请选择省份" else "请选择市或区县")
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), state = listState) {
                    items(options, key = { it.code }) { node ->
                        val unavailable = catalog.isUnavailableProvince(node)
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !unavailable, role = Role.Button) {
                                pathCodes = pathCodes + node.code
                            }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                node.name,
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (unavailable) 0.38f else 1f),
                            )
                            if (unavailable) {
                                Text("暂无城市数据", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (node.children.isNotEmpty()) Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        }
                    }
                    if (options.isEmpty() && selected != null) {
                        item { Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart) {
                            Text(if (path.last().level == RegionLevel.DISTRICT) "已选到区县" else "该城市没有区县可选")
                        } }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { selected?.let(onSelect) }, enabled = selected != null, colors = runTripConfirmButtonColors()) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = runTripTextButtonColors()) { Text("取消") }
        },
    )
}
