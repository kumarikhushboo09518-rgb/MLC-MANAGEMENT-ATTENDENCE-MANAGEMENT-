package com.example.data.logic

import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.DailyAttendanceSummary
import com.example.data.model.DashboardMetrics
import com.example.data.model.FineRecord
import com.example.data.model.FineStatus
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.data.model.StudentSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DateUtils {
    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
    private val displayEnFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH)
    private val displayHiFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.forLanguageTag("hi-IN"))

    fun todayKey(nowMillis: Long = System.currentTimeMillis()): String =
        keyFormat.format(Date(nowMillis))

    fun currentMonthPrefix(nowMillis: Long = System.currentTimeMillis()): String =
        todayKey(nowMillis).substring(0, 7)

    fun currentTimeText(nowMillis: Long = System.currentTimeMillis()): String =
        timeFormat.format(Date(nowMillis))

    fun dateKeyFromMillis(millis: Long): String = keyFormat.format(Date(millis))

    fun millisFromDateKey(dateKey: String): Long {
        return try {
            keyFormat.parse(dateKey)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun isSunday(dateKey: String): Boolean {
        return try {
            val date = keyFormat.parse(dateKey) ?: return false
            val cal = Calendar.getInstance()
            cal.time = date
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        } catch (_: Exception) {
            false
        }
    }

    fun formatDisplayDate(dateKey: String, languageCode: String): String {
        return try {
            val date = keyFormat.parse(dateKey) ?: return dateKey
            val fmt = if (languageCode == "hi") displayHiFormat else displayEnFormat
            fmt.format(date)
        } catch (_: Exception) {
            dateKey
        }
    }

    fun recentDateKeys(days: Int, endMillis: Long = System.currentTimeMillis()): List<String> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = endMillis
        val list = mutableListOf<String>()
        for (i in 0 until days) {
            list.add(keyFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return list.reversed()
    }
}

object AttendanceFineCalculator {

    fun generateNextStudentCode(existingStudents: List<Student>): String {
        val maxNum = existingStudents.mapNotNull { stu ->
            stu.studentCode.removePrefix("MLC-").toIntOrNull()
        }.maxOrNull() ?: 1000
        return "MLC-${maxNum + 1}"
    }

    fun computeStudentSummaries(
        students: List<Student>,
        attendanceRecords: List<AttendanceRecord>,
        fineRecords: List<FineRecord>,
        paymentRecords: List<PaymentRecord>
    ): List<StudentSummary> {
        val attByStudent = attendanceRecords.groupBy { it.studentId }
        val finesByStudent = fineRecords.groupBy { it.studentId }
        val paymentsByStudent = paymentRecords.groupBy { it.studentId }

        return students.map { student ->
            val stuAtt = attByStudent[student.id].orEmpty()
            val presentDays = stuAtt.count { it.statusEnum() == AttendanceStatus.PRESENT }
            val absentDays = stuAtt.count { it.statusEnum() == AttendanceStatus.ABSENT }

            val stuFines = finesByStudent[student.id].orEmpty()
            val activeFinesTotal = stuFines
                .filter {
                    val st = it.statusEnum()
                    st == FineStatus.UNPAID || st == FineStatus.PARTIAL || st == FineStatus.PAID
                }
                .sumOf { it.amount }

            val totalPaid = paymentsByStudent[student.id].orEmpty().sumOf { it.amountPaid }
            val remaining = max(0L, activeFinesTotal - totalPaid)
            val credit = max(0L, totalPaid - activeFinesTotal)

            StudentSummary(
                student = student,
                presentDays = presentDays,
                absentDays = absentDays,
                totalFineGenerated = activeFinesTotal,
                totalFinePaid = totalPaid,
                remainingFine = remaining,
                creditBalance = credit
            )
        }
    }

    fun computeDashboardMetrics(
        students: List<Student>,
        attendanceRecords: List<AttendanceRecord>,
        fineRecords: List<FineRecord>,
        paymentRecords: List<PaymentRecord>,
        todayKey: String,
        sundayHolidayEnabled: Boolean
    ): DashboardMetrics {
        val activeIds = students.map { it.id }.toSet()
        val todayRecords = attendanceRecords.filter { it.dateKey == todayKey && it.studentId in activeIds }
        val presentToday = todayRecords.count { it.statusEnum() == AttendanceStatus.PRESENT }
        val absentToday = todayRecords.count { it.statusEnum() == AttendanceStatus.ABSENT }
        val totalStudents = students.size
        val pct = if (totalStudents > 0) {
            ((presentToday.toDouble() / totalStudents.toDouble()) * 100.0).roundToInt()
        } else 0

        val summaries = computeStudentSummaries(students, attendanceRecords, fineRecords, paymentRecords)
        val totalPending = summaries.sumOf { it.remainingFine }
        val pendingStudents = summaries.count { it.remainingFine > 0L }
        val totalCollected = paymentRecords.sumOf { it.amountPaid }

        return DashboardMetrics(
            totalStudents = totalStudents,
            presentToday = presentToday,
            absentToday = absentToday,
            totalPendingFines = totalPending,
            totalFineCollected = totalCollected,
            attendancePercentage = pct,
            isSundayHoliday = sundayHolidayEnabled && DateUtils.isSunday(todayKey),
            pendingStudentCount = pendingStudents
        )
    }

    fun computeDailyAttendanceSummary(
        dateKey: String,
        students: List<Student>,
        attendanceRecords: List<AttendanceRecord>,
        fineRecords: List<FineRecord>
    ): DailyAttendanceSummary {
        val activeIds = students.map { it.id }.toSet()
        val dayRecords = attendanceRecords.filter { it.dateKey == dateKey && it.studentId in activeIds }
        val presentCount = dayRecords.count { it.statusEnum() == AttendanceStatus.PRESENT }
        val absentCount = dayRecords.count { it.statusEnum() == AttendanceStatus.ABSENT }
        val fineGenerated = fineRecords
            .filter {
                it.attendanceDate == dateKey &&
                    it.statusEnum() != FineStatus.REVERSED &&
                    it.statusEnum() != FineStatus.CREDIT_ADJUSTED
            }
            .sumOf { it.amount }

        return DailyAttendanceSummary(
            dateKey = dateKey,
            presentCount = presentCount,
            absentCount = absentCount,
            totalMarked = dayRecords.size,
            fineGenerated = fineGenerated,
            isSunday = DateUtils.isSunday(dateKey)
        )
    }

    /**
     * Allocates a fine payment across open fine records (oldest date first).
     * Returns pairs of (FineRecord, newPaidAmount, newStatus).
     */
    fun allocateFinePayment(
        openFines: List<FineRecord>,
        paymentAmount: Long
    ): List<Triple<FineRecord, Long, FineStatus>> {
        var remainingToAllocate = paymentAmount
        val updates = mutableListOf<Triple<FineRecord, Long, FineStatus>>()

        val sorted = openFines
            .filter {
                val st = it.statusEnum()
                (st == FineStatus.UNPAID || st == FineStatus.PARTIAL) && it.amount > it.paidAmount
            }
            .sortedBy { it.attendanceDate }

        for (fine in sorted) {
            if (remainingToAllocate <= 0L) break
            val due = fine.amount - fine.paidAmount
            if (due <= 0L) continue
            val apply = min(due, remainingToAllocate)
            val newPaid = fine.paidAmount + apply
            val newStatus = if (newPaid >= fine.amount) FineStatus.PAID else FineStatus.PARTIAL
            updates.add(Triple(fine, newPaid, newStatus))
            remainingToAllocate -= apply
        }
        return updates
    }
}
