package fr.suivipoids

import android.content.Context
import java.time.LocalDate

class GoalStore(context: Context) {
    private val prefs = context.getSharedPreferences("goal", Context.MODE_PRIVATE)

    fun load(): Goal? {
        val target = prefs.getFloat("targetKg", -1f)
        val date = prefs.getString("targetDate", null)
        val startKg = prefs.getFloat("startKg", -1f)
        val startDate = prefs.getString("startDate", null)
        if (target < 0 || date == null || startKg < 0 || startDate == null) return null
        return runCatching {
            Goal(
                targetKg = target.toDouble(),
                targetDate = LocalDate.parse(date),
                startKg = startKg.toDouble(),
                startDate = LocalDate.parse(startDate),
            )
        }.getOrNull()
    }

    fun save(goal: Goal) {
        prefs.edit()
            .putFloat("targetKg", goal.targetKg.toFloat())
            .putString("targetDate", goal.targetDate.toString())
            .putFloat("startKg", goal.startKg.toFloat())
            .putString("startDate", goal.startDate.toString())
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
