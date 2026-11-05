package com.saudeplus.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Agenda e exibe os lembretes diários de cada medicamento. */
object Reminders {
    const val CHANNEL_ID = "lembretes"
    const val ACTION_REMIND = "com.saudeplus.app.REMIND"
    const val ACTION_TAKEN = "com.saudeplus.app.TAKEN"
    const val EXTRA_MED_ID = "med_id"
    private const val TEST_ID = 999_999

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lembretes de medicamentos",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Avisa na hora de tomar cada medicamento" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun remindIntent(context: Context, medId: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_MED_ID, medId)
        return PendingIntent.getBroadcast(
            context, medId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** true se o app pode agendar alarmes no horário exato. */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** true se as notificações do app estão liberadas (permissão + configurações do sistema). */
    fun notificationsEnabled(context: Context): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Agenda o próximo lembrete no horário exato: hoje, se o horário ainda não passou
     * (ou é o minuto atual); senão amanhã.
     */
    fun schedule(context: Context, med: Medication) {
        val now = LocalDateTime.now()
        val today = LocalDateTime.of(LocalDate.now(), LocalTime.of(med.hour, med.minute))
        val next = when {
            today.isAfter(now) -> today
            // Horário no minuto atual: avisa já (ex.: cadastrou para "agora").
            !today.plusMinutes(1).isBefore(now) -> now.plusSeconds(2)
            else -> today.plusDays(1)
        }
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = remindIntent(context, med.id)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    /** Agenda o lembrete seguinte, sempre para o próximo dia (usado após disparar). */
    private fun scheduleTomorrow(context: Context, med: Medication) {
        val next = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(med.hour, med.minute))
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = remindIntent(context, med.id)
        try {
            if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    internal fun onAlarmFired(context: Context, med: Medication) = scheduleTomorrow(context, med)

    fun cancel(context: Context, medId: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(remindIntent(context, medId))
        NotificationManagerCompat.from(context).cancel(medId)
    }

    fun scheduleAll(context: Context) {
        MedStore.meds.value.forEach { schedule(context, it) }
    }

    fun showNotification(context: Context, med: Medication, test: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)

        val openApp = PendingIntent.getActivity(
            context, med.id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val takenAction = PendingIntent.getBroadcast(
            context, 100_000 + med.id,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_TAKEN)
                .putExtra(EXTRA_MED_ID, med.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = buildString {
            append("Hora de tomar")
            if (med.dose.isNotBlank()) append(" ${med.dose}")
            append(" (${med.timeText})")
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(if (test) "Teste: ${med.name}" else med.name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
        // No teste o botão "Tomei" não aparece, para não marcar o remédio sem querer.
        if (!test) builder.addAction(0, "✓ Tomei", takenAction)
        NotificationManagerCompat.from(context).notify(if (test) TEST_ID else med.id, builder.build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedStore.init(context)
        val medId = intent.getIntExtra(Reminders.EXTRA_MED_ID, -1)
        val med = MedStore.find(medId) ?: return
        when (intent.action) {
            Reminders.ACTION_REMIND -> {
                if (MedStore.takenAt(med.id) == null) {
                    Reminders.createChannel(context)
                    Reminders.showNotification(context, med)
                }
                Reminders.onAlarmFired(context, med) // próximo dia
            }
            Reminders.ACTION_TAKEN -> {
                MedStore.setTaken(med.id, true)
                NotificationManagerCompat.from(context).cancel(med.id)
            }
        }
    }
}

/** Reagenda tudo após reiniciar, atualizar o app, mudar a hora ou liberar alarmes exatos. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedStore.init(context)
        Reminders.scheduleAll(context)
    }
}
