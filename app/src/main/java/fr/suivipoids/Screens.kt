@file:OptIn(ExperimentalMaterial3Api::class)

package fr.suivipoids

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

private val dateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

private fun fmt1(v: Double) = String.format(Locale.FRENCH, "%.1f", v)

private fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Composable
fun AppRoot(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val goal by vm.goal.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { vm.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Suivi Poids") }) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Ready -> MainTabs(
                    s = s,
                    goal = goal,
                    vm = vm,
                    onConnectHc = {
                        if (s.hcNeedsPermission) {
                            permissionLauncher.launch(vm.repo.requestedPermissions)
                        } else {
                            openHealthConnectStore(context)
                        }
                    },
                )
            }
        }
    }
}

private fun openHealthConnectStore(context: Context) {
    runCatching {
        val uri = Uri.parse(
            "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setPackage("com.android.vending")
                data = uri
                putExtra("overlay", true)
                putExtra("callerId", context.packageName)
            }
        )
    }
}

@Composable
private fun MainTabs(s: UiState.Ready, goal: Goal?, vm: MainViewModel, onConnectHc: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var bannerDismissed by remember { mutableStateOf(false) }
    val titles = listOf("Poids", "Activités", "Objectif")
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        if (s.hcInfo != null && !bannerDismissed) {
            Card(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "${s.hcInfo}. Pas de souci : tu peux saisir tes pesées et tes activités à la main.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        TextButton(onClick = onConnectHc) { Text("Connecter Health Connect") }
                        TextButton(onClick = { bannerDismissed = true }) { Text("Masquer") }
                    }
                }
            }
        }
        when (tab) {
            0 -> WeightTab(s.data, goal, vm)
            1 -> ActivitiesTab(s.data, vm)
            else -> GoalTab(s.data, goal, vm)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DateField(date: LocalDate, onChange: (LocalDate) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Date : ${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))}")
    }
    if (show) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Annuler") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    integer: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AddWeightDialog(
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double, Double?, Double?) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var kgText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var leanText by remember { mutableStateOf("") }
    val kg = kgText.toDecimalOrNull()
    val fat = fatText.toDecimalOrNull()
    val lean = leanText.toDecimalOrNull()
    val valid = kg != null && kg in 20.0..400.0 &&
        (fatText.isBlank() || (fat != null && fat in 1.0..80.0)) &&
        (leanText.isBlank() || (lean != null && lean in 5.0..300.0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une pesée") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(date) { date = it }
                NumberField(kgText, { kgText = it }, "Poids (kg)")
                NumberField(fatText, { fatText = it }, "Masse grasse en % (facultatif)")
                NumberField(leanText, { leanText = it }, "Masse maigre en kg (facultatif)")
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(date, kg!!, fat, lean); onDismiss() },
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun AddWorkoutDialog(
    onDismiss: () -> Unit,
    onSave: (LocalDate, Long, String, Double?, Double?) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var title by remember { mutableStateOf("Marche") }
    var minText by remember { mutableStateOf("") }
    var kcalText by remember { mutableStateOf("") }
    var kmText by remember { mutableStateOf("") }
    val minutes = minText.trim().toLongOrNull()
    val kcal = kcalText.toDecimalOrNull()
    val km = kmText.toDecimalOrNull()
    val valid = title.isNotBlank() && minutes != null && minutes in 1..1440 &&
        (kcalText.isBlank() || kcal != null) && (kmText.isBlank() || km != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une activité") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(date) { date = it }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Activité (marche, vélo, course...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField(minText, { minText = it }, "Durée (minutes)", integer = true)
                NumberField(kcalText, { kcalText = it }, "Calories (facultatif)")
                NumberField(kmText, { kmText = it }, "Distance en km (facultatif)")
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(date, minutes!!, title.trim(), kcal, km); onDismiss() },
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun WeightTab(data: HealthData, goal: Goal?, vm: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    if (showAdd) {
        AddWeightDialog(onDismiss = { showAdd = false }, onSave = vm::addWeight)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Ajouter une pesée")
            }
        }
        if (data.weights.isEmpty()) {
            item {
                Text(
                    "Aucune pesée pour le moment. Ajoute ta première pesée pour voir ta courbe et définir un objectif."
                )
            }
            return@LazyColumn
        }

        val last = data.weights.last()
        val trendNow = data.trend.last().kg
        val monthAgo = last.date.minusDays(30)
        val reference = data.trend.lastOrNull { !it.date.isAfter(monthAgo) } ?: data.trend.first()
        val delta = trendNow - reference.kg
        val comp = data.bodyComp.lastOrNull()

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Dernier poids", "${fmt1(last.kg)} kg", Modifier.weight(1f))
                StatCard("Tendance 7 j", "${fmt1(trendNow)} kg", Modifier.weight(1f))
                StatCard(
                    "Sur 30 j",
                    (if (delta > 0) "+" else "") + fmt1(delta) + " kg",
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Évolution du poids", fontWeight = FontWeight.Bold)
                    Text(
                        if (data.weights.size < 2) {
                            "Ajoute une deuxième pesée pour voir la courbe."
                        } else {
                            "Points = pesées, trait = moyenne sur 7 jours" +
                                if (goal != null) ", ligne = objectif" else ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    WeightChart(data.weights, data.trend, goal?.targetKg)
                }
            }
        }
        if (comp != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Composition corporelle", fontWeight = FontWeight.Bold)
                        Text(comp.date.format(dateFmt), style = MaterialTheme.typography.bodySmall)
                        comp.fatPercent?.let { Text("Masse grasse : ${fmt1(it)} %") }
                        comp.leanKg?.let { Text("Masse maigre : ${fmt1(it)} kg") }
                    }
                }
            }
        }
        item { Text("Dernières pesées", fontWeight = FontWeight.Bold) }
        items(data.weightEntries.takeLast(30).reversed()) { w ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(w.date.format(dateFmt))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fmt1(w.kg)} kg")
                    if (w.id != null) {
                        TextButton(onClick = { vm.deleteWeight(w.id) }) { Text("Supprimer") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivitiesTab(data: HealthData, vm: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    if (showAdd) {
        AddWorkoutDialog(onDismiss = { showAdd = false }, onSave = vm::addWorkout)
    }

    val zone = ZoneId.systemDefault()
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = (7 downTo 0).map { monday.minusWeeks(it.toLong()) }
    val minutesPerWeek = weeks.map { start ->
        data.workouts
            .filter {
                val d = it.start.atZone(zone).toLocalDate()
                !d.isBefore(start) && d.isBefore(start.plusDays(7))
            }
            .sumOf { it.durationMin }
    }
    val thisWeek = data.workouts.filter {
        !it.start.atZone(zone).toLocalDate().isBefore(monday)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Ajouter une activité")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Séances", "${thisWeek.size}", Modifier.weight(1f))
                StatCard("Minutes", "${thisWeek.sumOf { it.durationMin }}", Modifier.weight(1f))
                StatCard(
                    "Kcal",
                    "${thisWeek.sumOf { it.calories ?: 0.0 }.roundToInt()}",
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Minutes d'activité par semaine (8 dernières)", fontWeight = FontWeight.Bold)
                    Text(
                        "Repère de santé courant : 150 min par semaine.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    WeeklyBars(minutesPerWeek)
                }
            }
        }
        if (data.workouts.isEmpty()) {
            item { Text("Aucune activité pour le moment. Ajoute ta première séance.") }
        }
        items(data.workouts) { w ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(w.title, fontWeight = FontWeight.Bold)
                    Text(
                        w.start.atZone(zone).toLocalDate().format(dateFmt) +
                            " · ${w.durationMin} min" +
                            (w.distanceKm?.let { " · ${fmt1(it)} km" } ?: "") +
                            (w.calories?.let { " · ${it.roundToInt()} kcal" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (w.id != null) {
                        TextButton(onClick = { vm.deleteWorkout(w.id) }) { Text("Supprimer") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalTab(data: HealthData, goal: Goal?, vm: MainViewModel) {
    val currentKg = data.trend.lastOrNull()?.kg
    var targetText by remember { mutableStateOf(goal?.targetKg?.let { fmt1(it) } ?: "") }
    var weeksText by remember { mutableStateOf("12") }
    val today = LocalDate.now()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (goal != null && currentKg != null) {
            item {
                val total = goal.startKg - goal.targetKg
                val done = goal.startKg - currentKg
                val progress = if (total > 0) (done / total).coerceIn(0.0, 1.0) else 0.0
                val remaining = currentKg - goal.targetKg
                val weeksLeft = ChronoUnit.DAYS.between(today, goal.targetDate) / 7.0
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Progression", fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { progress.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Départ ${fmt1(goal.startKg)} kg → objectif ${fmt1(goal.targetKg)} kg " +
                                "(${(progress * 100).roundToInt()} %)"
                        )
                        Text("Poids actuel (tendance) : ${fmt1(currentKg)} kg")
                        if (remaining > 0) {
                            Text("Reste à perdre : ${fmt1(remaining)} kg")
                            if (weeksLeft > 0) {
                                val pace = remaining / weeksLeft
                                Text("Rythme nécessaire : ${fmt1(pace)} kg par semaine")
                                if (pace > 1.0) {
                                    Text(
                                        "Ce rythme est supérieur à environ 1 kg par semaine, difficile à tenir " +
                                            "durablement. Pense à alléger l'objectif ou à en parler à un médecin.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            } else {
                                Text("La date cible est dépassée : fixe-toi une nouvelle échéance.")
                            }
                        } else {
                            Text("Objectif atteint, bravo !", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Date cible : ${goal.targetDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (goal == null) "Définir un objectif" else "Modifier l'objectif",
                        fontWeight = FontWeight.Bold,
                    )
                    if (currentKg == null) {
                        Text(
                            "Ajoute d'abord une pesée (onglet Poids) : elle sert de point de départ.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    NumberField(targetText, { targetText = it }, "Poids cible (kg)")
                    NumberField(weeksText, { weeksText = it }, "Durée (semaines)", integer = true)
                    val target = targetText.toDecimalOrNull()
                    val weeks = weeksText.trim().toIntOrNull()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = currentKg != null && target != null && weeks != null && weeks > 0,
                            onClick = {
                                vm.saveGoal(
                                    Goal(
                                        targetKg = target!!,
                                        targetDate = today.plusWeeks(weeks!!.toLong()),
                                        startKg = currentKg!!,
                                        startDate = today,
                                    )
                                )
                            },
                        ) { Text("Enregistrer") }
                        if (goal != null) {
                            OutlinedButton(onClick = { vm.clearGoal() }) { Text("Supprimer") }
                        }
                    }
                }
            }
        }
    }
}
