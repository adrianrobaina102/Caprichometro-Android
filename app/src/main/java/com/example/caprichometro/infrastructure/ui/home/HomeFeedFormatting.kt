package com.example.caprichometro.infrastructure.ui.home

import android.content.Context
import com.example.caprichometro.R
import com.example.caprichometro.domain.model.PollTiming
import kotlin.math.roundToInt
internal fun localizedCapriceType(context: Context, rawType: String): String = when {
    rawType.contains("compra", ignoreCase = true) -> context.getString(R.string.type_compra)
    rawType.contains("plan", ignoreCase = true) -> context.getString(R.string.type_plan)
    rawType.contains("ropa", ignoreCase = true) -> context.getString(R.string.category_ropa)
    rawType.isBlank() -> context.getString(R.string.category_otros)
    else -> rawType
}

internal fun formatLocalizedDistanceMeters(context: Context, distanceMeters: Double): String {
    if (!distanceMeters.isFinite() || distanceMeters < 0.0 || distanceMeters < 1_000.0) {
        return context.getString(R.string.distance_your_zone)
    }
    val km = (distanceMeters / 1_000.0).roundToInt().coerceAtLeast(1)
    return context.getString(R.string.distance_approx_km, km)
}

internal fun formatRelativeTime(context: Context, timestampMillis: Long, nowMillis: Long): String {
    val diffMillis = (nowMillis - timestampMillis).coerceAtLeast(0L)
    val minutes = diffMillis / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L

    return when {
        minutes < 1L -> context.getString(R.string.feed_time_now)
        minutes < 60L -> context.getString(R.string.feed_time_minutes, minutes)
        hours < 24L -> context.getString(R.string.feed_time_hours, hours)
        else -> context.getString(R.string.feed_time_days, days)
    }
}

internal fun formatPollStatus(context: Context, timestampMillis: Long, nowMillis: Long): String {
    val closeTimestamp = PollTiming.calculateTriggerTime(timestampMillis)
    val remainingMillis = closeTimestamp - nowMillis

    return if (remainingMillis > 0) {
        val remainingMin = kotlin.math.ceil(remainingMillis / 60_000.0).toLong().coerceAtLeast(1L)
        if (remainingMin == 1L) {
            context.getString(R.string.poll_status_remaining_one)
        } else {
            context.getString(R.string.poll_status_remaining_many, remainingMin)
        }
    } else {
        val closedSinceMillis = (nowMillis - closeTimestamp).coerceAtLeast(0L)
        val closedMinutes = closedSinceMillis / 60_000L
        val closedHours = closedMinutes / 60L
        val closedDays = closedHours / 24L

        when {
            closedMinutes < 1L -> context.getString(R.string.poll_status_closed_now)
            closedMinutes < 60L -> context.getString(R.string.poll_status_closed_minutes, closedMinutes)
            closedHours < 24L -> context.getString(R.string.poll_status_closed_hours, closedHours)
            else -> context.getString(R.string.poll_status_closed_days, closedDays)
        }
    }
}

internal fun formatRelativeTime(timestampMillis: Long, nowMillis: Long): String {
    val diffMillis = (nowMillis - timestampMillis).coerceAtLeast(0L)
    val minutes = diffMillis / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L

    return when {
        minutes < 1L -> "Ahora"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        else -> "${days}d"
    }
}

internal fun formatPollStatus(timestampMillis: Long, nowMillis: Long): String {
    val closeTimestamp = PollTiming.calculateTriggerTime(timestampMillis)
    val remainingMillis = closeTimestamp - nowMillis

    return if (remainingMillis > 0) {
        val remainingMin = kotlin.math.ceil(remainingMillis / 60_000.0).toLong().coerceAtLeast(1L)
        if (remainingMin == 1L) "Queda 1 min" else "Quedan $remainingMin min"
    } else {
        val closedSinceMillis = (nowMillis - closeTimestamp).coerceAtLeast(0L)
        val closedMinutes = closedSinceMillis / 60_000L
        val closedHours = closedMinutes / 60L
        val closedDays = closedHours / 24L

        when {
            closedMinutes < 1L -> "Cerró ahora"
            closedMinutes < 60L -> "Cerró hace ${closedMinutes} min"
            closedHours < 24L -> "Cerró hace ${closedHours}h"
            else -> "Cerró hace ${closedDays}d"
        }
    }
}
