package com.ddupg.runtrip.feature.form

import androidx.compose.foundation.lazy.LazyListScope
import com.ddupg.runtrip.data.model.CaaRaceLevel
import com.ddupg.runtrip.data.model.WorldAthleticsLabel
import com.ddupg.runtrip.ui.presentation.RaceLabelDensity
import com.ddupg.runtrip.ui.presentation.RacePresentation

internal fun LazyListScope.roadRunningFields(draft: RoadRunningDraft, onChange: (RoadRunningDraft) -> Unit) {
    item { SectionDivider() }
    item { FormSectionTitle("赛事等级", "选填") }
    item {
        OptionalChoiceChips(
            label = "中国田协等级",
            values = CaaRaceLevel.entries,
            selected = draft.caaRaceLevel,
            key = CaaRaceLevel::code,
            displayName = { RacePresentation.caaRaceLevel(it).text },
            onSelected = { onChange(draft.copy(caaRaceLevel = it)) },
        )
    }
    item {
        OptionalChoiceChips(
            label = "World Athletics Label",
            values = WorldAthleticsLabel.entries,
            selected = draft.worldAthleticsLabel,
            key = WorldAthleticsLabel::code,
            displayName = { RacePresentation.worldAthleticsLabel(it, RaceLabelDensity.FULL).text },
            onSelected = { onChange(draft.copy(worldAthleticsLabel = it)) },
        )
    }
}
