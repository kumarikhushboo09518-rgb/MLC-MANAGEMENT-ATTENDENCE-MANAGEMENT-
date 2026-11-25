package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.logic.AttendanceFineCalculator
import com.example.data.logic.DateUtils
import com.example.data.model.AppSettings
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.FineRecord
import com.example.data.model.Student
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MlcDatePickerDialog
import com.example.ui.components.SundayHolidayBanner
import com.example.ui.theme.CardBorderGrey
import com.example.ui.theme.CrimsonOnTint
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonTint
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldOnTint
import com.example.ui.theme.EmeraldTint
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftLightGrey
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun AttendanceScreen(
    students: List<Student>,
    attendanceRecords: List<AttendanceRecord>,
    fineRecords: List<FineRecord>,
    settings: AppSettings?,
    isBusy: Boolean,
    onSaveAttendance: suspend (dateKey: String, statuses: Map<String, AttendanceStatus>) -> Result<Unit>,
    onShowMessage: (String) -> Unit
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDateKey by remember { mutableStateOf(DateUtils.todayKey()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var sundayOverride by remember(selectedDateKey) { mutableStateOf(false) }

    val sundayHolidayEnabled = settings?.sundayHolidayEnabled ?: true
    val isSelectedSunday = DateUtils.isSunday(selectedDateKey)
    val isLockedHoliday = sundayHolidayEnabled && isSelectedSunday && !sundayOverride

    val sortedStudents = remember(students) { students.sortedBy { it.fullName.lowercase() } }
    val draftStatuses = remember { mutableStateMapOf<String, AttendanceStatus>() }

    LaunchedEffect(selectedDateKey, attendanceRecords, sortedStudents) {
        draftStatuses.clear()
        val existingForDate = attendanceRecords.filter { it.dateKey == selectedDateKey }.associateBy { it.studentId }
        for (stu in sortedStudents) {
            existingForDate[stu.id]?.let { rec ->
                draftStatuses[stu.id] = rec.statusEnum()
            }
        }
    }

    val presentCount = draftStatuses.values.count { it == AttendanceStatus.PRESENT }
    val absentCount = draftStatuses.values.count { it == AttendanceStatus.ABSENT }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = PureWhite,
            contentColor = DeepNavy
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(s.markAttendanceTab) },
                modifier = Modifier.testTag("tab_mark_attendance")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(s.dateHistoryTab) },
                modifier = Modifier.testTag("tab_date_history")
            )
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date selector card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, CardBorderGrey)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = DateUtils.formatDisplayDate(selectedDateKey, s.languageCode),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (selectedDateKey == DateUtils.todayKey()) s.todayTag else selectedDateKey,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldOnTint
                                )
                            }
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.testTag("attendance_date_button")
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(s.selectDateBtn)
                            }
                        }
                    }
                }

                // Sunday Holiday Banner & Override toggle
                if (sundayHolidayEnabled && isSelectedSunday) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SundayHolidayBanner()
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = PureWhite),
                                border = BorderStroke(1.dp, CardBorderGrey)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = s.sundayOverrideLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = sundayOverride,
                                        onCheckedChange = { sundayOverride = it },
                                        modifier = Modifier.testTag("sunday_override_switch")
                                    )
                                }
                            }
                        }
                    }
                }

                if (sortedStudents.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.AutoMirrored.Rounded.FactCheck,
                            title = s.noStudentsTitle,
                            description = s.noStudentsDesc
                        )
                    }
                } else {
                    // Live Counts + Mark All Present
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            border = BorderStroke(1.dp, CardBorderGrey)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(color = EmeraldTint, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${s.presentStudentsCount}: $presentCount",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = EmeraldOnTint,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                    Surface(color = CrimsonTint, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${s.absentStudentsCount}: $absentCount",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = CrimsonOnTint,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        sortedStudents.forEach { stu ->
                                            draftStatuses[stu.id] = AttendanceStatus.PRESENT
                                        }
                                    },
                                    enabled = !isLockedHoliday,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("mark_all_present_button")
                                ) {
                                    Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(s.markAllPresentBtn)
                                }
                            }
                        }
                    }

                    // Student List with PRESENT / ABSENT buttons
                    items(sortedStudents, key = { it.id }) { stu ->
                        val status = draftStatuses[stu.id]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            border = BorderStroke(1.dp, CardBorderGrey)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stu.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stu.studentCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isPresent = status == AttendanceStatus.PRESENT
                                    val isAbsent = status == AttendanceStatus.ABSENT
                                    Button(
                                        onClick = { draftStatuses[stu.id] = AttendanceStatus.PRESENT },
                                        enabled = !isLockedHoliday,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPresent) EmeraldGreen else SoftLightGrey,
                                            contentColor = if (isPresent) PureWhite else TextSecondary
                                        ),
                                        modifier = Modifier.testTag("present_btn_${stu.id}")
                                    ) {
                                        Text(s.presentBtn, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Button(
                                        onClick = { draftStatuses[stu.id] = AttendanceStatus.ABSENT },
                                        enabled = !isLockedHoliday,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAbsent) CrimsonRed else SoftLightGrey,
                                            contentColor = if (isAbsent) PureWhite else TextSecondary
                                        ),
                                        modifier = Modifier.testTag("absent_btn_${stu.id}")
                                    ) {
                                        Text(s.absentBtn, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                scope.launch {
                                    val res = onSaveAttendance(selectedDateKey, draftStatuses.toMap())
                                    res.onSuccess {
                                        onShowMessage(s.attendanceSavedMsg)
                                    }.onFailure {
                                        onShowMessage(it.localizedMessage ?: "Error")
                                    }
                                }
                            },
                            enabled = !isBusy && !isLockedHoliday && draftStatuses.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("save_attendance_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepNavy, contentColor = PureWhite)
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PureWhite, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(s.saveAttendanceBtn, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        if (isLockedHoliday) {
                            Text(
                                text = s.sundayLockedNotice,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Date-wise Attendance History Tab
            val recordedDates = remember(attendanceRecords) {
                (attendanceRecords.map { it.dateKey } + DateUtils.todayKey()).distinct().sortedDescending()
            }
            val daySummary = remember(selectedDateKey, students, attendanceRecords, fineRecords) {
                AttendanceFineCalculator.computeDailyAttendanceSummary(
                    selectedDateKey, students, attendanceRecords, fineRecords
                )
            }
            val dayRecords = remember(selectedDateKey, attendanceRecords) {
                attendanceRecords.filter { it.dateKey == selectedDateKey }.sortedBy { it.studentName.lowercase() }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateUtils.formatDisplayDate(selectedDateKey, s.languageCode),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        OutlinedButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.selectDateBtn)
                        }
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recordedDates.take(14)) { dKey ->
                            FilterChip(
                                selected = dKey == selectedDateKey,
                                onClick = { selectedDateKey = dKey },
                                label = { Text(dKey) }
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, CardBorderGrey)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HistoryStat(s.presentStudentsCount, "${daySummary.presentCount}", EmeraldOnTint)
                            HistoryStat(s.absentStudentsCount, "${daySummary.absentCount}", CrimsonOnTint)
                            HistoryStat(s.totalStudents, "${students.size}", DeepNavy)
                            HistoryStat(s.fineGeneratedOnDate, "₹${daySummary.fineGenerated}", TextPrimary)
                        }
                    }
                }

                if (dayRecords.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.CheckCircle,
                            title = s.noRecordsYet,
                            description = DateUtils.formatDisplayDate(selectedDateKey, s.languageCode)
                        )
                    }
                } else {
                    items(dayRecords, key = { it.id }) { rec ->
                        val isPresent = rec.statusEnum() == AttendanceStatus.PRESENT
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = PureWhite),
                            border = BorderStroke(1.dp, CardBorderGrey)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(rec.studentName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Surface(
                                    color = if (isPresent) EmeraldTint else CrimsonTint,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isPresent) s.presentBtn else s.absentBtn,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isPresent) EmeraldOnTint else CrimsonOnTint,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        MlcDatePickerDialog(
            initialDateKey = selectedDateKey,
            onDismiss = { showDatePicker = false },
            onDateSelected = { selectedDateKey = it }
        )
    }
}

@Composable
private fun HistoryStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
