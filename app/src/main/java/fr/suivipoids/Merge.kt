package fr.suivipoids

import java.time.Instant

/** Moyenne glissante sur [windowDays] jours. */
fun smooth(points: List<WeightPoint>, windowDays: Long = 7): List<WeightPoint> =
    points.map { p ->
        val window = points.filter {
            !it.date.isAfter(p.date) && it.date.isAfter(p.date.minusDays(windowDays))
        }
        WeightPoint(p.date, window.map { it.kg }.average())
    }

/** Fusionne les données Health Connect (facultatives) et les saisies manuelles. */
fun buildHealthData(
    hc: HealthData?,
    manualWeights: List<ManualWeightEntry>,
    manualWorkouts: List<ManualWorkoutEntry>,
): HealthData {
    val entries = ((hc?.weights ?: emptyList()) +
        manualWeights.map { WeightPoint(it.date, it.kg, it.id) })
        .sortedBy { it.date }

    val perDay = entries
        .groupBy { it.date }
        .map { (d, l) -> WeightPoint(d, l.map { it.kg }.average()) }
        .sortedBy { it.date }

    val comp = (hc?.bodyComp ?: emptyList()).associateBy { it.date }.toMutableMap()
    manualWeights.filter { it.fatPercent != null || it.leanKg != null }.forEach {
        val old = comp[it.date]
        comp[it.date] = BodyCompPoint(
            it.date,
            it.fatPercent ?: old?.fatPercent,
            it.leanKg ?: old?.leanKg,
        )
    }

    val workouts = ((hc?.workouts ?: emptyList()) + manualWorkouts.map {
        Workout(
            start = Instant.ofEpochMilli(it.startEpochMs),
            durationMin = it.durationMin,
            title = it.title,
            calories = it.calories,
            distanceKm = it.distanceKm,
            id = it.id,
        )
    }).sortedByDescending { it.start }

    return HealthData(
        weights = perDay,
        trend = smooth(perDay, 7),
        bodyComp = comp.values.sortedBy { it.date },
        workouts = workouts,
        weightEntries = entries,
    )
}
