package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.TwilightCheckEntity
import com.example.data.model.AppSettings
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

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Требуется вход через Google для работы с Firestore.")
    }

    fun observeUserSettings(userId: String): Flow<CloudUserSettings?> = flow {
        val uid = auth.currentUser?.uid
        require(userId.isNotBlank() && uid == userId) { "Несоответствие пользователя Firestore" }
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        CloudUserSettings::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeChecks(userId: String): Flow<List<CloudTwilightCheck>> = flow {
        val uid = auth.currentUser?.uid
        require(userId.isNotBlank() && uid == userId) { "Несоответствие пользователя Firestore" }
        val colRef = db.collection("users").document(userId).collection("twilightChecks")
        emitAll(
            colRef
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudTwilightCheck::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveSettings(
        userId: String,
        settings: AppSettings,
        displayName: String
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Отказ: несоответствие UID" }
            val docRef = db.collection("users").document(uid)

            val cleanDisplayName = displayName.trim().take(100)
            val cleanAlarmLabel = settings.alarmLabel.trim().ifEmpty { "Самые ранние сумерки" }.take(100)
            val cleanLocName = settings.locationName.trim().take(200)
            val lat = settings.latitude?.takeIf { it in -90.0..90.0 }
            val lon = settings.longitude?.takeIf { it in -180.0..180.0 }

            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val createPayload = mapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to cleanDisplayName,
                    "onboardingCompleted" to settings.onboardingCompleted,
                    "dailyMidnightEnabled" to settings.dailyMidnightEnabled,
                    "maxRetryAttempts" to settings.maxRetryAttempts.coerceIn(1, 30),
                    "retryDelaySeconds" to settings.retryDelaySeconds.coerceIn(1, 30),
                    "twilightPreference" to settings.twilightPreference.name,
                    "alarmOffsetMinutes" to settings.alarmOffsetMinutes.coerceIn(-30, 30),
                    "alarmLabel" to cleanAlarmLabel,
                    "locationMode" to settings.locationMode.name,
                    "latitude" to lat,
                    "longitude" to lon,
                    "locationName" to cleanLocName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf<String, Any?>(
                    "displayName" to cleanDisplayName,
                    "onboardingCompleted" to settings.onboardingCompleted,
                    "dailyMidnightEnabled" to settings.dailyMidnightEnabled,
                    "maxRetryAttempts" to settings.maxRetryAttempts.coerceIn(1, 30),
                    "retryDelaySeconds" to settings.retryDelaySeconds.coerceIn(1, 30),
                    "twilightPreference" to settings.twilightPreference.name,
                    "alarmOffsetMinutes" to settings.alarmOffsetMinutes.coerceIn(-30, 30),
                    "alarmLabel" to cleanAlarmLabel,
                    "locationMode" to settings.locationMode.name,
                    "latitude" to lat,
                    "longitude" to lon,
                    "locationName" to cleanLocName,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addCheckLog(userId: String, entity: TwilightCheckEntity): Result<Unit> {
        var docPath = "users/$userId/twilightChecks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Отказ: несоответствие UID" }
            val docRef = db.collection("users").document(uid)
                .collection("twilightChecks").document()
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "dateLabel" to entity.dateLabel.ifBlank { "-" }.take(32),
                "locationName" to entity.locationName.take(200),
                "latitude" to entity.latitude?.takeIf { it in -90.0..90.0 },
                "longitude" to entity.longitude?.takeIf { it in -180.0..180.0 },
                "attemptsUsed" to entity.attemptsUsed.coerceIn(0, 30),
                "maxAttempts" to entity.maxAttempts.coerceIn(1, 30),
                "succeeded" to entity.success,
                "alarmSet" to entity.alarmSet,
                "scheduledMidnight" to entity.isScheduledMidnight,
                "twilightTime" to entity.twilightTime?.take(16),
                "twilightStage" to entity.twilightStage?.take(64),
                "astronomicalTime" to entity.astronomicalTime?.take(16),
                "nauticalTime" to entity.nauticalTime?.take(16),
                "civilTime" to entity.civilTime?.take(16),
                "sunriseTime" to entity.sunriseTime?.take(16),
                "message" to entity.message.take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearCheckLogs(userId: String): Result<Unit> {
        val colPath = "users/$userId/twilightChecks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Отказ: несоответствие UID" }
            val colRef = db.collection("users").document(uid).collection("twilightChecks")
            val snapshots = colRef.get().await()
            if (!snapshots.isEmpty) {
                val batch = db.batch()
                snapshots.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colPath)
            Result.failure(e)
        }
    }
}
