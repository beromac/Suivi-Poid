package fr.suivipoids

import java.time.Instant
import java.time.LocalDate

data class WeightPoint(val date: LocalDate, val kg: Double, val id: String? = null)

data class BodyCompPoint(
    val date: LocalDate,
    val fatPercent: Double?,
    val leanKg: Double?,
)

data class Workout(
    val start: Instant,
    val durationMin: Long,
    val title: String,
    val calories: Double?,
    val distanceKm: Double?,
    val id: String? = null,
)

data class Goal(
    val targetKg: Double,
    val targetDate: LocalDate,
    val startKg: Double,
    val startDate: LocalDate,
)

data class HealthData(
    /** Un point par jour (moyenne), pour le graphique. */
    val weights: List<WeightPoint>,
    val trend: List<WeightPoint>,
    val bodyComp: List<BodyCompPoint>,
    val workouts: List<Workout>,
    /** Toutes les pesées, une ligne par saisie (les saisies manuelles ont un id). */
    val weightEntries: List<WeightPoint> = emptyList(),
)
