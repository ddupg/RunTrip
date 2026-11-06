package com.ddupg.runtrip.feature.training

import com.ddupg.runtrip.data.model.TrainingActivity
import com.ddupg.runtrip.data.model.TrainingSport
import com.ddupg.runtrip.data.model.TrainingSubtype
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TrainingDay(val date: LocalDate, val activities: List<TrainingActivity>)

fun trainingDays(records: List<TrainingActivity>, sport: TrainingSport?, zone: ZoneId): List<TrainingDay> = records
    .filter { sport == null || it.subtype.sport == sport }
    .groupBy { trainingDate(it, zone) }.toSortedMap(compareByDescending { it })
    .map { (date, activities) -> TrainingDay(date, activities.sortedWith(
        compareByDescending<TrainingActivity> { it.startEpochSeconds ?: Long.MIN_VALUE }.thenBy { it.id },
    )) }

internal fun trainingDate(activity: TrainingActivity, zone: ZoneId): LocalDate = activity.startEpochSeconds
    ?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDate() } ?: activity.sourceDate

fun trainingDateLabel(date: LocalDate, today: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "M月d日 E" else "yyyy年M月d日 E", Locale.CHINA))

fun trainingTime(activity: TrainingActivity, zone: ZoneId): String = activity.startEpochSeconds
    ?.let { Instant.ofEpochSecond(it).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "--:--"

fun trainingDistance(activity: TrainingActivity): String = activity.distanceMeters?.let {
    if (activity.subtype.sport == TrainingSport.SWIMMING) String.format(Locale.ROOT, "%.0f m", it)
    else String.format(Locale.ROOT, "%.2f km", it / 1000)
} ?: "距离未记录"

fun trainingDuration(seconds: Long): String = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)

fun trainingSubtypeLabel(subtype: TrainingSubtype): String = when (subtype) {
    TrainingSubtype.OUTDOOR_RUN -> "户外跑"
    TrainingSubtype.INDOOR_RUN -> "室内跑"
    TrainingSubtype.TRAIL_RUN -> "越野跑"
    TrainingSubtype.TRACK_RUN -> "操场跑"
    TrainingSubtype.OUTDOOR_RIDE -> "户外骑行"
    TrainingSubtype.INDOOR_RIDE -> "室内骑行"
    TrainingSubtype.E_BIKE -> "电助力骑行"
    TrainingSubtype.GRAVEL_RIDE -> "砾石骑行"
    TrainingSubtype.MOUNTAIN_BIKE -> "山地骑行"
    TrainingSubtype.E_MOUNTAIN_BIKE -> "电助力山地骑行"
    TrainingSubtype.HELMET_RIDE -> "头盔骑行"
    TrainingSubtype.POOL_SWIM -> "泳池游泳"
    TrainingSubtype.OPEN_WATER_SWIM -> "公开水域游泳"
}
