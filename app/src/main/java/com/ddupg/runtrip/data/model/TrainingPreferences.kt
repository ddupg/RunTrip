package com.ddupg.runtrip.data.model

data class TrainingPreferences(
    val runningWeeklyGrowthPercent: Double = 5.0,
    val runningMonthlyGrowthPercent: Double = 10.0,
    val cyclingFactor: Double = 0.25,
    val swimmingFactor: Double = 4.0,
) {
    init {
        require(runningWeeklyGrowthPercent.isFinite() && runningWeeklyGrowthPercent >= 0)
        require(runningMonthlyGrowthPercent.isFinite() && runningMonthlyGrowthPercent >= 0)
        require(cyclingFactor.isFinite() && cyclingFactor > 0)
        require(swimmingFactor.isFinite() && swimmingFactor > 0)
    }
}
