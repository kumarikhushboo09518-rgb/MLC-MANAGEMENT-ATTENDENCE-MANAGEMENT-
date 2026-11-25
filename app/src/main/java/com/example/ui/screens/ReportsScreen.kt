package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.export.ReportExporter
import com.example.data.logic.AttendanceFineCalculator
import com.example.data.logic.DateUtils
import com.example.data.model.AttendanceRecord
import com.example.data.model.FineRecord
import com.example.data.model.FineStatus
import com.example.data.model.MlcProfile
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.data.model.StudentSummary
import com.example.localization.LocalStrings
import com.example.ui.components.DualBarAttendanceChart
import com.example.ui.theme.AmberOnTint
import com.example.ui.theme.CardBorderGrey
import com.example.ui.theme.CrimsonOnTint
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldOnTint
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private enum class AttendanceReportKind { DAILY, WEEKLY, MONTHLY, STUDENT_WISE }
private enum class FineReportKind { DAILY, MONTHLY, PENDING, COLLECTION }

@Composable
fun ReportsScreen(
    mlcProfile: MlcProfile?,
    students: List<Student>,
    summaries: List<StudentSummary>,
    attendanceRecords: List<AttendanceRecord>,
    fineRecords: List<FineRecord>,
    paymentRecords: List<PaymentRecord>,
    onShowMessage: (String) -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var activeTab by remember { mutableIntStateOf(0) }
    var attKind by remember { mutableStateOf(AttendanceReportKind.DAILY) }
    var fineKind by remember { mutableStateOf(FineReportKind.PENDING) }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val bytes = pendingExportBytes
        if (uri != null && bytes != null) {
            if (ReportExporter.writeUriBytes(context, uri, bytes)) {
                onShowMessage(s.exportSuccessMsg)
            }
        }
        pendingExportBytes = null
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val bytes = pendingExportBytes
        if (uri != null && bytes != null) {
            if (ReportExporter.writeUriBytes(context, uri, bytes)) {
                onShowMessage(s.exportSuccessMsg)
            }
        }
        pendingExportBytes = null
    }

    val todayKey = remember { DateUtils.todayKey() }
    val monthPrefix = remember { DateUtils.currentMonthPrefix() }
    val weekDates = remember { DateUtils.recentDateKeys(7) }
    val monthDates = remember { DateUtils.recentDateKeys(30) }

    // Build active report table (headers + rows) for both display & export
    val (reportTitle, headers, rows) = remember(
        activeTab, attKind, fineKind, students, summaries, attendanceRecords, fineRecords, paymentRecords, s
    ) {
        if (activeTab == 0) {
            when (attKind) {
                AttendanceReportKind.DAILY -> {
                    val sum = AttendanceFineCalculator.computeDailyAttendanceSummary(todayKey, students, attendanceRecords, fineRecords)
                    Triple(
                        "${s.dailyReport} ${s.navAttendance}",
                        listOf("Date", s.presentStudentsCount, s.absentStudentsCount, s.totalStudents, s.fineGeneratedOnDate),
                        listOf(listOf(todayKey, "${sum.presentCount}", "${sum.absentCount}", "${students.size}", "Rs.${sum.fineGenerated}"))
                    )
                }
                AttendanceReportKind.WEEKLY -> {
                    val daySums = weekDates.map { d ->
                        AttendanceFineCalculator.computeDailyAttendanceSummary(d, students, attendanceRecords, fineRecords)
                    }
                    Triple(
                        "${s.weeklyReport} ${s.navAttendance}",
                        listOf("Date", s.presentStudentsCount, s.absentStudentsCount, s.fineGeneratedOnDate),
                        daySums.map { listOf(it.dateKey, "${it.presentCount}", "${it.absentCount}", "Rs.${it.fineGenerated}") }
                    )
                }
                AttendanceReportKind.MONTHLY -> {
                    val daySums = monthDates.map { d ->
                        AttendanceFineCalculator.computeDailyAttendanceSummary(d, students, attendanceRecords, fineRecords)
                    }.filter { it.totalMarked > 0 || it.dateKey == todayKey }
                    Triple(
                        "${s.monthlyReport} ${s.navAttendance}",
                        listOf("Date", s.presentStudentsCount, s.absentStudentsCount, s.fineGeneratedOnDate),
                        daySums.map { listOf(it.dateKey, "${it.presentCount}", "${it.absentCount}", "Rs.${it.fineGenerated}") }
                    )
                }
                AttendanceReportKind.STUDENT_WISE -> {
                    Triple(
                        s.studentWiseReport,
                        listOf("ID", s.navStudents, s.presentStudentsCount, s.absentStudentsCount, s.remainingFine),
                        summaries.sortedBy { it.student.fullName.lowercase() }.map {
                            listOf(it.student.studentCode, it.student.fullName, "${it.presentDays}", "${it.absentDays}", "Rs.${it.remainingFine}")
                        }
                    )
                }
            }
        } else {
            when (fineKind) {
                FineReportKind.DAILY -> {
                    val dayFines = fineRecords.filter {
                        it.attendanceDate == todayKey && it.statusEnum() != FineStatus.REVERSED && it.statusEnum() != FineStatus.CREDIT_ADJUSTED
                    }
                    Triple(
                        "${s.dailyReport} ${s.fineReportsTab}",
                        listOf(s.navStudents, "Date", s.totalFineLabel, s.paidLabel, "Status"),
                        dayFines.map { listOf(it.studentName, it.attendanceDate, "Rs.${it.amount}", "Rs.${it.paidAmount}", it.status) }
                    )
                }
                FineReportKind.MONTHLY -> {
                    val monthFines = fineRecords.filter {
                        it.attendanceDate.startsWith(monthPrefix) && it.statusEnum() != FineStatus.REVERSED && it.statusEnum() != FineStatus.CREDIT_ADJUSTED
                    }
                    Triple(
                        "${s.monthlyReport} ${s.fineReportsTab}",
                        listOf(s.navStudents, "Date", s.totalFineLabel, s.paidLabel, "Status"),
                        monthFines.map { listOf(it.studentName, it.attendanceDate, "Rs.${it.amount}", "Rs.${it.paidAmount}", it.status) }
                    )
                }
                FineReportKind.PENDING -> {
                    val pending = summaries.filter { it.remainingFine > 0 }.sortedByDescending { it.remainingFine }
                    Triple(
                        s.pendingFineReport,
                        listOf(s.navStudents, s.absentDaysLabel, s.totalFineLabel, s.paidLabel, s.pendingAmountLabel),
                        pending.map { listOf(it.student.fullName, "${it.absentDays}", "Rs.${it.totalFineGenerated}", "Rs.${it.totalFinePaid}", "Rs.${it.remainingFine}") }
                    )
                }
                FineReportKind.COLLECTION -> {
                    val sortedPays = paymentRecords.sortedByDescending { it.paymentDateKey }
                    Triple(
                        s.fineCollectionReport,
                        listOf(s.navStudents, "Date", "Time", s.paidLabel, s.pendingAmountLabel),
                        sortedPays.map { listOf(it.studentName, it.paymentDateKey, it.paymentTimeText, "Rs.${it.amountPaid}", "Rs.${payRemaining(it)}") }
                    )
                }
            }
        }
    }

    val chartDays = remember(weekDates, students, attendanceRecords, fineRecords) {
        weekDates.map { d -> AttendanceFineCalculator.computeDailyAttendanceSummary(d, students, attendanceRecords, fineRecords) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = PureWhite,
            contentColor = DeepNavy
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text(s.attendanceReportsTab) },
                modifier = Modifier.testTag("reports_tab_attendance")
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text(s.fineReportsTab) },
                modifier = Modifier.testTag("reports_tab_fines")
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeTab == 0) {
                        item {
                            FilterChip(selected = attKind == AttendanceReportKind.DAILY, onClick = { attKind = AttendanceReportKind.DAILY }, label = { Text(s.dailyReport) })
                        }
                        item {
                            FilterChip(selected = attKind == AttendanceReportKind.WEEKLY, onClick = { attKind = AttendanceReportKind.WEEKLY }, label = { Text(s.weeklyReport) })
                        }
                        item {
                            FilterChip(selected = attKind == AttendanceReportKind.MONTHLY, onClick = { attKind = AttendanceReportKind.MONTHLY }, label = { Text(s.monthlyReport) })
                        }
                        item {
                            FilterChip(selected = attKind == AttendanceReportKind.STUDENT_WISE, onClick = { attKind = AttendanceReportKind.STUDENT_WISE }, label = { Text(s.studentWiseReport) })
                        }
                    } else {
                        item {
                            FilterChip(selected = fineKind == FineReportKind.DAILY, onClick = { fineKind = FineReportKind.DAILY }, label = { Text(s.dailyReport) })
                        }
                        item {
                            FilterChip(selected = fineKind == FineReportKind.MONTHLY, onClick = { fineKind = FineReportKind.MONTHLY }, label = { Text(s.monthlyReport) })
                        }
                        item {
                            FilterChip(selected = fineKind == FineReportKind.PENDING, onClick = { fineKind = FineReportKind.PENDING }, label = { Text(s.pendingFineReport) })
                        }
                        item {
                            FilterChip(selected = fineKind == FineReportKind.COLLECTION, onClick = { fineKind = FineReportKind.COLLECTION }, label = { Text(s.fineCollectionReport) })
                        }
                    }
                }
            }

            // Visual Chart card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = BorderStroke(1.dp, CardBorderGrey)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(reportTitle, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (activeTab == 0) {
                            DualBarAttendanceChart(
                                labels = chartDays.map { it.dateKey.takeLast(5) },
                                presentCounts = chartDays.map { it.presentCount },
                                absentCounts = chartDays.map { it.absentCount }
                            )
                        } else {
                            val totalGen = summaries.sumOf { it.totalFineGenerated }
                            val totalPaid = paymentRecords.sumOf { it.amountPaid }
                            val totalPend = summaries.sumOf { it.remainingFine }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                ReportMetricPill(s.totalFineGenerated, "₹$totalGen", DeepNavy)
                                ReportMetricPill(s.totalFineCollected, "₹$totalPaid", EmeraldOnTint)
                                ReportMetricPill(s.totalPendingFines, "₹$totalPend", AmberOnTint)
                            }
                        }
                    }
                }
            }

            // Export Actions (PDF & Excel CSV — Save & Share)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = BorderStroke(1.dp, CardBorderGrey)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val bytes = ReportExporter.buildPdfReport(
                                        title = reportTitle,
                                        mlcName = mlcProfile?.mlcName ?: "Malabar MLC",
                                        teacherName = mlcProfile?.teacherName ?: "Teacher",
                                        subtitle = todayKey,
                                        headers = headers,
                                        rows = rows
                                    )
                                    pendingExportBytes = bytes
                                    savePdfLauncher.launch("mlc_report_$todayKey.pdf")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_pdf_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepNavy)
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(s.exportPdfBtn)
                            }
                            Button(
                                onClick = {
                                    val bytes = ReportExporter.buildCsv(headers, rows)
                                    pendingExportBytes = bytes
                                    saveCsvLauncher.launch("mlc_report_$todayKey.csv")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_csv_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                            ) {
                                Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(s.exportExcelBtn)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    val bytes = ReportExporter.buildPdfReport(
                                        title = reportTitle,
                                        mlcName = mlcProfile?.mlcName ?: "Malabar MLC",
                                        teacherName = mlcProfile?.teacherName ?: "Teacher",
                                        subtitle = todayKey,
                                        headers = headers,
                                        rows = rows
                                    )
                                    ReportExporter.shareBytes(context, "mlc_report_$todayKey.pdf", "application/pdf", bytes)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${s.shareBtn} PDF")
                            }
                            OutlinedButton(
                                onClick = {
                                    val bytes = ReportExporter.buildCsv(headers, rows)
                                    ReportExporter.shareBytes(context, "mlc_report_$todayKey.csv", "text/csv", bytes)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${s.shareBtn} CSV")
                            }
                        }
                    }
                }
            }

            // Data Table Rows
            if (rows.isEmpty()) {
                item {
                    Text(
                        text = s.noRecordsYet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                items(rows) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, CardBorderGrey)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = row.firstOrNull() ?: "",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = row.lastOrNull() ?: "",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (activeTab == 0) DeepNavy else CrimsonOnTint
                                )
                            }
                            if (row.size > 2) {
                                Text(
                                    text = row.drop(1).dropLast(1).joinToString("  •  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun payRemaining(p: PaymentRecord): Long = p.remainingAfter

@Composable
private fun ReportMetricPill(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
