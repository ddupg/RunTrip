package com.ddupg.runtrip.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.repository.RaceRepository
import com.ddupg.runtrip.ui.theme.RunTripTheme
import java.time.LocalDate

@Composable
fun RaceDetailRoute(
    repository: RaceRepository,
    raceId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    val factory = remember(repository, raceId) {
        RaceDetailViewModel.Factory(repository, raceId)
    }
    val viewModel: RaceDetailViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.deletedEvents.collect { onDeleted() }
    }

    RaceDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onEdit = onEdit,
        onDelete = viewModel::deleteRace,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaceDetailScreen(
    uiState: RaceDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("比赛详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onEdit,
                        enabled = uiState.race != null,
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            enabled = uiState.race != null,
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "删除比赛",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteConfirmation = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.race == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("没有找到这条比赛记录")
                }
            }

            else -> {
                RaceDetailContent(
                    race = uiState.race,
                    deleteError = uiState.deleteError,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }

    val race = uiState.race
    if (showDeleteConfirmation && race != null) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeleting) showDeleteConfirmation = false
            },
            title = { Text("删除“${race.name}”？") },
            text = { Text("删除后无法恢复，这条比赛及酒店信息都会被永久移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    enabled = !uiState.isDeleting,
                ) {
                    Text(
                        text = "永久删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !uiState.isDeleting,
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RaceDetailContent(
    race: Race,
    deleteError: String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { RaceHero(race) }
        item {
            DetailSection(title = "行程") {
                DetailRow(
                    icon = Icons.Outlined.Route,
                    label = "路程距离",
                    value = formatDistance(race.travelDistanceKm),
                )
            }
        }
        item {
            DetailSection(title = "住宿") {
                DetailRow(
                    icon = Icons.Outlined.Hotel,
                    label = "预订状态",
                    value = race.hotelBookingStatus.displayName,
                )
                DetailRow(
                    icon = Icons.Outlined.Hotel,
                    label = "酒店名称",
                    value = race.hotelName.orNotFilled(),
                )
                DetailRow(
                    icon = Icons.Outlined.LocationOn,
                    label = "预订平台",
                    value = race.bookingPlatform.orNotFilled(),
                )
                DetailRow(
                    icon = Icons.Outlined.Hotel,
                    label = "酒店总价",
                    value = formatCny(race.hotelTotalPriceCents),
                )
                DetailRow(
                    icon = Icons.Outlined.Notes,
                    label = "酒店备注",
                    value = race.hotelNotes.orNotFilled(),
                    multiline = true,
                )
            }
        }
        item {
            DetailSection(title = "比赛备注") {
                DetailRow(
                    icon = Icons.Outlined.Notes,
                    label = "备注",
                    value = race.raceNotes.orNotFilled(),
                    multiline = true,
                )
            }
        }
        deleteError?.let { error ->
            item {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RaceHero(race: Race) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = race.raceDate.dayOfMonth.toString().padStart(2, '0'),
            fontFamily = FontFamily.Monospace,
            fontSize = 64.sp,
            lineHeight = 64.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = formatRaceDate(race.raceDate),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = race.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${race.city} · ${race.category.displayName}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text(
                text = race.status.displayName,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    multiline: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            modifier = Modifier.width(76.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (value == "未填写") FontWeight.Normal else FontWeight.Medium,
            color = if (value == "未填写") {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private fun String?.orNotFilled(): String = this?.takeIf { it.isNotBlank() } ?: "未填写"

@Preview(showBackground = true)
@Composable
private fun RaceDetailScreenPreview() {
    RunTripTheme {
        RaceDetailScreen(
            uiState = RaceDetailUiState(
                race = previewRace(),
                isLoading = false,
            ),
            onBack = {},
            onEdit = {},
            onDelete = {},
        )
    }
}

private fun previewRace(): Race = Race(
    id = "preview",
    name = "横店马拉松",
    city = "金华",
    raceDate = LocalDate.of(2026, 11, 15),
    category = RaceCategory.MARATHON,
    status = RaceStatus.DRAW_WON,
    travelDistanceKm = 350.0,
    hotelBookingStatus = HotelBookingStatus.BOOKED,
    hotelName = "万豪万枫",
    bookingPlatform = "携程",
    hotelTotalPriceCents = 35_000,
    hotelNotes = "靠近起点，比赛日前台可寄存行李。",
    raceNotes = "赛前一天领取物资。",
    createdAtEpochMillis = 0,
    updatedAtEpochMillis = 0,
    recordVersion = 1,
)
