package com.example.caprichometro.infrastructure.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.PollTiming

object PollNotificationScheduler {
    const val POLL_DURATION_MILLIS = PollTiming.POLL_DURATION_MILLIS

    fun calculateTriggerTime(timestamp: Long): Long =
        PollTiming.calculateTriggerTime(timestamp)

    fun isPollExpired(timestamp: Long, currentTimeMillis: Long = System.currentTimeMillis()): Boolean =
        PollTiming.isPollExpired(timestamp, currentTimeMillis)

    fun getPollRequestCode(caprice: Caprice): Int =
        getPollRequestCode(caprice.id, caprice.timestamp, caprice.question)

    fun getPollRequestCode(capriceId: String, timestamp: Long = 0L, question: String = ""): Int {
        return if (capriceId.isNotBlank()) {
            capriceId.hashCode()
        } else {
            (timestamp xor question.hashCode().toLong()).toInt()
        }
    }

    @SuppressLint("MissingPermission")
    fun schedulePollExpiration(context: Context, caprice: Caprice, recipientUserId: String = "") {
        val triggerAtMillis = calculateTriggerTime(caprice.timestamp)
        val remainingMillis = triggerAtMillis - System.currentTimeMillis()

        if (remainingMillis <= 0) {
            PollNotificationHelper.showPollClosedNotification(context, caprice.question, caprice.id, recipientUserId)
            return
        }

        val intent = Intent(context, PollExpiredReceiver::class.java).apply {
            putExtra(PollNotificationHelper.EXTRA_CAPRICE_QUESTION, caprice.question)
            putExtra(PollNotificationHelper.EXTRA_CAPRICE_ID, caprice.id)
            putExtra(PollNotificationHelper.EXTRA_RECIPIENT_USER_ID, recipientUserId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getPollRequestCode(caprice),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (_: Exception) {
            }
        }
    }

    fun cancelPollExpiration(context: Context, caprice: Caprice) {
        cancelPollExpiration(context, caprice.id, caprice.timestamp, caprice.question)
    }

    fun cancelPollExpiration(
        context: Context,
        capriceId: String,
        timestamp: Long = 0L,
        question: String = ""
    ) {
        val intent = Intent(context, PollExpiredReceiver::class.java)
        val requestCode = getPollRequestCode(capriceId, timestamp, question)
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
