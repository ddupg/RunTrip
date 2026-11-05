package com.ddupg.runtrip.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.SportType
import com.ddupg.runtrip.ui.components.RaceStatusIcon
import com.ddupg.runtrip.ui.components.RunTripFilterChip
import com.ddupg.runtrip.ui.presentation.RaceLabelDensity
import com.ddupg.runtrip.ui.presentation.RacePresentation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RaceFilterSheet(
    appliedFilter: RaceFilter,
    onDismiss: () -> Unit,
    onConfirm: (RaceFilter) -> Unit,
) {
    var draftFilter by remember(appliedFilter) { mutableStateOf(appliedFilter) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "取消筛选")
                }
                Text(
                    text = "筛选比赛",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = { draftFilter = RaceFilter() },
                    enabled = draftFilter.isActive,
                    colors = raceFilterResetButtonColors(MaterialTheme.colorScheme),
                ) {
                    Text("重置")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                FilterOptionGroup(
                    title = "参赛状态",
                    options = RaceStatus.entries,
                    selectedOptions = draftFilter.statuses,
                    optionLabel = { RacePresentation.status(it).text },
                    onToggle = { status ->
                        draftFilter = draftFilter.copy(
                            statuses = draftFilter.statuses.toggled(status),
                        )
                    },
                )
                Spacer(Modifier.height(20.dp))
                SportType.entries.forEach { sport ->
                    val options = RacePresentation.categories(sport)
                    val allSelected = draftFilter.categories.containsAll(options)
                    if (options.size > 1) {
                        TextButton(onClick = {
                            draftFilter = draftFilter.copy(
                                categories = if (allSelected) draftFilter.categories - options.toSet()
                                    else draftFilter.categories + options,
                            )
                        }) {
                            Text("${RacePresentation.sportType(sport).text} · ${if (allSelected) "取消全选" else "全选"}")
                        }
                    }
                    FilterOptionGroup(
                        title = RacePresentation.sportType(sport).text,
                        options = options,
                        selectedOptions = draftFilter.categories,
                        optionLabel = {
                            if (options.size == 1) "全部${RacePresentation.sportType(sport).text}"
                            else RacePresentation.category(it, RaceLabelDensity.COMPACT).text
                        },
                        onToggle = { category ->
                            draftFilter = draftFilter.copy(categories = draftFilter.categories.toggled(category))
                        },
                    )
                    Spacer(Modifier.height(20.dp))
                }

            }

            Button(
                onClick = { onConfirm(draftFilter) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 24.dp, end = 20.dp),
            ) {
                Text("确定")
            }
        }
    }
}

internal data class RaceFilterResetColors(
    val enabledContentColor: Color,
    val disabledContentColor: Color,
)

internal fun ColorScheme.raceFilterResetColors(): RaceFilterResetColors =
    RaceFilterResetColors(
        enabledContentColor = secondary,
        disabledContentColor = onSurfaceVariant,
    )

@Composable
private fun raceFilterResetButtonColors(colorScheme: ColorScheme) =
    ButtonDefaults.textButtonColors(
        contentColor = colorScheme.raceFilterResetColors().enabledContentColor,
        disabledContentColor = colorScheme.raceFilterResetColors().disabledContentColor,
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterOptionGroup(
    title: String,
    options: List<T>,
    selectedOptions: Set<T>,
    optionLabel: (T) -> String,
    onToggle: (T) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            RunTripFilterChip(
                selected = option in selectedOptions,
                onClick = { onToggle(option) },
                label = optionLabel(option),
            )
        }
    }
}

private fun <T> Set<T>.toggled(value: T): Set<T> =
    if (value in this) this - value else this + value

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickStatusSheet(
    race: Race,
    update: QuickStatusUpdate,
    onDismiss: () -> Unit,
    onSelectStatus: (RaceStatus) -> Unit,
) {
    val isSaving = update is QuickStatusUpdate.Saving
    val saving by rememberUpdatedState(isSaving)
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { it != SheetValue.Hidden || !saving },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = "更新参赛状态",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${race.name} · ${RacePresentation.location(race).text}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (update is QuickStatusUpdate.Failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = update.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            RaceStatus.entries.forEach { status ->
                val selected = status == race.status
                Surface(
                    onClick = { onSelectStatus(status) },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RaceStatusIcon(
                            status = status,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = RacePresentation.status(status).text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        when {
                            update is QuickStatusUpdate.Saving &&
                                update.targetStatus == status -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }

                            selected -> {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "当前状态",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
