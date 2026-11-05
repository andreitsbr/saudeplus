package com.saudeplus.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Green = Color(0xFF2E7D32)
private val Teal = Color(0xFF00796B)
private val LateRed = Color(0xFFC62828)

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MedStore.init(this)
        Reminders.createChannel(this)
        Reminders.scheduleAll(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Teal,
                    secondary = Green,
                    background = Color(0xFFF5F7F6),
                    surface = Color(0xFFF5F7F6),
                )
            ) {
                HomeScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val meds by MedStore.meds.collectAsState()
    val taken by MedStore.taken.collectAsState()

    // "Agora" atualizado periodicamente e ao voltar para o app (vira o dia, atrasos etc.).
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    LifecycleResumeEffect(Unit) {
        MedStore.reload()
        now = LocalDateTime.now()
        onPauseOrDispose { }
    }

    val today = now.toLocalDate()
    val sorted = meds.sortedWith(compareBy({ it.hour }, { it.minute }, { it.name }))
    val doneCount = sorted.count { taken.containsKey(MedStore.key(it.id, today)) }

    var notifOk by remember { mutableStateOf(Reminders.notificationsEnabled(context)) }
    var exactOk by remember { mutableStateOf(Reminders.canScheduleExact(context)) }
    LifecycleResumeEffect(Unit) {
        notifOk = Reminders.notificationsEnabled(context)
        exactOk = Reminders.canScheduleExact(context)
        if (exactOk) Reminders.scheduleAll(context)
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifOk = Reminders.notificationsEnabled(context)
        val activity = context as? ComponentActivity
        // Negado de vez (o sistema não mostra mais o pedido): abre as configurações.
        if (!granted && activity != null && Build.VERSION.SDK_INT >= 33 &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            openNotificationSettings(context)
        }
    }
    val enableNotifications = {
        val permissionMissing = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (permissionMissing) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openNotificationSettings(context)
    }

    // Verificação automática (silenciosa) de nova versão ao abrir o app.
    var newRelease by remember { mutableStateOf<Updater.Release?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val latest = runCatching { Updater.fetchLatest() }.getOrNull()
        if (latest != null && latest.versionCode > Updater.currentVersionCode(context)) newRelease = latest
    }

    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Medication?>(null) }

    val dateText = today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR")))
        .replaceFirstChar { it.uppercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Saúde+", fontWeight = FontWeight.Bold)
                        Text(dateText, fontSize = 14.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { showUpdate = true }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Verificar atualização")
                    }
                    IconButton(onClick = {
                        if (!Reminders.notificationsEnabled(context)) {
                            enableNotifications()
                        } else {
                            val sample = sorted.firstOrNull()
                                ?: Medication(0, "Seu medicamento", "", now.hour, now.minute)
                            Reminders.showNotification(context, sample, test = true)
                            Toast.makeText(context, "Notificação de teste enviada", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Testar notificação")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Teal,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Adicionar") },
                containerColor = Teal,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!notifOk) {
                WarningBanner(
                    text = "As notificações estão desativadas. Sem elas você não recebe o aviso na hora de tomar.",
                    button = "Ativar",
                    onClick = enableNotifications,
                )
            } else if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                WarningBanner(
                    text = "Permita \"Alarmes e lembretes\" para o aviso chegar no horário certo.",
                    button = "Permitir",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        )
                    },
                )
            }
            newRelease?.let { rel ->
                WarningBanner(
                    text = "Nova versão ${rel.versionName} disponível.",
                    button = "Atualizar",
                    onClick = { showUpdate = true },
                    color = Teal,
                    background = Color(0xFFE0F2F1),
                )
            }
            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum medicamento ainda.\nToque em \"Adicionar\" para começar.",
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        color = Color.Gray,
                    )
                }
                return@Column
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    if (doneCount == sorted.size) "Tudo tomado hoje! 🎉"
                    else "$doneCount de ${sorted.size} tomados hoje",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { doneCount.toFloat() / sorted.size },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Green,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sorted, key = { it.id }) { med ->
                    val takenAt = taken[MedStore.key(med.id, today)]
                    val late = takenAt == null && now.toLocalTime() > LocalTime.of(med.hour, med.minute)
                    MedCard(
                        med = med,
                        takenAt = takenAt,
                        late = late,
                        onToggle = {
                            val nowTaken = takenAt == null
                            MedStore.setTaken(med.id, nowTaken, today)
                            if (nowTaken) {
                                androidx.core.app.NotificationManagerCompat.from(context).cancel(med.id)
                            }
                        },
                        onDelete = { toDelete = med },
                    )
                }
            }
        }
    }

    if (showUpdate) {
        UpdateDialog(onDismiss = { showUpdate = false })
    }

    if (showAdd) {
        AddMedDialog(
            onDismiss = { showAdd = false },
            onSave = { name, dose, h, m ->
                val med = MedStore.add(name, dose, h, m)
                Reminders.schedule(context, med)
                showAdd = false
            },
        )
    }

    toDelete?.let { med ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Excluir ${med.name}?") },
            text = { Text("O medicamento e seus lembretes serão removidos.") },
            confirmButton = {
                TextButton(onClick = {
                    Reminders.cancel(context, med.id)
                    MedStore.remove(med.id)
                    toDelete = null
                }) { Text("Excluir", color = LateRed) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun WarningBanner(
    text: String,
    button: String,
    onClick: () -> Unit,
    color: Color = Color(0xFFE65100),
    background: Color = Color(0xFFFFF3E0),
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = color)
            Spacer(Modifier.width(10.dp))
            Text(text, Modifier.weight(1f), fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = color),
            ) { Text(button) }
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun MedCard(
    med: Medication,
    takenAt: Long?,
    late: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val isTaken = takenAt != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTaken) Color(0xFFE8F5E9) else Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    med.timeText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (late) LateRed else Teal,
                )
                Text(med.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (med.dose.isNotBlank()) Text(med.dose, color = Color.Gray)
                when {
                    isTaken -> Text("Tomado às ${formatTime(takenAt!!)}", color = Green, fontSize = 13.sp)
                    late -> Text("Atrasado", color = LateRed, fontSize = 13.sp)
                }
            }
            if (isTaken) {
                OutlinedButton(onClick = onToggle) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Green)
                    Spacer(Modifier.width(6.dp))
                    Text("Tomado", color = Green)
                }
            } else {
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Tomei", fontSize = 16.sp)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = Color.Gray, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun AddMedDialog(onDismiss: () -> Unit, onSave: (String, String, Int, Int) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    val nowTime = remember { LocalTime.now() }
    var hour by remember { mutableStateOf(nowTime.hour) }
    var minute by remember { mutableStateOf(nowTime.minute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo medicamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text("Dose (ex.: 1 comprimido)") },
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Horário: %02d:%02d".format(hour, minute), fontSize = 16.sp)
                }
                Text(
                    "Toma mais de uma vez ao dia? Adicione um item para cada horário.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, dose, hour, minute) },
                enabled = name.isNotBlank(),
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))

private sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    var checkKey by remember { mutableStateOf(0) }

    LaunchedEffect(checkKey) {
        state = UpdateState.Checking
        state = try {
            val latest = Updater.fetchLatest()
            if (latest != null && latest.versionCode > Updater.currentVersionCode(context)) {
                UpdateState.Available(latest)
            } else {
                UpdateState.UpToDate
            }
        } catch (e: Exception) {
            UpdateState.Failed("Não foi possível verificar. Confira a internet e tente de novo.")
        }
    }

    fun downloadAndInstall(release: Updater.Release) {
        if (!Updater.canInstall(context)) {
            Toast.makeText(
                context,
                "Permita \"Instalar apps\" para o Saúde+ e toque em Atualizar de novo",
                Toast.LENGTH_LONG,
            ).show()
            Updater.openInstallPermissionSettings(context)
            return
        }
        scope.launch {
            state = UpdateState.Downloading(0f)
            try {
                val apk = Updater.download(context, release.apkUrl) { p -> state = UpdateState.Downloading(p) }
                state = UpdateState.Installing
                Updater.install(context, apk)
            } catch (e: Exception) {
                state = UpdateState.Failed("Falha ao baixar a atualização. Tente de novo.")
            }
        }
    }

    val busy = state is UpdateState.Downloading
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Atualização") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Versão instalada: ${Updater.currentVersionName(context)}", color = Color.Gray, fontSize = 14.sp)
                when (val st = state) {
                    UpdateState.Checking -> {
                        Text("Verificando…")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    UpdateState.UpToDate -> Text("Você já está com a versão mais recente. ✓", color = Green)
                    is UpdateState.Available -> {
                        Text("Nova versão ${st.release.versionName} disponível!", fontWeight = FontWeight.SemiBold)
                        val notes = st.release.notes.lines().firstOrNull { it.isNotBlank() }
                        if (notes != null) Text(notes, fontSize = 14.sp, color = Color.Gray)
                    }
                    is UpdateState.Downloading -> {
                        Text("Baixando…")
                        if (st.progress >= 0f) {
                            LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth(), color = Green)
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
                        }
                    }
                    UpdateState.Installing -> Text("Confirme a instalação na tela do Android.")
                    is UpdateState.Failed -> Text(st.message, color = LateRed)
                }
            }
        },
        confirmButton = {
            when (val st = state) {
                is UpdateState.Available -> TextButton(onClick = { downloadAndInstall(st.release) }) {
                    Text("Baixar e atualizar")
                }
                is UpdateState.Failed -> TextButton(onClick = { checkKey++ }) { Text("Tentar de novo") }
                else -> TextButton(onClick = onDismiss, enabled = !busy) { Text("OK") }
            }
        },
        dismissButton = {
            if (state is UpdateState.Available || state is UpdateState.Failed) {
                TextButton(onClick = onDismiss) { Text("Agora não") }
            }
        },
    )
}
