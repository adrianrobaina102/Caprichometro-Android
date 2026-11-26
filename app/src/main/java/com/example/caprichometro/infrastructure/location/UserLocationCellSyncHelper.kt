package com.example.caprichometro.infrastructure.location

import com.example.caprichometro.domain.repository.UserProfileReadRepository
import com.example.caprichometro.domain.repository.UserProfileWriteRepository
import java.util.concurrent.ConcurrentHashMap

internal object UserLocationCellSyncHelper {
    suspend fun syncIfChanged(
        userProfileReadRepository: UserProfileReadRepository?,
        userProfileWriteRepository: UserProfileWriteRepository?,
        syncedLocationCells: ConcurrentHashMap<String, String>,
        uid: String,
        cellId: String
    ) {
        if (uid.isBlank() || cellId.isBlank()) return
        if (syncedLocationCells[uid] == cellId) return
        val writeRepo = userProfileWriteRepository ?: return
        val cachedCell = runCatching { userProfileReadRepository?.getCachedUserById(uid)?.locationCell }.getOrNull()
        if (cachedCell == cellId) {
            syncedLocationCells[uid] = cellId
            return
        }
        writeRepo.updateLocationCell(uid, cellId).onSuccess {
            syncedLocationCells[uid] = cellId
        }
    }
}
