package com.ddupg.runtrip.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.HotelBookingStatus
import com.ddupg.runtrip.data.model.Race
import com.ddupg.runtrip.data.model.RaceCategory
import com.ddupg.runtrip.data.model.RaceStatus
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.data.repository.RaceRepository
import com.ddupg.runtrip.ui.components.RunTripFilterChip
import com.ddupg.runtrip.ui.components.RaceStatusIcon
import com.ddupg.runtrip.ui.components.RunTripRaceStatusBadge
import com.ddupg.runtrip.ui.presentation.RaceLabelDensity
import com.ddupg.runtrip.ui.presentation.RacePresentation
import com.ddupg.runtrip.ui.theme.RunTripTheme
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
    var showFilterSheet by remember { mutableStateOf(false) }

    HomeScreen(
        uiState = uiState,
        onSelectSection = viewModel::selectSection,
        onOpenFilters = { showFilterSheet = true },
        onAddRace = onAddRace,
        onOpenRace = onOpenRace,
        onQuickStatus = viewModel::openQuickStatus,
    )

    if (showFilterSheet) {
        RaceFilterSheet(
            appliedFilter = uiState.filter,
            onDismiss = { showFilterSheet = false },
            onConfirm = { filter ->
                viewModel.applyFilter(filter)
                showFilterSheet = false
            },
        )
    }

    val quickStatusRace = uiState.quickStatusRace
    if (quickStatusRace != null) {
        QuickStatusSheet(
            race = quickStatusRace,
            update = uiState.quickStatusUpdate,
            onDismiss = viewModel::dismissQuickStatus,
            onSelectStatus = viewModel::updateQuickStatus,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSelectSection: (RaceSection) -> Unit,
    onOpenFilters: () -> Unit,
    onAddRace: () -> Unit,
    onOpenRace: (String) -> Unit,
    onQuickStatus: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    HomeTitle(
                        section = uiState.section,
                        collapsedFraction = scrollBehavior.state.collapsedFraction,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
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
        if (uiState.monthGroups.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                RaceSectionTabs(
                    section = uiState.section,
                    onSelectSection = onSelectSection,
                )
                RaceFilterToolbar(
                    resultCount = 0,
                    filter = uiState.filter,
                    onOpenFilters = onOpenFilters,
                )
                HomeEmptyState(
                    modifier = Modifier.weight(1f),
                    section = uiState.section,
                    isFilteredEmpty = uiState.filter.isActive && uiState.sectionRaceCount > 0,
                )
            }
        } else {
            RaceTimeline(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                section = uiState.section,
                filter = uiState.filter,
                monthGroups = uiState.monthGroups,
                onSelectSection = onSelectSection,
                onOpenFilters = onOpenFilters,
                onOpenRace = onOpenRace,
                onQuickStatus = onQuickStatus,
            )
        }
    }
}

@Composable
private fun HomeTitle(
    section: RaceSection,
    collapsedFraction: Float,
) {
    val collapsed = isHomeTitleCollapsed(collapsedFraction)
    Column {
        Text(
            text = "RunTrip",
            style = if (collapsed) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineLarge
            },
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
        )
        if (!collapsed) {
            Text(
                text = if (section == RaceSection.UPCOMING) "下一场，从这里出发" else "走过的路，都算数",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun isHomeTitleCollapsed(collapsedFraction: Float): Boolean = collapsedFraction >= 0.5f

@Composable
private fun RaceSectionTabs(
    section: RaceSection,
    onSelectSection: (RaceSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 12.dp, end = 20.dp),
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

@Composable
private fun RaceFilterToolbar(
    resultCount: Int,
    filter: RaceFilter,
    onOpenFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (filter.isActive) {
                "筛选结果 · $resultCount 场"
            } else {
                "全部比赛 · $resultCount 场"
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            onClick = onOpenFilters,
            shape = RoundedCornerShape(12.dp),
            color = if (filter.isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (filter.isActive) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (filter.isActive) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("筛选", style = MaterialTheme.typography.labelLarge)
                if (filter.isActive) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ) {
                        Text(
                            text = filter.activeDimensionCount.toString(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RaceFilterSheet(
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
                FilterOptionGroup(
                    title = "比赛项目",
                    options = RaceCategory.entries,
                    selectedOptions = draftFilter.categories,
                    optionLabel = {
                        RacePresentation.category(it, RaceLabelDensity.COMPACT).text
                    },
                    onToggle = { category ->
                        draftFilter = draftFilter.copy(
                            categories = draftFilter.categories.toggled(category),
                        )
                    },
                )
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

@Composable
private fun RaceTimeline(
    section: RaceSection,
    filter: RaceFilter,
    monthGroups: List<RaceMonthGroup>,
    onSelectSection: (RaceSection) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenRace: (String) -> Unit,
    onQuickStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item(key = "home-controls") {
            RaceSectionTabs(
                section = section,
                onSelectSection = onSelectSection,
            )
            RaceFilterToolbar(
                resultCount = monthGroups.sumOf { it.races.size },
                filter = filter,
                onOpenFilters = onOpenFilters,
            )
        }
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = race.raceDate.dayOfMonth.toString().padStart(2, '0'),
                fontFamily = FontFamily.Monospace,
                fontSize = 30.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = RacePresentation.weekday(race.raceDate.dayOfWeek).text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = race.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatRaceTimelineSummary(race),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RunTripRaceStatusBadge(
                    status = race.status,
                    onClick = onQuickStatus,
                )
                race.travelDistanceKm?.let { distance ->
                    CompactMetadata(
                        icon = { Icon(Icons.Outlined.Route, contentDescription = null) },
                        text = RacePresentation.distance(distance).text,
                    )
                }
                CompactMetadata(
                    icon = { Icon(Icons.Outlined.Hotel, contentDescription = null) },
                    text = RacePresentation.hotelBookingStatus(race.hotelBookingStatus).text,
                )
            }
        }
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
    isFilteredEmpty: Boolean,
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
                imageVector = if (isFilteredEmpty) {
                    Icons.Outlined.FilterList
                } else {
                    Icons.Outlined.CalendarMonth
                },
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = when {
                    isFilteredEmpty -> "没有符合条件的比赛"
                    section == RaceSection.HISTORY -> "还没有历史比赛"
                    else -> "还没有比赛安排"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isFilteredEmpty) {
                    "试试调整筛选条件。"
                } else {
                    "把报名、酒店和路程放在一起。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickStatusSheet(
    race: Race,
    update: QuickStatusUpdate,
    onDismiss: () -> Unit,
    onSelectStatus: (RaceStatus) -> Unit,
) {
    val isSaving = update is QuickStatusUpdate.Saving
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

internal fun formatRaceTimelineSummary(race: Race): AnnotatedString = buildAnnotatedString {
    append(race.city)
    append(" · ")
    append(RacePresentation.category(race.category, RaceLabelDensity.COMPACT).text)
    race.caaRaceLevel?.let { level ->
        append(" · ")
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(RacePresentation.caaRaceLevel(level).text)
        }
    }
    race.worldAthleticsLabel?.let { label ->
        append(" · ")
        append(
            RacePresentation.worldAthleticsLabel(
                label,
                RaceLabelDensity.COMPACT,
            ).text,
        )
    }
}

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
            onOpenFilters = {},
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
    caaRaceLevel = CaaRaceLevel.A1,
    worldAthleticsLabel = WorldAthleticsLabel.PLATINUM,
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
