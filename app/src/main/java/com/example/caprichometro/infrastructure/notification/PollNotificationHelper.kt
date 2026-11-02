package com.example.caprichometro.infrastructure.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.caprichometro.R
import com.example.caprichometro.infrastructure.ui.MainActivity

object PollNotificationHelper {
    const val CHANNEL_ID = "caprice_poll_channel"
    const val CHANNEL_NAME = "Votaciones de Caprichos"
    const val NOTIFICATION_ID = 1001

    const val EXTRA_NAVIGATE_TO = "EXTRA_NAVIGATE_TO"
    const val EXTRA_CAPRICE_ID = "EXTRA_CAPRICE_ID"
    const val EXTRA_CAPRICE_QUESTION = "EXTRA_CAPRICE_QUESTION"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de votaciones finalizadas en Caprichómetro"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showPollClosedNotification(
        context: Context,
        capriceQuestion: String,
        capriceId: String = ""
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, "result")
            putExtra(EXTRA_CAPRICE_ID, capriceId)
            putExtra(EXTRA_CAPRICE_QUESTION, capriceQuestion)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            if (capriceId.isNotBlank()) capriceId.hashCode() else NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_caprichometro_logo)
            .setContentTitle("¡Votación cerrada!")
            .setContentText(
                if (capriceQuestion.isNotBlank()) "Tus amigos ya han opinado: \"$capriceQuestion\""
                else "Tus amigos han terminado de votar. ¡Descubre el veredicto!"
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (capriceQuestion.isNotBlank()) "Tus amigos ya han opinado sobre: \"$capriceQuestion\". Entra a ver el veredicto final."
                    else "Tus amigos han terminado de votar tu capricho. Entra a ver el veredicto final."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                if (capriceId.isNotBlank()) capriceId.hashCode() else NOTIFICATION_ID,
                notification
            )
        } catch (_: SecurityException) {
            // Manejo seguro ante restricciones de permisos en Android 13+
        }
    }
}
