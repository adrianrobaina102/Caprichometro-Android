package com.example.caprichometro.infrastructure.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PollExpiredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val question = intent.getStringExtra(PollNotificationHelper.EXTRA_CAPRICE_QUESTION) ?: ""
        val capriceId = intent.getStringExtra(PollNotificationHelper.EXTRA_CAPRICE_ID) ?: ""
        PollNotificationHelper.showPollClosedNotification(context, question, capriceId)
    }
}
