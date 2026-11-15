package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FlightLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val missionId: String = "moon_tranquility",
    val missionName: String = "",
    val success: Boolean = false,
    val score: Long = 0L,
    val ratingStars: Long = 0L,
    val touchdownVerticalSpeed: Double = 0.0,
    val touchdownHorizontalSpeed: Double = 0.0,
    val touchdownTiltDeg: Double = 0.0,
    val fuelRemainingPercent: Double = 0.0,
    val flightDurationSec: Double = 0.0,
    val failureReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
)
