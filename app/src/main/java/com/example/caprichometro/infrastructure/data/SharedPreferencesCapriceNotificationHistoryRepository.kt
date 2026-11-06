package com.example.caprichometro.infrastructure.data

import android.content.Context
import android.content.SharedPreferences
import com.example.caprichometro.domain.repository.CapriceNotificationHistoryRepository

class SharedPreferencesCapriceNotificationHistoryRepository(
    context: Context
) : CapriceNotificationHistoryRepository {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun getLastNotifiedCapriceId(recipientUserId: String, friendUserId: String): String? {
        if (recipientUserId.isBlank() || friendUserId.isBlank()) return null
        return prefs.getString(keyId(recipientUserId, friendUserId), null)
    }

    override fun getLastNotifiedTimestamp(recipientUserId: String, friendUserId: String): Long {
        if (recipientUserId.isBlank() || friendUserId.isBlank()) return 0L
        return prefs.getLong(keyTimestamp(recipientUserId, friendUserId), 0L)
    }

    override fun setLastNotified(
        recipientUserId: String,
        friendUserId: String,
        capriceId: String,
        timestamp: Long
    ) {
        if (recipientUserId.isBlank() || friendUserId.isBlank()) return
        prefs.edit()
            .putString(keyId(recipientUserId, friendUserId), capriceId)
            .putLong(keyTimestamp(recipientUserId, friendUserId), timestamp)
            .apply()
    }

    private fun keyId(recipientUserId: String, friendUserId: String): String {
        return "notif_caprice_id_${recipientUserId}_${friendUserId}"
    }

    private fun keyTimestamp(recipientUserId: String, friendUserId: String): String {
        return "notif_timestamp_${recipientUserId}_${friendUserId}"
    }

    companion object {
        private const val PREFS_NAME = "caprice_notification_history"
    }
}
