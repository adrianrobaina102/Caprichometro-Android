package com.example.caprichometro.infrastructure.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.caprichometro.domain.model.Caprice

object PollNotificationScheduler {
    const val POLL_DURATION_MILLIS = 5 * 60 * 1000L // 5 minutos acordados para cada duda

    fun calculateTriggerTime(timestamp: Long): Long {
        return timestamp + POLL_DURATION_MILLIS
    }

    fun isPollExpired(timestamp: Long, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return (calculateTriggerTime(timestamp) - currentTimeMillis) <= 0
    }

    fun getPollRequestCode(caprice: Caprice): Int {
        return (caprice.timestamp xor caprice.question.hashCode().toLong()).toInt()
    }

    fun schedulePollExpiration(context: Context, caprice: Caprice) {
        val triggerAtMillis = calculateTriggerTime(caprice.timestamp)
        val remainingMillis = triggerAtMillis - System.currentTimeMillis()

        if (remainingMillis <= 0) {
            PollNotificationHelper.showPollClosedNotification(context, caprice.question, caprice.id)
            return
        }

        val intent = Intent(context, PollExpiredReceiver::class.java).apply {
            putExtra(PollNotificationHelper.EXTRA_CAPRICE_QUESTION, caprice.question)
            putExtra(PollNotificationHelper.EXTRA_CAPRICE_ID, caprice.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getPollRequestCode(caprice),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // Manejo de seguridad en caso de restricciones de alarmas en tiempo de ejecución
        }
    }

    fun cancelPollExpiration(context: Context, capriceId: String, timestamp: Long = 0L) {
        val intent = Intent(context, PollExpiredReceiver::class.java)
        val requestCode = if (capriceId.isNotBlank()) capriceId.hashCode() else timestamp.toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
