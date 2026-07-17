package com.ddupg.runtrip.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeRoute(
    repository: RaceRepository,
    onAddRace: () -> Unit,
    onOpenRace: (String) -> Unit,
) {
    val factory = remember(repository) { HomeViewModel.Factory(repository) }
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var statusRaceId by rememberSaveable { mutableStateOf<String?>(null) }
    val statusRace = uiState.monthGroups
        .asSequence()
        .flatMap { it.races.asSequence() }
        .firstOrNull { it.id == statusRaceId }

    HomeScreen(
        uiState = uiState,
        onSelectSection = viewModel::selectSection,
        onSelectStatus = viewModel::selectStatus,
        onAddRace = onAddRace,
        onOpenRace = onOpenRace,
        onQuickStatus = { statusRaceId = it },
    )

    if (statusRace != null) {
        QuickStatusSheet(
            race = statusRace,
            onDismiss = { statusRaceId = null },
            onSelectStatus = { status ->
                statusRaceId = null
                viewModel.updateStatus(statusRace.id, status)
            },
        )
    }
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSelectSection: (RaceSection) -> Unit,
    onSelectStatus: (RaceStatus?) -> Unit,
    onAddRace: () -> Unit,
    onOpenRace: (String) -> Unit,
    onQuickStatus: (String) -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRace,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "添加比赛")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            HomeHeader(
                section = uiState.section,
                onSelectSection = onSelectSection,
            )
            StatusFilters(
                selectedStatus = uiState.selectedStatus,
                onSelectStatus = onSelectStatus,
            )

            if (uiState.monthGroups.isEmpty()) {
                HomeEmptyState(
                    modifier = Modifier.weight(1f),
                    section = uiState.section,
                    hasStatusFilter = uiState.selectedStatus != null,
                    onAddRace = onAddRace,
                )
            } else {
                RaceTimeline(
                    modifier = Modifier.weight(1f),
                    monthGroups = uiState.monthGroups,
                    onOpenRace = onOpenRace,
                    onQuickStatus = onQuickStatus,
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    section: RaceSection,
    onSelectSection: (RaceSection) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp),
    ) {
        Text(
            text = "RunTrip",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
        )
        Text(
            text = if (section == RaceSection.UPCOMING) "下一场，从这里出发" else "走过的路，都算数",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RaceSection.entries.forEach { item ->
                val selected = item == section
                Surface(
                    onClick = { onSelectSection(item) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFilters(
    selectedStatus: RaceStatus?,
    onSelectStatus: (RaceStatus?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedStatus == null,
                onClick = { onSelectStatus(null) },
                label = { Text("全部") },
            )
        }
        items(RaceStatus.entries, key = RaceStatus::code) { status ->
            FilterChip(
                selected = selectedStatus == status,
                onClick = { onSelectStatus(status) },
                label = { Text(status.displayName) },
            )
        }
    }
}

@Composable
private fun RaceTimeline(
    monthGroups: List<RaceMonthGroup>,
    onOpenRace: (String) -> Unit,
    onQuickStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        monthGroups.forEach { group ->
            item(key = "month-${group.month}") {
                MonthHeader(group.month)
            }
            items(group.races, key = Race::id) { race ->
                RaceTimelineRow(
                    race = race,
                    onClick = { onOpenRace(race.id) },
                    onQuickStatus = { onQuickStatus(race.id) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = month.monthValue.toString().padStart(2, '0'),
            fontFamily = FontFamily.Monospace,
            fontSize = 28.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "月",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = month.year.toString(),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RaceTimelineRow(
    race: Race,
    onClick: () -> Unit,
    onQuickStatus: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(64.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = race.raceDate.dayOfMonth.toString().padStart(2, '0'),
                fontFamily = FontFamily.Monospace,
                fontSize = 34.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = race.raceDate.dayOfWeek.chineseShortName(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = race.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${race.city} · ${race.category.compactDisplayName()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusBadge(
                    status = race.status,
                    onClick = onQuickStatus,
                )
                race.travelDistanceKm?.let { distance ->
                    CompactMetadata(
                        icon = { Icon(Icons.Outlined.Route, contentDescription = null) },
                        text = distance.formatDistance(),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            CompactMetadata(
                icon = { Icon(Icons.Outlined.Hotel, contentDescription = null) },
                text = race.hotelBookingStatus.displayName,
            )
        }
    }
}

@Composable
private fun StatusBadge(
    status: RaceStatus,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            text = status.displayName,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CompactMetadata(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeEmptyState(
    section: RaceSection,
    hasStatusFilter: Boolean,
    onAddRace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = when {
                    hasStatusFilter -> "没有符合筛选的比赛"
                    section == RaceSection.HISTORY -> "还没有历史比赛"
                    else -> "还没有比赛安排"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (hasStatusFilter) {
                    "换一个参赛状态看看。"
                } else {
                    "把报名、酒店和路程放在一起。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasStatusFilter && section == RaceSection.UPCOMING) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAddRace) {
                    Text("添加第一场比赛")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickStatusSheet(
    race: Race,
    onDismiss: () -> Unit,
    onSelectStatus: (RaceStatus) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                    text = "${race.name} · ${race.city}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RaceStatus.entries.forEach { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectStatus(status) }
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status.displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (status == race.status) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (status == race.status) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "当前状态",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun RaceCategory.compactDisplayName(): String = when (this) {
    RaceCategory.MARATHON -> "全马"
    RaceCategory.HALF_MARATHON -> "半马"
    RaceCategory.TEN_K -> "10 公里"
    RaceCategory.OTHER -> "其他"
}

private fun DayOfWeek.chineseShortName(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

private fun Double.formatDistance(): String =
    if (this % 1.0 == 0.0) "${toLong()} km" else "$this km"

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RunTripTheme {
        HomeScreen(
            uiState = HomeUiState(
                monthGroups = listOf(
                    RaceMonthGroup(
                        month = YearMonth.of(2026, 11),
                        races = listOf(previewRace()),
                    ),
                ),
            ),
            onSelectSection = {},
            onSelectStatus = {},
            onAddRace = {},
            onOpenRace = {},
            onQuickStatus = {},
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
    hotelNotes = null,
    raceNotes = null,
    createdAtEpochMillis = 0,
    updatedAtEpochMillis = 0,
    recordVersion = 1,
)
