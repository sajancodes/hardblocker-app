package dev.sajancodes.hardblock.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sajancodes.hardblock.data.repository.PolicyCodec
import dev.sajancodes.hardblock.domain.policy.*
import dev.sajancodes.hardblock.ui.dashboard.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

internal fun policySummary(p: RestrictionPolicy): String = when {
    p.dailyLimit != null -> "Daily limit · ${requireNotNull(p.dailyLimit).toMinutes()}m · resets ${LocalTime.ofSecondOfDay(p.resetMinute * 60L)} ${p.zoneId}"
    p.alwaysBlocked -> "Always blocked during commitment"
    p.blockedUntil != null -> "Focus until ${displayTime(requireNotNull(p.blockedUntil))}"
    else -> "Scheduled lock · ${p.schedule.size} windows"
}
private fun displayTime(at: Instant) = DateTimeFormatter.ofPattern("d MMM, HH:mm z").format(at.atZone(ZoneId.systemDefault()))

@Composable
fun CommitmentsScreen(state: DashboardState, model: DashboardViewModel) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var accepting by remember { mutableStateOf<String?>(null) }
    val editing = state.policies.find { it.id == editingId }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your commitments", style = MaterialTheme.typography.headlineLarge) }
        item { Text(state.protection.detail, color = if (state.protection.state == EnforcementState.INTERRUPTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("Create commitment") } }
        if (state.policies.isEmpty()) item { Text("Choose applications, set a rule, then explicitly commit. Restrictions can become stricter immediately.") }
        items(state.policies, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.packages.joinToString { pkg -> state.apps.find { it.packageName == pkg }?.label ?: pkg }, style = MaterialTheme.typography.titleMedium)
                    Text(policySummary(p))
                    Text(if (PolicyEvaluation.active(p, state.now)) "ACTIVE · ${p.expiresAt?.let(::displayTime) ?: "Indefinite with delayed relaxation"}"
                        else if (p.state == PolicyState.DISABLED) "Disabled after eligible acceptance" else "Commitment expired")
                    p.dailyLimit?.let { limit -> p.packages.forEach { pkg ->
                        val used = state.policyUsage[p.id]?.get(pkg) ?: 0L
                        Text("${state.apps.find { it.packageName == pkg }?.label ?: pkg}: ${duration(used)} used · ${duration((limit.toMillis() - used).coerceAtLeast(0))} remaining", style = MaterialTheme.typography.bodySmall)
                    } }
                    TextButton(onClick = { editingId = p.id }) { Text("Review / change restriction") }
                }
            }
        }
        items(state.requests.filter { it.status == "PENDING" }, key = { it.id }) { r ->
            val proposed = remember(r.proposed) { PolicyCodec.decode(r.proposed) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Relaxation request", style = MaterialTheme.typography.titleMedium)
                    Text("Requested: ${if (proposed.state == PolicyState.DISABLED) "Disable restriction" else policySummary(proposed)}")
                    Text("Available ${displayTime(Instant.ofEpochMilli(r.availableAt))}")
                    Text("Delay cannot be shortened. Your current restriction remains in place.")
                    TextButton(onClick = { model.cancelRequest(r.id) }, enabled = !state.saving) { Text("Cancel request") }
                    if (state.now.toEpochMilli() >= r.availableAt) OutlinedButton(onClick = { accepting = r.id }) { Text("Review eligible change") }
                }
            }
        }
    }
    if (creating || editing != null) PolicyEditor(state, model, editing) { creating = false; editingId = null }
    accepting?.let { id -> AlertDialog(onDismissRequest = { accepting = null }, title = { Text("Accept this relaxation?") },
        text = { Text("This deliberately applies your requested weaker rule. Eligibility and the current policy revision will be checked again.") },
        confirmButton = { TextButton(onClick = { model.acceptRequest(id); accepting = null }) { Text("Confirm acceptance") } },
        dismissButton = { TextButton(onClick = { accepting = null }) { Text("Keep current rule") } }) }
}

@Composable
private fun PolicyEditor(state: DashboardState, model: DashboardViewModel, current: RestrictionPolicy?, close: () -> Unit) {
    var kind by remember { mutableStateOf(when { current?.alwaysBlocked == true -> "Always"; current?.blockedUntil != null -> "Focus"; current?.schedule?.isNotEmpty() == true -> "Schedule"; else -> "Daily" }) }
    var selected by remember { mutableStateOf(current?.packages ?: emptySet()) }
    var minutes by remember { mutableStateOf((current?.dailyLimit?.toMinutes() ?: 30).toString()) }
    var focusMinutes by remember { mutableStateOf("180") }
    var reset by remember { mutableStateOf(current?.let { LocalTime.ofSecondOfDay(it.resetMinute * 60L).toString() } ?: "00:00") }
    var days by remember { mutableStateOf("7") }
    var customEnd by remember { mutableStateOf(current?.expiresAt?.atZone(ZoneId.systemDefault())?.toLocalDateTime()?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")) ?: "") }
    var cooldown by remember { mutableStateOf((current?.relaxationCooldown?.toHours() ?: 72).toString()) }
    var warnings by remember { mutableStateOf((current?.warningMinutes ?: setOf(5, 1)).sortedDescending().joinToString(",")) }
    var start by remember { mutableStateOf(current?.schedule?.firstOrNull()?.startMinute?.let { LocalTime.ofSecondOfDay(it * 60L).toString() } ?: "22:00") }
    var end by remember { mutableStateOf(current?.schedule?.firstOrNull()?.endMinute?.let { LocalTime.ofSecondOfDay(it * 60L).toString() } ?: "08:00") }
    var weekdays by remember { mutableStateOf(current?.schedule?.map { it.day }?.toSet() ?: (0..6).toSet()) }
    var search by remember { mutableStateOf("") }
    var proposal by remember { mutableStateOf<RestrictionPolicy?>(null) }
    var request by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun prepare(relaxation: Boolean, disable: Boolean = false) {
        try {
            val now = state.now
            val zone = current?.zoneId ?: ZoneId.systemDefault().id
            val base = current ?: RestrictionPolicy(UUID.randomUUID().toString(), selected, now, zoneId = zone)
            val expiry = if (customEnd.isNotBlank()) LocalDateTime.parse(customEnd).atZone(ZoneId.of(zone)).toInstant()
                else when(days) { "Indefinite" -> null; "Today" -> now.atZone(ZoneId.of(zone)).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant(); else -> now.plus(Duration.ofDays(days.toLong())) }
            val resetTime = LocalTime.parse(reset)
            val until = if (kind == "Focus") now.plus(Duration.ofMinutes(focusMinutes.toLong().also { require(it > 0) })) else null
            val startTime = LocalTime.parse(start); val endTime = LocalTime.parse(end)
            val draft = if (disable) base.copy(state = PolicyState.DISABLED) else base.copy(packages = selected,
                expiresAt = if (kind == "Focus") until else expiry,
                dailyLimit = if (kind == "Daily") Duration.ofMinutes(minutes.toLong()) else null,
                resetMinute = resetTime.hour * 60 + resetTime.minute, blockedUntil = until, alwaysBlocked = kind == "Always",
                schedule = if (kind == "Schedule") weekdays.map { WeeklyWindow(it, startTime.hour * 60 + startTime.minute, endTime.hour * 60 + endTime.minute) }.toSet() else emptySet(),
                relaxationCooldown = Duration.ofHours(cooldown.toLong()), warningMinutes = if (warnings.isBlank()) emptySet() else warnings.split(',').map { it.trim().toInt() }.toSet())
            val result = PolicyEngine().validate(draft)
            require(result.allowed) { result.reason }
            proposal = draft; request = relaxation; error = null
        } catch (e: Exception) { error = e.message ?: "Check the configuration" }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = close, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (current == null) "Create commitment" else "Review restriction", style = MaterialTheme.typography.headlineMedium)
                if (current == null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Daily", "Focus", "Schedule", "Always").forEach { value -> FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(value) }) } }
                if (kind == "Daily") { OutlinedTextField(minutes, { minutes = it }, label = { Text("Minutes per day (0–1440)") }); OutlinedTextField(reset, { reset = it }, label = { Text("Daily reset HH:mm") }) }
                if (kind == "Daily") OutlinedTextField(warnings, { warnings = it }, label = { Text("Warning minutes, comma separated (e.g. 5,1)") })
                if (kind == "Focus") OutlinedTextField(focusMinutes, { focusMinutes = it }, label = { Text("Focus minutes from now") })
                if (kind == "Schedule") {
                    OutlinedTextField(start, { start = it }, label = { Text("Block starts HH:mm") }); OutlinedTextField(end, { end = it }, label = { Text("Block ends HH:mm (next day if earlier)") })
                    Row { (0..6).forEach { d -> FilterChip(selected = d in weekdays, onClick = { weekdays = if (d in weekdays) weekdays - d else weekdays + d }, label = { Text(listOf("M", "T", "W", "T", "F", "S", "S")[d]) }, modifier = Modifier.weight(1f)) } }
                }
                if (kind != "Focus") {
                    Text("Commitment duration")
                    listOf(listOf("Today", "1", "3", "7", "14"), listOf("30", "90", "Indefinite")).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { value -> FilterChip(selected = days == value && customEnd.isBlank(), onClick = { days = value; customEnd = "" }, label = { Text(if (value.toIntOrNull() != null) "${value}d" else value) }) } } }
                    OutlinedTextField(customEnd, { customEnd = it }, label = { Text("Custom local end yyyy-MM-ddTHH:mm") }, modifier = Modifier.fillMaxWidth())
                }
                Text("Relaxation cooldown")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf(24L, 72L, 168L, 720L).forEach { hours -> FilterChip(selected = cooldown == hours.toString(), onClick = { cooldown = hours.toString() }, label = { Text(if(hours < 100) "${hours}h" else "${hours / 24}d") }) } }
                Text("${selected.size} applications selected · essential applications are protected")
                OutlinedTextField(search, { search = it }, label = { Text("Search applications") }, modifier = Modifier.fillMaxWidth())
                state.apps.filter { it.packageName !in state.protectedPackages && (it.label.contains(search, true) || it.packageName in selected) }.forEach { app ->
                    Row { Checkbox(checked = app.packageName in selected, onCheckedChange = { checked -> selected = if (checked) selected + app.packageName else selected - app.packageName }); Column(Modifier.weight(1f)) { Text(app.label); Text(app.packageName, style = MaterialTheme.typography.bodySmall) } }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { prepare(false) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Review commitment") }
                if (current != null) {
                    OutlinedButton(onClick = { prepare(true) }, modifier = Modifier.fillMaxWidth()) { Text("Request delayed relaxation") }
                    TextButton(onClick = { prepare(true, true) }) { Text("Request disabling after eligibility") }
                }
                TextButton(onClick = close) { Text("Close") }
            }
        }
    }
    proposal?.let { p ->
        AlertDialog(onDismissRequest = { proposal = null }, title = { Text(if (request) "Queue relaxation request?" else "Confirm your commitment") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (p.state == PolicyState.DISABLED) "Requested: disable restriction" else policySummary(p))
                Text("Applications: ${p.packages.joinToString { pkg -> state.apps.find { it.packageName == pkg }?.label ?: pkg }}")
                Text("Commitment until ${p.expiresAt?.let(::displayTime) ?: "indefinite"}. Relaxation delay: ${p.relaxationCooldown.toHours()} hours.")
                Text("Protection: ${state.protection.mode}. Standard Mode can be bypassed through Android system settings.")
                Text(if (request) "This does not weaken anything now. Eligibility requires the cooldown and any finite commitment to expire, then another explicit acceptance. A stricter edit cancels pending requests."
                    else "You can decrease allowances, add blocked apps and extend commitments. You cannot increase allowances, remove blocked apps, disable the rule or shorten the delay while committed.")
            } }, confirmButton = { TextButton(onClick = { model.save(p, current, request); proposal = null; close() }) { Text(if (request) "Confirm request" else "I commit") } },
            dismissButton = { TextButton(onClick = { proposal = null }) { Text("Go back") } })
    }
}
