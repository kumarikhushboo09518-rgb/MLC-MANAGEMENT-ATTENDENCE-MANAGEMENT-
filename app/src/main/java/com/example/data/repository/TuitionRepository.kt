package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.logic.AttendanceFineCalculator
import com.example.data.logic.DateUtils
import com.example.data.model.AppSettings
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.FineRecord
import com.example.data.model.FineStatus
import com.example.data.model.MlcProfile
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.data.model.UserAccount
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class BackupPayload(
    val mlcProfile: MlcProfile?,
    val appSettings: AppSettings?,
    val students: List<Student>,
    val attendanceRecords: List<AttendanceRecord>,
    val fineRecords: List<FineRecord>,
    val paymentRecords: List<PaymentRecord>
)

interface TuitionRepository {
    fun observeUserAccount(userId: String): Flow<UserAccount?>
    fun observeMlcProfile(userId: String): Flow<MlcProfile?>
    fun observeAppSettings(userId: String): Flow<AppSettings?>
    fun observeStudents(userId: String): Flow<List<Student>>
    fun observeAttendanceRecords(userId: String): Flow<List<AttendanceRecord>>
    fun observeFineRecords(userId: String): Flow<List<FineRecord>>
    fun observePaymentRecords(userId: String): Flow<List<PaymentRecord>>

    suspend fun registerTeacherAccount(
        userId: String,
        username: String,
        passwordHash: String,
        passwordSalt: String,
        recoveryHash: String,
        recoverySalt: String,
        rememberMe: Boolean
    ): Result<Unit>

    suspend fun updateRememberMe(userId: String, rememberMe: Boolean): Result<Unit>

    suspend fun updatePassword(
        userId: String,
        passwordHash: String,
        passwordSalt: String
    ): Result<Unit>

    suspend fun saveMlcProfile(
        userId: String,
        teacherName: String,
        mlcName: String,
        mlcLocation: String,
        logoData: String?,
        existingProfile: MlcProfile?
    ): Result<Unit>

    suspend fun saveAppSettings(
        userId: String,
        languageCode: String,
        defaultFineAmount: Long,
        sundayHolidayEnabled: Boolean,
        notificationsEnabled: Boolean,
        existingSettings: AppSettings?
    ): Result<Unit>

    suspend fun addStudent(
        userId: String,
        studentCode: String,
        fullName: String,
        parentName: String?,
        contactNumber: String?
    ): Result<Unit>

    suspend fun updateStudent(
        userId: String,
        studentId: String,
        fullName: String,
        parentName: String?,
        contactNumber: String?
    ): Result<Unit>

    suspend fun deleteStudent(userId: String, studentId: String): Result<Unit>

    suspend fun saveDailyAttendanceBatch(
        userId: String,
        dateKey: String,
        students: List<Student>,
        statuses: Map<String, AttendanceStatus>,
        existingAttendance: List<AttendanceRecord>,
        existingFines: List<FineRecord>,
        defaultFineAmount: Long
    ): Result<Unit>

    suspend fun collectFinePayment(
        userId: String,
        student: Student,
        amountPaid: Long,
        currentRemainingFine: Long,
        openFinesForStudent: List<FineRecord>,
        note: String?
    ): Result<Unit>

    suspend fun restoreBackup(
        userId: String,
        backup: BackupPayload,
        existingProfile: MlcProfile?,
        existingSettings: AppSettings?,
        existingStudents: List<Student>,
        existingAttendance: List<AttendanceRecord>,
        existingFines: List<FineRecord>,
        existingPayments: List<PaymentRecord>
    ): Result<Unit>
}

class FirestoreTuitionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : TuitionRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user write." }
        return uid
    }

    private fun <T> Flow<T>.handleObserveError(userId: String, path: String, op: OperationType): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                handleFirestoreError(exceptionToLog, op, path)
            }
            throw error
        }

    override fun observeUserAccount(userId: String): Flow<UserAccount?> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .handleObserveError(userId, path, OperationType.GET)
    }

    override fun observeMlcProfile(userId: String): Flow<MlcProfile?> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/mlcProfiles/default"
        return db.collection("users").document(userId)
            .collection("mlcProfiles").document("default")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(MlcProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .handleObserveError(userId, path, OperationType.GET)
    }

    override fun observeAppSettings(userId: String): Flow<AppSettings?> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/appSettings/preferences"
        return db.collection("users").document(userId)
            .collection("appSettings").document("preferences")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(AppSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .handleObserveError(userId, path, OperationType.GET)
    }

    override fun observeStudents(userId: String): Flow<List<Student>> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/students"
        return db.collection("users").document(userId).collection("students")
            .snapshots()
            .map { it.toObjects(Student::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .handleObserveError(userId, path, OperationType.LIST)
    }

    override fun observeAttendanceRecords(userId: String): Flow<List<AttendanceRecord>> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/attendanceRecords"
        return db.collection("users").document(userId).collection("attendanceRecords")
            .snapshots()
            .map { it.toObjects(AttendanceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .handleObserveError(userId, path, OperationType.LIST)
    }

    override fun observeFineRecords(userId: String): Flow<List<FineRecord>> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/fineRecords"
        return db.collection("users").document(userId).collection("fineRecords")
            .snapshots()
            .map { it.toObjects(FineRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .handleObserveError(userId, path, OperationType.LIST)
    }

    override fun observePaymentRecords(userId: String): Flow<List<PaymentRecord>> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/paymentRecords"
        return db.collection("users").document(userId).collection("paymentRecords")
            .snapshots()
            .map { it.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .handleObserveError(userId, path, OperationType.LIST)
    }

    override suspend fun registerTeacherAccount(
        userId: String,
        username: String,
        passwordHash: String,
        passwordSalt: String,
        recoveryHash: String,
        recoverySalt: String,
        rememberMe: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val cleanUser = username.trim()
            require(cleanUser.length in 2..50) { "Username must be 2 to 50 characters" }
            val payload = mapOf(
                "userId" to uid,
                "username" to cleanUser,
                "passwordHash" to passwordHash,
                "passwordSalt" to passwordSalt,
                "recoveryHash" to recoveryHash,
                "recoverySalt" to recoverySalt,
                "rememberMe" to rememberMe,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateRememberMe(userId: String, rememberMe: Boolean): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users").document(uid).update(
                mapOf(
                    "rememberMe" to rememberMe,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updatePassword(
        userId: String,
        passwordHash: String,
        passwordSalt: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users").document(uid).update(
                mapOf(
                    "passwordHash" to passwordHash,
                    "passwordSalt" to passwordSalt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun saveMlcProfile(
        userId: String,
        teacherName: String,
        mlcName: String,
        mlcLocation: String,
        logoData: String?,
        existingProfile: MlcProfile?
    ): Result<Unit> {
        val path = "users/$userId/mlcProfiles/default"
        return try {
            val uid = requireUserId(userId)
            val tName = teacherName.trim().take(100)
            val mName = mlcName.trim().take(120)
            val mLoc = mlcLocation.trim().take(160)
            require(tName.isNotEmpty() && mName.isNotEmpty() && mLoc.isNotEmpty()) {
                "Teacher name, MLC name, and location are required."
            }
            val cleanLogo = logoData?.takeIf { it.isNotBlank() && it.length <= 60000 }
            val ref = db.collection("users").document(uid).collection("mlcProfiles").document("default")
            if (existingProfile == null) {
                val payload = mapOf(
                    "userId" to uid,
                    "teacherName" to tName,
                    "mlcName" to mName,
                    "mlcLocation" to mLoc,
                    "logoData" to cleanLogo,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                ref.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "teacherName" to tName,
                    "mlcName" to mName,
                    "mlcLocation" to mLoc,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanLogo != null) {
                    updates["logoData"] = cleanLogo
                } else if (existingProfile.logoData != null) {
                    updates["logoData"] = FieldValue.delete()
                }
                ref.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun saveAppSettings(
        userId: String,
        languageCode: String,
        defaultFineAmount: Long,
        sundayHolidayEnabled: Boolean,
        notificationsEnabled: Boolean,
        existingSettings: AppSettings?
    ): Result<Unit> {
        val path = "users/$userId/appSettings/preferences"
        return try {
            val uid = requireUserId(userId)
            val lang = if (languageCode == "hi") "hi" else "en"
            val fine = defaultFineAmount.coerceIn(1L, 1000L)
            val ref = db.collection("users").document(uid).collection("appSettings").document("preferences")
            if (existingSettings == null) {
                val payload = mapOf(
                    "userId" to uid,
                    "languageCode" to lang,
                    "defaultFineAmount" to fine,
                    "sundayHolidayEnabled" to sundayHolidayEnabled,
                    "notificationsEnabled" to notificationsEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            } else {
                ref.update(
                    mapOf(
                        "languageCode" to lang,
                        "defaultFineAmount" to fine,
                        "sundayHolidayEnabled" to sundayHolidayEnabled,
                        "notificationsEnabled" to notificationsEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun addStudent(
        userId: String,
        studentCode: String,
        fullName: String,
        parentName: String?,
        contactNumber: String?
    ): Result<Unit> {
        val docId = "stu_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$userId/students/$docId"
        return try {
            val uid = requireUserId(userId)
            val cleanName = fullName.trim().take(100)
            require(cleanName.isNotEmpty()) { "Student name is required" }
            val payload = mapOf(
                "userId" to uid,
                "studentCode" to studentCode.trim().take(32),
                "fullName" to cleanName,
                "parentName" to parentName?.trim()?.take(100)?.takeIf { it.isNotEmpty() },
                "contactNumber" to contactNumber?.trim()?.take(25)?.takeIf { it.isNotEmpty() },
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("users").document(uid).collection("students").document(docId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateStudent(
        userId: String,
        studentId: String,
        fullName: String,
        parentName: String?,
        contactNumber: String?
    ): Result<Unit> {
        val path = "users/$userId/students/$studentId"
        return try {
            val uid = requireUserId(userId)
            val cleanName = fullName.trim().take(100)
            require(studentId.isNotBlank() && cleanName.isNotEmpty()) { "Invalid student update" }
            val cleanParent = parentName?.trim()?.take(100)?.takeIf { it.isNotEmpty() }
            val cleanContact = contactNumber?.trim()?.take(25)?.takeIf { it.isNotEmpty() }
            val updates = mutableMapOf<String, Any>(
                "fullName" to cleanName,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            updates["parentName"] = cleanParent ?: FieldValue.delete()
            updates["contactNumber"] = cleanContact ?: FieldValue.delete()
            db.collection("users").document(uid).collection("students").document(studentId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteStudent(userId: String, studentId: String): Result<Unit> {
        val path = "users/$userId/students/$studentId"
        return try {
            val uid = requireUserId(userId)
            require(studentId.isNotBlank()) { "Student ID required" }
            db.collection("users").document(uid).collection("students").document(studentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun saveDailyAttendanceBatch(
        userId: String,
        dateKey: String,
        students: List<Student>,
        statuses: Map<String, AttendanceStatus>,
        existingAttendance: List<AttendanceRecord>,
        existingFines: List<FineRecord>,
        defaultFineAmount: Long
    ): Result<Unit> {
        val path = "users/$userId/attendanceRecords"
        return try {
            val uid = requireUserId(userId)
            val attMap = existingAttendance.filter { it.dateKey == dateKey }.associateBy { it.id }
            val fineMap = existingFines.filter { it.attendanceDate == dateKey }.associateBy { it.id }
            val fineValue = defaultFineAmount.coerceIn(1L, 1000L)

            val operations = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()

            for (student in students) {
                val targetStatus = statuses[student.id] ?: continue
                val attId = "${dateKey}_${student.id}"
                val attRef = db.collection("users").document(uid).collection("attendanceRecords").document(attId)
                val existingAtt = attMap[attId]

                if (existingAtt == null) {
                    val attPayload = mapOf(
                        "userId" to uid,
                        "studentId" to student.id,
                        "studentName" to student.fullName.take(100),
                        "dateKey" to dateKey,
                        "status" to targetStatus.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    operations.add { it.set(attRef, attPayload) }
                } else if (existingAtt.statusEnum() != targetStatus || existingAtt.studentName != student.fullName) {
                    val attUpdates = mapOf(
                        "studentName" to student.fullName.take(100),
                        "status" to targetStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    operations.add { it.update(attRef, attUpdates) }
                }

                val fineId = "fine_${dateKey}_${student.id}"
                val fineRef = db.collection("users").document(uid).collection("fineRecords").document(fineId)
                val existingFine = fineMap[fineId]

                when (targetStatus) {
                    AttendanceStatus.ABSENT -> {
                        if (existingFine == null) {
                            val finePayload = mapOf(
                                "userId" to uid,
                                "studentId" to student.id,
                                "studentName" to student.fullName.take(100),
                                "attendanceDate" to dateKey,
                                "amount" to fineValue,
                                "paidAmount" to 0L,
                                "status" to FineStatus.UNPAID.name,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            operations.add { it.set(fineRef, finePayload) }
                        } else {
                            val curStatus = existingFine.statusEnum()
                            if (curStatus == FineStatus.REVERSED || curStatus == FineStatus.CREDIT_ADJUSTED) {
                                val reactivatedAmount = fineValue
                                val reactivatedStatus = when {
                                    existingFine.paidAmount >= reactivatedAmount -> FineStatus.PAID
                                    existingFine.paidAmount > 0L -> FineStatus.PARTIAL
                                    else -> FineStatus.UNPAID
                                }
                                operations.add {
                                    it.update(
                                        fineRef,
                                        mapOf(
                                            "studentName" to student.fullName.take(100),
                                            "amount" to reactivatedAmount,
                                            "status" to reactivatedStatus.name,
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        )
                                    )
                                }
                            }
                        }
                    }

                    AttendanceStatus.PRESENT -> {
                        if (existingFine != null) {
                            val curStatus = existingFine.statusEnum()
                            if (curStatus == FineStatus.UNPAID || curStatus == FineStatus.PARTIAL || curStatus == FineStatus.PAID) {
                                val newStatus = if (existingFine.paidAmount > 0L) {
                                    FineStatus.CREDIT_ADJUSTED
                                } else {
                                    FineStatus.REVERSED
                                }
                                operations.add {
                                    it.update(
                                        fineRef,
                                        mapOf(
                                            "studentName" to student.fullName.take(100),
                                            "amount" to 0L,
                                            "status" to newStatus.name,
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            for (chunk in operations.chunked(350)) {
                val batch = db.batch()
                chunk.forEach { op -> op(batch) }
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun collectFinePayment(
        userId: String,
        student: Student,
        amountPaid: Long,
        currentRemainingFine: Long,
        openFinesForStudent: List<FineRecord>,
        note: String?
    ): Result<Unit> {
        val paymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val path = "users/$userId/paymentRecords/$paymentId"
        return try {
            val uid = requireUserId(userId)
            require(amountPaid in 1L..currentRemainingFine) {
                "Payment amount must be between 1 and remaining fine ($currentRemainingFine)"
            }
            val now = System.currentTimeMillis()
            val remainingAfter = (currentRemainingFine - amountPaid).coerceAtLeast(0L)
            val payRef = db.collection("users").document(uid).collection("paymentRecords").document(paymentId)

            val payPayload = mapOf(
                "userId" to uid,
                "studentId" to student.id,
                "studentName" to student.fullName.take(100),
                "amountPaid" to amountPaid,
                "remainingAfter" to remainingAfter,
                "paymentDateKey" to DateUtils.todayKey(now),
                "paymentTimeText" to DateUtils.currentTimeText(now),
                "note" to note?.trim()?.take(200)?.takeIf { it.isNotEmpty() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val allocations = AttendanceFineCalculator.allocateFinePayment(openFinesForStudent, amountPaid)

            val batch = db.batch()
            batch.set(payRef, payPayload)
            for ((fine, newPaid, newStatus) in allocations) {
                val fineRef = db.collection("users").document(uid).collection("fineRecords").document(fine.id)
                batch.update(
                    fineRef,
                    mapOf(
                        "paidAmount" to newPaid,
                        "status" to newStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun restoreBackup(
        userId: String,
        backup: BackupPayload,
        existingProfile: MlcProfile?,
        existingSettings: AppSettings?,
        existingStudents: List<Student>,
        existingAttendance: List<AttendanceRecord>,
        existingFines: List<FineRecord>,
        existingPayments: List<PaymentRecord>
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()

            backup.mlcProfile?.let { profile ->
                val ref = db.collection("users").document(uid).collection("mlcProfiles").document("default")
                val logo = profile.logoData?.takeIf { it.isNotBlank() && it.length <= 60000 }
                if (existingProfile == null) {
                    val map = mapOf(
                        "userId" to uid,
                        "teacherName" to profile.teacherName.take(100),
                        "mlcName" to profile.mlcName.take(120),
                        "mlcLocation" to profile.mlcLocation.take(160),
                        "logoData" to logo,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    ops.add { it.set(ref, map) }
                } else {
                    val updates = mutableMapOf<String, Any>(
                        "teacherName" to profile.teacherName.take(100),
                        "mlcName" to profile.mlcName.take(120),
                        "mlcLocation" to profile.mlcLocation.take(160),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (logo != null) updates["logoData"] = logo
                    ops.add { it.update(ref, updates) }
                }
            }

            backup.appSettings?.let { settings ->
                val ref = db.collection("users").document(uid).collection("appSettings").document("preferences")
                val lang = if (settings.languageCode == "hi") "hi" else "en"
                val fine = settings.defaultFineAmount.coerceIn(1L, 1000L)
                if (existingSettings == null) {
                    val map = mapOf(
                        "userId" to uid,
                        "languageCode" to lang,
                        "defaultFineAmount" to fine,
                        "sundayHolidayEnabled" to settings.sundayHolidayEnabled,
                        "notificationsEnabled" to settings.notificationsEnabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    ops.add { it.set(ref, map) }
                } else {
                    ops.add {
                        it.update(
                            ref,
                            mapOf(
                                "languageCode" to lang,
                                "defaultFineAmount" to fine,
                                "sundayHolidayEnabled" to settings.sundayHolidayEnabled,
                                "notificationsEnabled" to settings.notificationsEnabled,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            val existingStudentIds = existingStudents.map { it.id }.toSet()
            backup.students.forEach { stu ->
                if (stu.id.isBlank()) return@forEach
                val ref = db.collection("users").document(uid).collection("students").document(stu.id)
                if (stu.id !in existingStudentIds) {
                    val map = mapOf(
                        "userId" to uid,
                        "studentCode" to stu.studentCode.take(32),
                        "fullName" to stu.fullName.take(100),
                        "parentName" to stu.parentName?.take(100)?.takeIf { it.isNotEmpty() },
                        "contactNumber" to stu.contactNumber?.take(25)?.takeIf { it.isNotEmpty() },
                        "active" to stu.active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    ops.add { it.set(ref, map) }
                }
            }

            val existingAttIds = existingAttendance.map { it.id }.toSet()
            backup.attendanceRecords.forEach { rec ->
                if (rec.id.isBlank()) return@forEach
                if (rec.id !in existingAttIds) {
                    val ref = db.collection("users").document(uid).collection("attendanceRecords").document(rec.id)
                    val map = mapOf(
                        "userId" to uid,
                        "studentId" to rec.studentId,
                        "studentName" to rec.studentName.take(100),
                        "dateKey" to rec.dateKey,
                        "status" to rec.statusEnum().name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    ops.add { it.set(ref, map) }
                }
            }

            val existingFineIds = existingFines.map { it.id }.toSet()
            backup.fineRecords.forEach { fine ->
                if (fine.id.isBlank()) return@forEach
                if (fine.id !in existingFineIds) {
                    val ref = db.collection("users").document(uid).collection("fineRecords").document(fine.id)
                    val map = mapOf(
                        "userId" to uid,
                        "studentId" to fine.studentId,
                        "studentName" to fine.studentName.take(100),
                        "attendanceDate" to fine.attendanceDate,
                        "amount" to fine.amount.coerceIn(0L, 10000L),
                        "paidAmount" to fine.paidAmount.coerceIn(0L, 10000L),
                        "status" to fine.statusEnum().name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    ops.add { it.set(ref, map) }
                }
            }

            val existingPayIds = existingPayments.map { it.id }.toSet()
            backup.paymentRecords.forEach { pay ->
                if (pay.id.isBlank()) return@forEach
                if (pay.id !in existingPayIds) {
                    val ref = db.collection("users").document(uid).collection("paymentRecords").document(pay.id)
                    val map = mapOf(
                        "userId" to uid,
                        "studentId" to pay.studentId,
                        "studentName" to pay.studentName.take(100),
                        "amountPaid" to pay.amountPaid.coerceIn(1L, 100000L),
                        "remainingAfter" to pay.remainingAfter.coerceIn(0L, 100000L),
                        "paymentDateKey" to pay.paymentDateKey,
                        "paymentTimeText" to pay.paymentTimeText.take(30),
                        "note" to pay.note?.take(200)?.takeIf { it.isNotEmpty() },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    ops.add { it.set(ref, map) }
                }
            }

            for (chunk in ops.chunked(350)) {
                val batch = db.batch()
                chunk.forEach { it(batch) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
