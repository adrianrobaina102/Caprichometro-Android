package com.example.caprichometro.infrastructure.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.infrastructure.ui.MainActivity

object PollNotificationHelper {
    const val CHANNEL_ID = "caprice_poll_channel"
    const val CHANNEL_NAME = "Votaciones de Caprichos"
    const val NOTIFICATION_ID = 1001
    const val CREATED_NOTIFICATION_ID = 1002

    const val EXTRA_NAVIGATE_TO = "EXTRA_NAVIGATE_TO"
    const val EXTRA_CAPRICE_ID = "EXTRA_CAPRICE_ID"
    const val EXTRA_CAPRICE_QUESTION = "EXTRA_CAPRICE_QUESTION"
    const val EXTRA_RECIPIENT_USER_ID = "EXTRA_RECIPIENT_USER_ID"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de votaciones y caprichos de amigos en Caprichómetro"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showCapriceCreatedNotification(
        context: Context,
        capriceQuestion: String,
        capriceId: String = "",
        recipientUserId: String = ""
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, "active")
            putExtra(EXTRA_CAPRICE_ID, capriceId)
            putExtra(EXTRA_CAPRICE_QUESTION, capriceQuestion)
            putExtra(EXTRA_RECIPIENT_USER_ID, recipientUserId)
            if (capriceId.isNotBlank()) {
                data = Uri.parse("caprichometro://caprice/$capriceId")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            CREATED_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_caprichometro_logo)
            .setContentTitle("¡Duda en marcha! ⏳")
            .setContentText("Ya queda menos para saber si es un capricho o no.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (capriceQuestion.isNotBlank()) "Publicado: \"$capriceQuestion\". Ya queda menos para saber si es un capricho o no. ¡Tus amigos tienen 5 minutos!"
                    else "Tu duda está en marcha. Ya queda menos para saber si es un capricho o no. ¡Tus amigos tienen 5 minutos!"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                CREATED_NOTIFICATION_ID,
                notification
            )
        } catch (_: SecurityException) {
        }
    }

    fun showPollClosedNotification(
        context: Context,
        capriceQuestion: String,
        capriceId: String = "",
        recipientUserId: String = ""
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, "result")
            putExtra(EXTRA_CAPRICE_ID, capriceId)
            putExtra(EXTRA_CAPRICE_QUESTION, capriceQuestion)
            putExtra(EXTRA_RECIPIENT_USER_ID, recipientUserId)
            if (capriceId.isNotBlank()) {
                data = Uri.parse("caprichometro://caprice/$capriceId")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
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
                NOTIFICATION_ID,
                notification
            )
        } catch (_: SecurityException) {
        }
    }

    fun showFriendCapriceNotification(
        context: Context,
        recipientUserId: String,
        authorUsername: String,
        caprice: Caprice
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, "friend_poll")
            putExtra(EXTRA_CAPRICE_ID, caprice.id)
            putExtra(EXTRA_CAPRICE_QUESTION, caprice.question)
            putExtra(EXTRA_RECIPIENT_USER_ID, recipientUserId)
            if (caprice.id.isNotBlank()) {
                data = Uri.parse("caprichometro://caprice/${caprice.id}")
            }
        }

        val requestCode = if (caprice.id.isNotBlank()) caprice.id.hashCode() else System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "@$authorUsername ha subido un capricho"
        val body = if (caprice.question.isNotBlank()) "\"${caprice.question}\"" else "¡Entra a opinar y votar!"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_caprichometro_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$title:\n$body\n¡Tus 5 minutos para opinar acaban de empezar!"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                requestCode,
                notification
            )
        } catch (_: SecurityException) {
        }
    }
}
