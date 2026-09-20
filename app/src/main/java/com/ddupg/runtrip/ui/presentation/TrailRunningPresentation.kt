package com.ddupg.runtrip.ui.presentation

import com.ddupg.runtrip.data.model.TrailRunningDetails

internal object TrailRunningPresentation {
    fun fields(details: TrailRunningDetails, density: RaceLabelDensity): List<SportDetailField> =
        details.elevationGainMeters?.let {
            listOf(SportDetailField("累计爬升", if (density == RaceLabelDensity.COMPACT) "爬升 $it m" else "$it m"))
        }.orEmpty()
}
