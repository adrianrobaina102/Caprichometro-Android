package com.example.caprichometro.infrastructure.ui.app

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.UserProfile

class SessionNavigationViewModel : ViewModel() {
    var bootstrappedUserId: String? = null
    var selectedFeedItem by mutableStateOf<CapriceFeedItem?>(null)
    var selectedProfileCaprice by mutableStateOf<Caprice?>(null)
    var selectedPublicAuthor by mutableStateOf<UserProfile?>(null)
    var pendingIntent: Intent? = null

    fun reset() {
        bootstrappedUserId = null
        selectedFeedItem = null
        selectedProfileCaprice = null
        selectedPublicAuthor = null
    }
}
