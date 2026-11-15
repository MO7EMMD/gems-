package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class PilotProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val callsign: String = "صقر-1",
    val totalFlights: Long = 0L,
    val successfulLandings: Long = 0L,
    val highestScore: Long = 0L,
    val highestUnlockedTier: Long = 1L,
    val tiltControlEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
