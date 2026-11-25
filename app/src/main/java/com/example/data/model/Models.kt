package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class AttendanceStatus {
    PRESENT,
    ABSENT;

    companion object {
        fun fromRaw(raw: String?): AttendanceStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PRESENT
    }
}

enum class FineStatus {
    UNPAID,
    PARTIAL,
    PAID,
    REVERSED,
    CREDIT_ADJUSTED;

    companion object {
        fun fromRaw(raw: String?): FineStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNPAID
    }
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val recoveryHash: String = "",
    val recoverySalt: String = "",
    val rememberMe: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MlcProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val teacherName: String = "",
    val mlcName: String = "",
    val mlcLocation: String = "",
    val logoData: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentCode: String = "",
    val fullName: String = "",
    val parentName: String? = null,
    val contactNumber: String? = null,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val dateKey: String = "",
    val status: String = AttendanceStatus.PRESENT.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): AttendanceStatus = AttendanceStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class FineRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val attendanceDate: String = "",
    val amount: Long = 5L,
    val paidAmount: Long = 0L,
    val status: String = FineStatus.UNPAID.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): FineStatus = FineStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val amountPaid: Long = 0L,
    val remainingAfter: Long = 0L,
    val paymentDateKey: String = "",
    val paymentTimeText: String = "",
    val note: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val languageCode: String = "en",
    val defaultFineAmount: Long = 5L,
    val sundayHolidayEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class StudentSummary(
    val student: Student,
    val presentDays: Int,
    val absentDays: Int,
    val totalFineGenerated: Long,
    val totalFinePaid: Long,
    val remainingFine: Long,
    val creditBalance: Long
)

data class DashboardMetrics(
    val totalStudents: Int,
    val presentToday: Int,
    val absentToday: Int,
    val totalPendingFines: Long,
    val totalFineCollected: Long,
    val attendancePercentage: Int,
    val isSundayHoliday: Boolean,
    val pendingStudentCount: Int
)

data class DailyAttendanceSummary(
    val dateKey: String,
    val presentCount: Int,
    val absentCount: Int,
    val totalMarked: Int,
    val fineGenerated: Long,
    val isSunday: Boolean
)
