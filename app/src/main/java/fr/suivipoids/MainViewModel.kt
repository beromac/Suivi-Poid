package fr.suivipoids

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId

sealed interface UiState {
    data object Loading : UiState

    /**
     * [hcInfo] : message si Health Connect n'est pas utilisable (null si tout va bien).
     * L'appli reste utilisable avec les saisies manuelles dans tous les cas.
     */
    data class Ready(
        val data: HealthData,
        val hcInfo: String?,
        val hcNeedsPermission: Boolean,
    ) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = HealthRepository(app)
    private val goalStore = GoalStore(app)
    private val manual = ManualStore(app)

    // L'écran s'affiche tout de suite avec les saisies manuelles ; Health Connect s'ajoute ensuite.
    private val _state = MutableStateFlow<UiState>(
        UiState.Ready(
            data = buildHealthData(null, safeWeights(), safeWorkouts()),
            hcInfo = null,
            hcNeedsPermission = false,
        )
    )
    val state: StateFlow<UiState> = _state

    private val _goal = MutableStateFlow(runCatching { goalStore.load() }.getOrNull())
    val goal: StateFlow<Goal?> = _goal

    private var lastHc: HealthData? = null
    private var lastInfo: String? = null
    private var lastNeedsPermission = false

    private fun safeWeights() = runCatching { manual.weights() }.getOrDefault(emptyList())
    private fun safeWorkouts() = runCatching { manual.workouts() }.getOrDefault(emptyList())

    private fun publish() {
        _state.value = UiState.Ready(
            data = buildHealthData(lastHc, safeWeights(), safeWorkouts()),
            hcInfo = lastInfo,
            hcNeedsPermission = lastNeedsPermission,
        )
    }

    /** Relit les saisies manuelles immédiatement, puis tente Health Connect (8 secondes maximum). */
    fun refresh() {
        publish()
        viewModelScope.launch {
            var hc: HealthData? = null
            var info: String? = null
            var needsPermission = false
            try {
                when (repo.sdkStatus()) {
                    HealthConnectClient.SDK_UNAVAILABLE ->
                        info = "Health Connect n'est pas installé"
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                        info = "Health Connect doit être mis à jour"
                    else -> {
                        val result = withTimeoutOrNull(8000) {
                            if (!repo.hasCorePermissions()) null else repo.load()
                        }
                        when {
                            result != null -> hc = result
                            else -> {
                                info = "Health Connect n'est pas autorisé ou ne répond pas"
                                needsPermission = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                info = "Health Connect indisponible"
            }
            lastHc = hc
            lastInfo = info
            lastNeedsPermission = needsPermission
            publish()
        }
    }

    fun addWeight(date: LocalDate, kg: Double, fat: Double?, lean: Double?) {
        manual.addWeight(date, kg, fat, lean)
        publish()
    }

    fun deleteWeight(id: String) {
        manual.deleteWeight(id)
        publish()
    }

    fun addWorkout(date: LocalDate, minutes: Long, title: String, kcal: Double?, km: Double?) {
        val start = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        manual.addWorkout(start, minutes, title, kcal, km)
        publish()
    }

    fun deleteWorkout(id: String) {
        manual.deleteWorkout(id)
        publish()
    }

    fun saveGoal(goal: Goal) {
        goalStore.save(goal)
        _goal.value = goal
    }

    fun clearGoal() {
        goalStore.clear()
        _goal.value = null
    }
}
