package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.logic.AttendanceFineCalculator
import com.example.data.logic.DateUtils
import com.example.data.model.AppSettings
import com.example.data.model.AttendanceRecord
import com.example.data.model.DashboardMetrics
import com.example.data.model.FineRecord
import com.example.data.model.MlcProfile
import com.example.data.model.Student
import com.example.localization.LocalStrings
import com.example.ui.components.BrandLogoAvatar
import com.example.ui.components.DualBarAttendanceChart
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.SundayHolidayBanner
import com.example.ui.theme.AmberTint
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBorderGrey
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonTint
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldTint
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    mlcProfile: MlcProfile?,
    settings: AppSettings?,
    metrics: DashboardMetrics,
    students: List<Student>,
    attendanceRecords: List<AttendanceRecord>,
    fineRecords: List<FineRecord>,
    onNavigateAttendance: () -> Unit,
    onNavigatePendingFines: () -> Unit,
    onNavigatePaymentHistory: () -> Unit,
    onNavigateStudents: () -> Unit
) {
    val s = LocalStrings.current
    val todayKey = remember { DateUtils.todayKey() }
    val todayFormatted = remember(todayKey, s.languageCode) {
        DateUtils.formatDisplayDate(todayKey, s.languageCode)
    }
    val defaultFine = settings?.defaultFineAmount ?: 5L

    val recentDays = remember { DateUtils.recentDateKeys(7) }
    val weeklySummaries = remember(recentDays, students, attendanceRecords, fineRecords) {
        recentDays.map { d ->
            AttendanceFineCalculator.computeDailyAttendanceSummary(d, students, attendanceRecords, fineRecords)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Executive Welcome Hero Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DeepNavy)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_mlc_hero_banner),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.35f
                    )
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            BrandLogoAvatar(logoBase64 = mlcProfile?.logoData, size = 54.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${s.welcomePrefix} ${mlcProfile?.teacherName ?: ""}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOfNotNull(
                                        mlcProfile?.mlcName?.takeIf { it.isNotBlank() },
                                        mlcProfile?.mlcLocation?.takeIf { it.isNotBlank() }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PureWhite.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = PureWhite.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CalendarToday,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = todayFormatted,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PureWhite
                                    )
                                }
                            }

                            Surface(
                                color = EmeraldGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(50.dp)
                            ) {
                                Text(
                                    text = s.organizationName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sunday Holiday Alert if Today is Sunday
        if (metrics.isSundayHoliday) {
            item { SundayHolidayBanner() }
        }

        // Business Rule Highlight Pill
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = EmeraldTint,
                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CurrencyRupee,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = String.format(s.noFineRuleBanner, defaultFine),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }
        }

        // 6 KPI Summary Cards (2-col on Compact, 3-col on Wide)
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 680.dp) 3 else 2
                val cards = listOf(
                    Triple(s.totalStudents, metrics.totalStudents.toString(), Icons.Rounded.Groups) to
                        Triple(DeepNavy, DeepNavy.copy(alpha = 0.1f), onNavigateStudents),
                    Triple(s.presentToday, metrics.presentToday.toString(), Icons.Rounded.CheckCircle) to
                        Triple(EmeraldGreen, EmeraldTint, onNavigateAttendance),
                    Triple(s.absentToday, metrics.absentToday.toString(), Icons.Rounded.PersonOff) to
                        Triple(CrimsonRed, CrimsonTint, onNavigateAttendance),
                    Triple(s.totalPendingFines, "₹${metrics.totalPendingFines}", Icons.Rounded.AccountBalanceWallet) to
                        Triple(AmberWarning, AmberTint, onNavigatePendingFines),
                    Triple(s.totalFineCollected, "₹${metrics.totalFineCollected}", Icons.AutoMirrored.Rounded.ReceiptLong) to
                        Triple(EmeraldGreen, EmeraldTint, onNavigatePaymentHistory),
                    Triple(s.todayAttendancePct, "${metrics.attendancePercentage}%", Icons.Rounded.Percent) to
                        Triple(DeepNavy, DeepNavy.copy(alpha = 0.1f), onNavigateAttendance)
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cards.chunked(columns).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { (info, style) ->
                                KpiMetricCard(
                                    title = info.first,
                                    value = info.second,
                                    icon = info.third,
                                    accentColor = style.first,
                                    accentTint = style.second,
                                    onClick = style.third,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size < columns) {
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = BorderStroke(1.dp, CardBorderGrey)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = s.quickActionsTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateAttendance,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("quick_attendance_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepNavy)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.markTodayAttendanceBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = onNavigatePendingFines,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("quick_fines_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                        ) {
                            Icon(Icons.Rounded.CurrencyRupee, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.collectFinesBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedButton(
                        onClick = onNavigateStudents,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("quick_add_student_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(s.addStudentBtn)
                    }
                }
            }
        }

        // 7-Day Attendance Overview Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = BorderStroke(1.dp, CardBorderGrey)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${s.weeklyReport} • ${s.navAttendance}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendDot(color = EmeraldGreen, label = s.presentStudentsCount)
                            LegendDot(color = CrimsonRed, label = s.absentStudentsCount)
                        }
                    }
                    DualBarAttendanceChart(
                        labels = weeklySummaries.map { it.dateKey.takeLast(5) },
                        presentCounts = weeklySummaries.map { it.presentCount },
                        absentCounts = weeklySummaries.map { it.absentCount }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
