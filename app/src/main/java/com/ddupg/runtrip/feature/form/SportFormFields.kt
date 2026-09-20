package com.ddupg.runtrip.feature.form

import androidx.compose.foundation.lazy.LazyListScope

internal fun LazyListScope.sportFields(draft: SportDraft, errors: Map<String, String>, onChange: (SportDraft) -> Unit) {
    when (draft) {
        is RoadRunningDraft -> roadRunningFields(draft, onChange)
        is TriathlonDraft -> Unit
        is TrailRunningDraft -> trailRunningFields(draft, errors, onChange)
    }
}
