package com.example.caprichometro.infrastructure.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.infrastructure.ui.app.MainActivity

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
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            enableVibration(true)
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.createNotificationChannel(channel)
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
                data = "caprichometro://caprice/$capriceId".toUri()
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
            .setContentTitle(context.getString(R.string.notif_created_title))
            .setContentText(context.getString(R.string.notif_created_short))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (capriceQuestion.isNotBlank()) context.getString(R.string.notif_created_big_with_q, capriceQuestion)
                    else context.getString(R.string.notif_created_big_no_q)
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
                data = "caprichometro://caprice/$capriceId".toUri()
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
            .setContentTitle(context.getString(R.string.notif_closed_title))
            .setContentText(
                if (capriceQuestion.isNotBlank()) context.getString(R.string.notif_closed_short_with_q, capriceQuestion)
                else context.getString(R.string.notif_closed_short_no_q)
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (capriceQuestion.isNotBlank()) context.getString(R.string.notif_closed_big_with_q, capriceQuestion)
                    else context.getString(R.string.notif_closed_big_no_q)
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
                data = "caprichometro://caprice/${caprice.id}".toUri()
            }
        }

        val requestCode = if (caprice.id.isNotBlank()) caprice.id.hashCode() else System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notif_friend_caprice_title, authorUsername)
        val body = if (caprice.question.isNotBlank()) "\"${caprice.question}\"" else context.getString(R.string.notif_friend_caprice_fallback)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_caprichometro_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.notif_friend_caprice_big, title, body)
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

    fun showFriendFinalDecisionNotification(
        context: Context,
        recipientUserId: String,
        authorUsername: String,
        caprice: Caprice
    ) {
        val chosenOption = when (caprice.finalDecision) {
            1 -> caprice.answer1
            2 -> caprice.answer2
            else -> return
        }
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, "result")
            putExtra(EXTRA_CAPRICE_ID, caprice.id)
            putExtra(EXTRA_CAPRICE_QUESTION, caprice.question)
            putExtra(EXTRA_RECIPIENT_USER_ID, recipientUserId)
            if (caprice.id.isNotBlank()) {
                data = "caprichometro://caprice/${caprice.id}".toUri()
            }
        }

        val requestCode = "decision_${caprice.id}".hashCode()

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notif_friend_decision_title, authorUsername)
        val body = context.getString(R.string.notif_friend_decision_body, chosenOption)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_caprichometro_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (caprice.question.isNotBlank()) "\"${caprice.question}\"\n$body" else body
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
