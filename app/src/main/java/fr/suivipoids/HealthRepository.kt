package fr.suivipoids

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

class HealthRepository(private val context: Context) {

    /** Permissions indispensables. */
    val corePermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
    )

    /** Permissions demandées à l'utilisateur (avec l'accès à l'historique de plus de 30 jours). */
    val requestedPermissions: Set<String> =
        corePermissions + "android.permission.health.READ_HEALTH_DATA_HISTORY"

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasCorePermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(corePermissions)

    suspend fun load(days: Long = 180): HealthData {
        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days))
        val filter = TimeRangeFilter.between(start, end)

        val weights = readAll(WeightRecord::class, filter)
            .map { WeightPoint(it.time.atZone(zone).toLocalDate(), it.weight.inKilograms) }
            .groupBy { it.date }
            .map { (date, list) -> WeightPoint(date, list.map { it.kg }.average()) }
            .sortedBy { it.date }

        val fat = readAll(BodyFatRecord::class, filter)
            .associate { it.time.atZone(zone).toLocalDate() to it.percentage.value }
        val lean = readAll(LeanBodyMassRecord::class, filter)
            .associate { it.time.atZone(zone).toLocalDate() to it.mass.inKilograms }
        val bodyComp = (fat.keys + lean.keys).sorted()
            .map { BodyCompPoint(it, fat[it], lean[it]) }

        val sessions = readAll(ExerciseSessionRecord::class, filter)
            .sortedByDescending { it.startTime }
            .take(80)
        val workouts = sessions.map { s ->
            val sessionFilter = TimeRangeFilter.between(s.startTime, s.endTime)
            val agg = runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                            DistanceRecord.DISTANCE_TOTAL,
                        ),
                        timeRangeFilter = sessionFilter,
                    )
                )
            }.getOrNull()
            val kcal = agg?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories
                ?: agg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories
            val km = agg?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers
            Workout(
                start = s.startTime,
                durationMin = Duration.between(s.startTime, s.endTime).toMinutes(),
                title = s.title?.takeIf { it.isNotBlank() } ?: exerciseName(s.exerciseType),
                calories = kcal?.takeIf { it > 0 },
                distanceKm = km?.takeIf { it > 0 },
            )
        }

        return HealthData(
            weights = weights,
            trend = movingAverage(weights, 7),
            bodyComp = bodyComp,
            workouts = workouts,
        )
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val result = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = filter,
                    pageSize = 1000,
                    pageToken = token,
                )
            )
            result += response.records
            token = response.pageToken
        } while (token != null)
        return result
    }

    private fun movingAverage(points: List<WeightPoint>, windowDays: Long): List<WeightPoint> =
        points.map { p ->
            val window = points.filter {
                !it.date.isAfter(p.date) && it.date.isAfter(p.date.minusDays(windowDays))
            }
            WeightPoint(p.date, window.map { it.kg }.average())
        }

    private fun exerciseName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Course à pied"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Marche"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Randonnée"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Vélo"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Natation"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Renforcement musculaire"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptique"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rameur"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        else -> "Activité"
    }

    fun today(): LocalDate = LocalDate.now()
}
