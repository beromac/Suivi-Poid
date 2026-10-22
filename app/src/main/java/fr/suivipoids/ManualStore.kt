package fr.suivipoids

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class ManualWeightEntry(
    val id: String,
    val date: LocalDate,
    val kg: Double,
    val fatPercent: Double?,
    val leanKg: Double?,
)

data class ManualWorkoutEntry(
    val id: String,
    val startEpochMs: Long,
    val durationMin: Long,
    val title: String,
    val calories: Double?,
    val distanceKm: Double?,
)

/** Saisies manuelles, conservées uniquement sur le téléphone. */
class ManualStore(context: Context) {
    private val prefs = context.getSharedPreferences("manual", Context.MODE_PRIVATE)

    fun weights(): List<ManualWeightEntry> {
        val arr = JSONArray(prefs.getString("weights", "[]"))
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                ManualWeightEntry(
                    id = o.getString("id"),
                    date = LocalDate.parse(o.getString("date")),
                    kg = o.getDouble("kg"),
                    fatPercent = if (o.has("fat")) o.getDouble("fat") else null,
                    leanKg = if (o.has("lean")) o.getDouble("lean") else null,
                )
            }.getOrNull()
        }
    }

    private fun saveWeights(list: List<ManualWeightEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("date", it.date.toString())
                put("kg", it.kg)
                it.fatPercent?.let { v -> put("fat", v) }
                it.leanKg?.let { v -> put("lean", v) }
            })
        }
        prefs.edit().putString("weights", arr.toString()).apply()
    }

    fun addWeight(date: LocalDate, kg: Double, fat: Double?, lean: Double?) {
        saveWeights(weights() + ManualWeightEntry(UUID.randomUUID().toString(), date, kg, fat, lean))
    }

    fun deleteWeight(id: String) = saveWeights(weights().filter { it.id != id })

    fun workouts(): List<ManualWorkoutEntry> {
        val arr = JSONArray(prefs.getString("workouts", "[]"))
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                ManualWorkoutEntry(
                    id = o.getString("id"),
                    startEpochMs = o.getLong("start"),
                    durationMin = o.getLong("min"),
                    title = o.getString("title"),
                    calories = if (o.has("kcal")) o.getDouble("kcal") else null,
                    distanceKm = if (o.has("km")) o.getDouble("km") else null,
                )
            }.getOrNull()
        }
    }

    private fun saveWorkouts(list: List<ManualWorkoutEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("start", it.startEpochMs)
                put("min", it.durationMin)
                put("title", it.title)
                it.calories?.let { v -> put("kcal", v) }
                it.distanceKm?.let { v -> put("km", v) }
            })
        }
        prefs.edit().putString("workouts", arr.toString()).apply()
    }

    fun addWorkout(startEpochMs: Long, minutes: Long, title: String, kcal: Double?, km: Double?) {
        saveWorkouts(
            workouts() + ManualWorkoutEntry(UUID.randomUUID().toString(), startEpochMs, minutes, title, kcal, km)
        )
    }

    fun deleteWorkout(id: String) = saveWorkouts(workouts().filter { it.id != id })
}
