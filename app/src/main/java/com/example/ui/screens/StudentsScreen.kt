package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.logic.DateUtils
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.data.model.StudentSummary
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.AmberOnTint
import com.example.ui.theme.AmberTint
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
fun StudentsScreen(
    summaries: List<StudentSummary>,
    attendanceRecords: List<AttendanceRecord>,
    paymentRecords: List<PaymentRecord>,
    isBusy: Boolean,
    onAddStudent: suspend (fullName: String, parentName: String?, contactNumber: String?) -> Result<Unit>,
    onUpdateStudent: suspend (studentId: String, fullName: String, parentName: String?, contactNumber: String?) -> Result<Unit>,
    onDeleteStudent: suspend (studentId: String) -> Result<Unit>,
    onShowMessage: (String) -> Unit
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var ascending by remember { mutableStateOf(true) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var deletingStudent by remember { mutableStateOf<Student?>(null) }
    var selectedStudentId by remember { mutableStateOf<String?>(null) }

    val selectedSummary = remember(selectedStudentId, summaries) {
        summaries.firstOrNull { it.student.id == selectedStudentId }
    }

    if (selectedSummary != null) {
        BackHandler { selectedStudentId = null }
        StudentProfileView(
            summary = selectedSummary,
            attendanceHistory = attendanceRecords
                .filter { it.studentId == selectedSummary.student.id }
                .sortedByDescending { it.dateKey },
            paymentHistory = paymentRecords
                .filter { it.studentId == selectedSummary.student.id }
                .sortedByDescending { it.paymentDateKey },
            onBack = { selectedStudentId = null }
        )
        return
    }

    val filtered = remember(summaries, searchQuery, ascending) {
        val q = searchQuery.trim()
        val list = if (q.isEmpty()) summaries else summaries.filter {
            it.student.fullName.contains(q, ignoreCase = true) ||
                it.student.studentCode.contains(q, ignoreCase = true)
        }
        if (ascending) list.sortedBy { it.student.fullName.lowercase() }
        else list.sortedByDescending { it.student.fullName.lowercase() }
    }

    Scaffold(
        containerColor = SoftLightGrey,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingStudent = null
                    showAddEditDialog = true
                },
                containerColor = EmeraldGreen,
                contentColor = PureWhite,
                modifier = Modifier.testTag("add_student_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = s.addNewStudent)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(s.searchStudentHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("student_search_input")
                    )
                    Surface(
                        onClick = { ascending = !ascending },
                        shape = RoundedCornerShape(14.dp),
                        color = PureWhite,
                        border = BorderStroke(1.dp, CardBorderGrey),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("student_sort_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.SortByAlpha, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (ascending) s.sortAZ else s.sortZA,
                                style = MaterialTheme.typography.labelLarge,
                                color = DeepNavy
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Groups,
                        title = s.noStudentsTitle,
                        description = s.noStudentsDesc
                    )
                }
            } else {
                items(filtered, key = { it.student.id }) { summary ->
                    val stu = summary.student
                    Card(
                        onClick = { selectedStudentId = stu.id },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_card_${stu.id}"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = BorderStroke(1.dp, CardBorderGrey)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(DeepNavy.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stu.fullName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DeepNavy,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stu.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        color = SoftLightGrey,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = stu.studentCode,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "${s.presentBtn}: ${summary.presentDays}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EmeraldOnTint
                                    )
                                    Text(
                                        text = "${s.absentBtn}: ${summary.absentDays}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CrimsonOnTint
                                    )
                                    if (summary.remainingFine > 0) {
                                        Surface(color = AmberTint, shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                text = "₹${summary.remainingFine} ${s.pendingAmountLabel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AmberOnTint,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    editingStudent = stu
                                    showAddEditDialog = true
                                }
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = s.editStudent, tint = DeepNavy)
                            }
                            IconButton(
                                onClick = { deletingStudent = stu }
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = s.deleteStudent, tint = CrimsonRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditStudentDialog(
            existing = editingStudent,
            isBusy = isBusy,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, parent, contact ->
                scope.launch {
                    val res = if (editingStudent == null) {
                        onAddStudent(name, parent, contact)
                    } else {
                        onUpdateStudent(editingStudent!!.id, name, parent, contact)
                    }
                    res.onSuccess {
                        showAddEditDialog = false
                    }.onFailure {
                        onShowMessage(it.localizedMessage ?: "Error")
                    }
                }
            }
        )
    }

    if (deletingStudent != null) {
        val target = deletingStudent!!
        AlertDialog(
            onDismissRequest = { deletingStudent = null },
            title = { Text(s.deleteStudent) },
            text = { Text(String.format(s.deleteStudentConfirm, target.fullName)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            onDeleteStudent(target.id)
                            deletingStudent = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) { Text(s.deleteBtn) }
            },
            dismissButton = {
                TextButton(onClick = { deletingStudent = null }) { Text(s.cancelBtn) }
            }
        )
    }
}

@Composable
private fun AddEditStudentDialog(
    existing: Student?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, parent: String?, contact: String?) -> Unit
) {
    val s = LocalStrings.current
    var fullName by remember(existing) { mutableStateOf(existing?.fullName ?: "") }
    var parentName by remember(existing) { mutableStateOf(existing?.parentName ?: "") }
    var contact by remember(existing) { mutableStateOf(existing?.contactNumber ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) s.addNewStudent else s.editStudent) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(s.studentFullNameLabel) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_name_input")
                )
                OutlinedTextField(
                    value = parentName,
                    onValueChange = { parentName = it },
                    label = { Text(s.parentNameOptionalLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text(s.contactOptionalLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err != null) {
                    Text(err!!, color = CrimsonRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isBlank()) {
                        err = s.requiredFieldsErr
                    } else {
                        err = null
                        onSave(fullName, parentName.takeIf { it.isNotBlank() }, contact.takeIf { it.isNotBlank() })
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_student_button")
            ) { Text(s.saveBtn) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancelBtn) }
        }
    )
}

@Composable
private fun StudentProfileView(
    summary: StudentSummary,
    attendanceHistory: List<AttendanceRecord>,
    paymentHistory: List<PaymentRecord>,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val stu = summary.student

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftLightGrey),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.cancelBtn)
                }
                Text(
                    text = s.studentProfileTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DeepNavy)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stu.fullName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = EmeraldGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stu.studentCode,
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (!stu.parentName.isNullOrBlank()) {
                        Text(
                            text = stu.parentName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureWhite.copy(alpha = 0.85f)
                        )
                    }
                    if (!stu.contactNumber.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Phone, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                            Text(
                                text = stu.contactNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = PureWhite.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileStatBox(s.totalPresentDays, summary.presentDays.toString(), EmeraldOnTint, EmeraldTint, Modifier.weight(1f))
                    ProfileStatBox(s.totalAbsentDays, summary.absentDays.toString(), CrimsonOnTint, CrimsonTint, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileStatBox(s.totalFineGenerated, "₹${summary.totalFineGenerated}", DeepNavy, PureWhite, Modifier.weight(1f))
                    ProfileStatBox(s.totalFinePaid, "₹${summary.totalFinePaid}", EmeraldOnTint, PureWhite, Modifier.weight(1f))
                    ProfileStatBox(s.remainingFine, "₹${summary.remainingFine}", AmberOnTint, AmberTint, Modifier.weight(1f))
                }
                if (summary.creditBalance > 0L) {
                    Surface(
                        color = EmeraldTint,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${s.creditBalanceLabel}: ₹${summary.creditBalance}",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldOnTint,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = s.attendanceHistoryTitle,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
        if (attendanceHistory.isEmpty()) {
            item { Text(s.noRecordsYet, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        } else {
            items(attendanceHistory, key = { it.id }) { rec ->
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
                        Text(
                            text = DateUtils.formatDisplayDate(rec.dateKey, s.languageCode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
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

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = s.paymentHistoryTitle,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
        if (paymentHistory.isEmpty()) {
            item { Text(s.noRecordsYet, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        } else {
            items(paymentHistory, key = { it.id }) { pay ->
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
                                text = "${DateUtils.formatDisplayDate(pay.paymentDateKey, s.languageCode)} • ${pay.paymentTimeText}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${s.remainingAfterPayment}: ₹${pay.remainingAfter}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "+₹${pay.amountPaid}",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldOnTint,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatBox(
    label: String,
    value: String,
    textColor: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, CardBorderGrey)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleLarge, color = textColor, fontWeight = FontWeight.Bold)
        }
    }
}
