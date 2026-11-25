package com.example

import com.example.data.export.ReportExporter
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
import com.example.data.repository.BackupPayload
import com.example.data.repository.TuitionRepository
import com.example.data.security.PasswordHasher
import com.example.localization.AppLanguage
import com.example.localization.stringsFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private class InMemoryTuitionRepository : TuitionRepository {
    val userAccount = MutableStateFlow<UserAccount?>(null)
    val mlcProfile = MutableStateFlow<MlcProfile?>(null)
    val appSettings = MutableStateFlow<AppSettings?>(null)
    val students = MutableStateFlow<List<Student>>(emptyList())
    val attendance = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val fines = MutableStateFlow<List<FineRecord>>(emptyList())
    val payments = MutableStateFlow<List<PaymentRecord>>(emptyList())

    override fun observeUserAccount(userId: String): Flow<UserAccount?> = userAccount
    override fun observeMlcProfile(userId: String): Flow<MlcProfile?> = mlcProfile
    override fun observeAppSettings(userId: String): Flow<AppSettings?> = appSettings
    override fun observeStudents(userId: String): Flow<List<Student>> = students
    override fun observeAttendanceRecords(userId: String): Flow<List<AttendanceRecord>> = attendance
    override fun observeFineRecords(userId: String): Flow<List<FineRecord>> = fines
    override fun observePaymentRecords(userId: String): Flow<List<PaymentRecord>> = payments

    override suspend fun registerTeacherAccount(
        userId: String,
        username: String,
        passwordHash: String,
        passwordSalt: String,
        recoveryHash: String,
        recoverySalt: String,
        rememberMe: Boolean
    ): Result<Unit> {
        userAccount.value = UserAccount(
            id = userId,
            userId = userId,
            username = username,
            passwordHash = passwordHash,
            passwordSalt = passwordSalt,
            recoveryHash = recoveryHash,
            recoverySalt = recoverySalt,
            rememberMe = rememberMe
        )
        return Result.success(Unit)
    }

    override suspend fun updateRememberMe(userId: String, rememberMe: Boolean): Result<Unit> {
        userAccount.value = userAccount.value?.copy(rememberMe = rememberMe)
        return Result.success(Unit)
    }

    override suspend fun updatePassword(userId: String, passwordHash: String, passwordSalt: String): Result<Unit> {
        userAccount.value = userAccount.value?.copy(passwordHash = passwordHash, passwordSalt = passwordSalt)
        return Result.success(Unit)
    }

    override suspend fun saveMlcProfile(
        userId: String,
        teacherName: String,
        mlcName: String,
        mlcLocation: String,
        logoData: String?,
        existingProfile: MlcProfile?
    ): Result<Unit> {
        mlcProfile.value = MlcProfile("default", userId, teacherName, mlcName, mlcLocation, logoData)
        return Result.success(Unit)
    }

    override suspend fun saveAppSettings(
        userId: String,
        languageCode: String,
        defaultFineAmount: Long,
        sundayHolidayEnabled: Boolean,
        notificationsEnabled: Boolean,
        existingSettings: AppSettings?
    ): Result<Unit> {
        appSettings.value = AppSettings("preferences", userId, languageCode, defaultFineAmount, sundayHolidayEnabled, notificationsEnabled)
        return Result.success(Unit)
    }

    override suspend fun addStudent(
        userId: String,
        studentCode: String,
        fullName: String,
        parentName: String?,
        contactNumber: String?
    ): Result<Unit> {
        val stu = Student("stu_${UUID.randomUUID().toString().take(8)}", userId, studentCode, fullName, parentName, contactNumber, true)
        students.value = students.value + stu
        return Result.success(Unit)
    }

    override suspend fun updateStudent(
        userId: String,
        studentId: String,
        fullName: String,
        parentName: String?,
        contactNumber: String?
    ): Result<Unit> {
        students.value = students.value.map {
            if (it.id == studentId) it.copy(fullName = fullName, parentName = parentName, contactNumber = contactNumber) else it
        }
        return Result.success(Unit)
    }

    override suspend fun deleteStudent(userId: String, studentId: String): Result<Unit> {
        students.value = students.value.filterNot { it.id == studentId }
        return Result.success(Unit)
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
        val attMap = attendance.value.associateBy { it.id }.toMutableMap()
        val fineMap = fines.value.associateBy { it.id }.toMutableMap()

        for (stu in students) {
            val st = statuses[stu.id] ?: continue
            val attId = "${dateKey}_${stu.id}"
            attMap[attId] = AttendanceRecord(attId, userId, stu.id, stu.fullName, dateKey, st.name)

            val fineId = "fine_${dateKey}_${stu.id}"
            val oldFine = fineMap[fineId]
            if (st == AttendanceStatus.ABSENT) {
                if (oldFine == null) {
                    fineMap[fineId] = FineRecord(fineId, userId, stu.id, stu.fullName, dateKey, defaultFineAmount, 0L, FineStatus.UNPAID.name)
                } else if (oldFine.statusEnum() == FineStatus.REVERSED || oldFine.statusEnum() == FineStatus.CREDIT_ADJUSTED) {
                    val reactivatedStatus = when {
                        oldFine.paidAmount >= defaultFineAmount -> FineStatus.PAID
                        oldFine.paidAmount > 0L -> FineStatus.PARTIAL
                        else -> FineStatus.UNPAID
                    }
                    fineMap[fineId] = oldFine.copy(amount = defaultFineAmount, status = reactivatedStatus.name)
                }
            } else {
                if (oldFine != null && (oldFine.statusEnum() == FineStatus.UNPAID || oldFine.statusEnum() == FineStatus.PARTIAL || oldFine.statusEnum() == FineStatus.PAID)) {
                    val newStatus = if (oldFine.paidAmount > 0L) FineStatus.CREDIT_ADJUSTED else FineStatus.REVERSED
                    fineMap[fineId] = oldFine.copy(amount = 0L, status = newStatus.name)
                }
            }
        }
        attendance.value = attMap.values.toList()
        fines.value = fineMap.values.toList()
        return Result.success(Unit)
    }

    override suspend fun collectFinePayment(
        userId: String,
        student: Student,
        amountPaid: Long,
        currentRemainingFine: Long,
        openFinesForStudent: List<FineRecord>,
        note: String?
    ): Result<Unit> {
        val payId = "pay_${UUID.randomUUID().toString().take(8)}"
        val remainingAfter = (currentRemainingFine - amountPaid).coerceAtLeast(0L)
        payments.value = payments.value + PaymentRecord(
            id = payId,
            userId = userId,
            studentId = student.id,
            studentName = student.fullName,
            amountPaid = amountPaid,
            remainingAfter = remainingAfter,
            paymentDateKey = DateUtils.todayKey(),
            paymentTimeText = "10:00 AM",
            note = note
        )
        val allocs = AttendanceFineCalculator.allocateFinePayment(openFinesForStudent, amountPaid).associateBy { it.first.id }
        fines.value = fines.value.map { f ->
            val upd = allocs[f.id]
            if (upd != null) f.copy(paidAmount = upd.second, status = upd.third.name) else f
        }
        return Result.success(Unit)
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
        if (backup.mlcProfile != null) mlcProfile.value = backup.mlcProfile
        if (backup.appSettings != null) appSettings.value = backup.appSettings
        students.value = backup.students
        attendance.value = backup.attendanceRecords
        fines.value = backup.fineRecords
        payments.value = backup.paymentRecords
        return Result.success(Unit)
    }
}

class TuitionLogicUnitTest {

    @Test
    fun passwordHashing_isSaltedAndVerifiedSecurely() {
        val hashed = PasswordHasher.hashSecret("Malabar@2026")
        assertFalse(hashed.hash.contains("Malabar"))
        assertTrue(hashed.hash.length == 64)
        assertTrue(hashed.salt.length == 32)
        assertTrue(PasswordHasher.verifySecret("Malabar@2026", hashed.hash, hashed.salt))
        assertFalse(PasswordHasher.verifySecret("WrongPass", hashed.hash, hashed.salt))
    }

    @Test
    fun sundayHoliday_detectionWorksAccurately() {
        assertTrue(DateUtils.isSunday("2026-11-22"))
        assertFalse(DateUtils.isSunday("2026-11-23"))
    }

    @Test
    fun endToEndBusinessRules_finesDuplicatePreventionPartialFullPaymentsAndCorrections() = runTest {
        val repo = InMemoryTuitionRepository()
        val uid = "teacher_uid_1"

        val pwd = PasswordHasher.hashSecret("Secret123")
        val rec = PasswordHasher.hashSecret("PIN9999")
        repo.registerTeacherAccount(uid, "rudransh_mlc", pwd.hash, pwd.salt, rec.hash, rec.salt, true)
        repo.saveMlcProfile(uid, "Anjali Nair", "Malabar MLC Central", "Kozhikode", null, null)

        // Add 2 students
        repo.addStudent(uid, "MLC-1001", "Rahul Kumar", "Suresh Kumar", "9876543210")
        repo.addStudent(uid, "MLC-1002", "Aman Verma", null, null)
        val stuList = repo.students.value
        assertEquals(2, stuList.size)
        val rahul = stuList.first { it.fullName == "Rahul Kumar" }
        val aman = stuList.first { it.fullName == "Aman Verma" }

        // Mark Rahul ABSENT for 5 days -> ₹25; Aman PRESENT -> ₹0
        val dates = listOf("2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19", "2026-11-20")
        for (d in dates) {
            repo.saveDailyAttendanceBatch(uid, d, stuList, mapOf(rahul.id to AttendanceStatus.ABSENT, aman.id to AttendanceStatus.PRESENT), repo.attendance.value, repo.fines.value, 5L)
        }
        // Duplicate save on same date -> no duplicate attendance or duplicate fine!
        repo.saveDailyAttendanceBatch(uid, "2026-11-20", stuList, mapOf(rahul.id to AttendanceStatus.ABSENT, aman.id to AttendanceStatus.PRESENT), repo.attendance.value, repo.fines.value, 5L)

        assertEquals(10, repo.attendance.value.size)
        assertEquals(5, repo.fines.value.size)

        val summary1 = AttendanceFineCalculator.computeStudentSummaries(repo.students.value, repo.attendance.value, repo.fines.value, repo.payments.value)
        val rahul1 = summary1.first { it.student.id == rahul.id }
        val aman1 = summary1.first { it.student.id == aman.id }
        assertEquals(5, rahul1.absentDays)
        assertEquals(25L, rahul1.remainingFine)
        assertEquals(0L, aman1.remainingFine)

        // Partial payment ₹10 -> ₹15 remaining
        repo.collectFinePayment(uid, rahul, 10L, 25L, repo.fines.value.filter { it.studentId == rahul.id }, "Partial")
        val rahulPartial = AttendanceFineCalculator.computeStudentSummaries(repo.students.value, repo.attendance.value, repo.fines.value, repo.payments.value).first { it.student.id == rahul.id }
        assertEquals(15L, rahulPartial.remainingFine)
        assertEquals(10L, rahulPartial.totalFinePaid)

        // Full payment ₹15 -> ₹0 remaining (disappears from pending)
        repo.collectFinePayment(uid, rahul, 15L, 15L, repo.fines.value.filter { it.studentId == rahul.id }, "Full")
        val rahulFull = AttendanceFineCalculator.computeStudentSummaries(repo.students.value, repo.attendance.value, repo.fines.value, repo.payments.value).first { it.student.id == rahul.id }
        assertEquals(0L, rahulFull.remainingFine)
        assertEquals(2, repo.payments.value.size)

        // Correct already-paid day 2026-11-20 from ABSENT -> PRESENT -> payment records preserved + ₹5 adjustment credit!
        repo.saveDailyAttendanceBatch(uid, "2026-11-20", stuList, mapOf(rahul.id to AttendanceStatus.PRESENT, aman.id to AttendanceStatus.PRESENT), repo.attendance.value, repo.fines.value, 5L)
        val rahulCorrected = AttendanceFineCalculator.computeStudentSummaries(repo.students.value, repo.attendance.value, repo.fines.value, repo.payments.value).first { it.student.id == rahul.id }
        assertEquals(4, rahulCorrected.absentDays)
        assertEquals(20L, rahulCorrected.totalFineGenerated)
        assertEquals(25L, rahulCorrected.totalFinePaid)
        assertEquals(0L, rahulCorrected.remainingFine)
        assertEquals(5L, rahulCorrected.creditBalance)
        assertEquals(2, repo.payments.value.size)

        // Historical fines unchanged when default fine changes to ₹10 for new absences
        repo.saveDailyAttendanceBatch(uid, "2026-11-21", stuList, mapOf(rahul.id to AttendanceStatus.PRESENT, aman.id to AttendanceStatus.ABSENT), repo.attendance.value, repo.fines.value, 10L)
        val amanUpdated = AttendanceFineCalculator.computeStudentSummaries(repo.students.value, repo.attendance.value, repo.fines.value, repo.payments.value).first { it.student.id == aman.id }
        assertEquals(10L, amanUpdated.remainingFine)

        // Unpaid fine reversal for Aman (ABSENT -> PRESENT) drops remaining fine to ₹0
        repo.saveDailyAttendanceBatch(uid, "2026-11-21", stuList, mapOf(rahul.id to AttendanceStatus.PRESENT, aman.id to AttendanceStatus.PRESENT), repo.attendance.value, repo.fines.value, 10L)
        val amanReversed = AttendanceFineCalculator.computeStudentSummaries(repo.students.value, repo.attendance.value, repo.fines.value, repo.payments.value).first { it.student.id == aman.id }
        assertEquals(0L, amanReversed.remainingFine)

        // CSV & Localization
        val csv = String(ReportExporter.buildCsv(listOf("Student", "Pending"), listOf(listOf("Rahul", "0"))), Charsets.UTF_8)
        assertTrue(csv.contains("Rahul"))
        assertEquals("ट्यूशन प्रबंधन", stringsFor(AppLanguage.HINDI).appTitle)
        assertEquals("TUITION MANAGEMENT", stringsFor(AppLanguage.ENGLISH).appTitle)
    }
}
