package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.FlightLog
import com.example.data.model.PilotProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FlightRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePilotProfile(userId: String): Flow<PilotProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            PilotProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeFlightLogs(userId: String): Flow<List<FlightLog>> = flow {
        val colRef = db.collection("users").document(userId).collection("flight_logs")
        val query = colRef.orderBy("createdAt", Query.Direction.DESCENDING).limit(40)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FlightLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun ensurePilotProfile(defaultCallsign: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeCallsign = defaultCallsign.trim().ifBlank { "صقر-1" }.take(40)
                val payload = mapOf(
                    "userId" to uid,
                    "callsign" to safeCallsign,
                    "totalFlights" to 0L,
                    "successfulLandings" to 0L,
                    "highestScore" to 0L,
                    "highestUnlockedTier" to 1L,
                    "tiltControlEnabled" to false,
                    "soundEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}", auth)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        callsign: String? = null,
        tiltControlEnabled: Boolean? = null,
        soundEnabled: Boolean? = null,
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            ensurePilotProfile(callsign ?: "صقر-1").getOrThrow()
            val docRef = db.collection("users").document(uid)
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (callsign != null) {
                updates["callsign"] = callsign.trim().ifBlank { "صقر-1" }.take(40)
            }
            if (tiltControlEnabled != null) {
                updates["tiltControlEnabled"] = tiltControlEnabled
            }
            if (soundEnabled != null) {
                updates["soundEnabled"] = soundEnabled
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}", auth)
            Result.failure(e)
        }
    }

    suspend fun recordFlightOutcome(
        log: FlightLog,
        unlockedTierCandidate: Int,
        defaultCallsign: String = "صقر-1",
    ): Result<String> {
        return try {
            val uid = requireUserId()
            ensurePilotProfile(defaultCallsign).getOrThrow()

            val userRef = db.collection("users").document(uid)
            val logRef = userRef.collection("flight_logs").document()

            val logPayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "missionId" to log.missionId,
                "missionName" to log.missionName.take(80),
                "success" to log.success,
                "score" to log.score.coerceIn(0L, 100000L),
                "ratingStars" to log.ratingStars.coerceIn(0L, 3L),
                "touchdownVerticalSpeed" to log.touchdownVerticalSpeed.coerceIn(0.0, 5000.0),
                "touchdownHorizontalSpeed" to log.touchdownHorizontalSpeed.coerceIn(0.0, 5000.0),
                "touchdownTiltDeg" to log.touchdownTiltDeg.coerceIn(0.0, 180.0),
                "fuelRemainingPercent" to log.fuelRemainingPercent.coerceIn(0.0, 100.0),
                "flightDurationSec" to log.flightDurationSec.coerceIn(0.0, 86400.0),
                "createdAt" to FieldValue.serverTimestamp(),
            )
            val reason = log.failureReason?.trim()?.take(120)
            if (!reason.isNullOrEmpty()) {
                logPayload["failureReason"] = reason
            }

            logRef.set(logPayload).await()

            val snapshot = userRef.get().await()
            val prevTotal = snapshot.getLong("totalFlights") ?: 0L
            val prevWins = snapshot.getLong("successfulLandings") ?: 0L
            val prevBest = snapshot.getLong("highestScore") ?: 0L
            val prevTier = snapshot.getLong("highestUnlockedTier") ?: 1L

            val nextTotal = prevTotal + 1L
            val nextWins = if (log.success) prevWins + 1L else prevWins
            val nextBest = maxOf(prevBest, log.score.coerceIn(0L, 100000L))
            val nextTier = if (log.success) {
                maxOf(prevTier, unlockedTierCandidate.toLong()).coerceIn(1L, 4L)
            } else {
                prevTier.coerceIn(1L, 4L)
            }

            userRef.update(
                mapOf(
                    "totalFlights" to nextTotal,
                    "successfulLandings" to nextWins,
                    "highestScore" to nextBest,
                    "highestUnlockedTier" to nextTier,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            Result.success(logRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/flight_logs", auth)
            Result.failure(e)
        }
    }

    suspend fun deleteFlightLog(logId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(logId.isNotBlank()) { "Flight log ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("flight_logs").document(logId)
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/flight_logs/$logId", auth)
            Result.failure(e)
        }
    }
}
