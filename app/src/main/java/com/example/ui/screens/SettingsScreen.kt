package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.export.ImageUtils
import com.example.data.export.ReportExporter
import com.example.data.logic.DateUtils
import com.example.data.model.AppSettings
import com.example.data.model.AttendanceRecord
import com.example.data.model.FineRecord
import com.example.data.model.MlcProfile
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.data.model.UserAccount
import com.example.data.repository.BackupPayload
import com.example.localization.AppLanguage
import com.example.localization.LocalStrings
import com.example.ui.components.BrandLogoAvatar
import com.example.ui.theme.CardBorderGrey
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    userAccount: UserAccount?,
    mlcProfile: MlcProfile?,
    settings: AppSettings?,
    currentLanguage: AppLanguage,
    students: List<Student>,
    attendanceRecords: List<AttendanceRecord>,
    fineRecords: List<FineRecord>,
    paymentRecords: List<PaymentRecord>,
    isBusy: Boolean,
    onChangePassword: suspend (currentPwd: String, newPwd: String) -> Result<Boolean>,
    onLockTeacherSession: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onSaveMlcProfile: suspend (teacherName: String, mlcName: String, mlcLocation: String, logoData: String?) -> Result<Unit>,
    onUpdateAppSettings: suspend (language: AppLanguage, defaultFine: Long, sundayHoliday: Boolean, notifications: Boolean) -> Result<Unit>,
    onRestoreBackup: suspend (BackupPayload) -> Result<Unit>,
    onShowMessage: (String) -> Unit
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // MLC Editable state
    var teacherName by remember(mlcProfile) { mutableStateOf(mlcProfile?.teacherName ?: "") }
    var mlcName by remember(mlcProfile) { mutableStateOf(mlcProfile?.mlcName ?: "") }
    var mlcLocation by remember(mlcProfile) { mutableStateOf(mlcProfile?.mlcLocation ?: "") }
    var logoData by remember(mlcProfile) { mutableStateOf(mlcProfile?.logoData) }

    // App Settings Editable state
    var fineAmountStr by remember(settings) { mutableStateOf((settings?.defaultFineAmount ?: 5L).toString()) }
    var sundayHoliday by remember(settings) { mutableStateOf(settings?.sundayHolidayEnabled ?: true) }
    var notifications by remember(settings) { mutableStateOf(settings?.notificationsEnabled ?: true) }

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            logoData = ImageUtils.compressLogoToBase64(context, uri)
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val bytes = pendingSaveBytes
        if (uri != null && bytes != null) {
            if (ReportExporter.writeUriBytes(context, uri, bytes)) {
                onShowMessage(s.backupSuccessMsg)
            }
        }
        pendingSaveBytes = null
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val bytes = pendingSaveBytes
        if (uri != null && bytes != null) {
            if (ReportExporter.writeUriBytes(context, uri, bytes)) {
                onShowMessage(s.exportSuccessMsg)
            }
        }
        pendingSaveBytes = null
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = ReportExporter.readUriText(context, uri)
            val payload = raw?.let { ReportExporter.parseBackupJson(it) }
            if (payload == null) {
                onShowMessage(s.invalidBackupErr)
            } else {
                scope.launch {
                    onRestoreBackup(payload)
                        .onSuccess { onShowMessage(s.restoreSuccessMsg) }
                        .onFailure { onShowMessage(it.localizedMessage ?: "Error") }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Account Settings
        item {
            SettingsCard(title = s.accountSettingsSection) {
                Text(
                    text = "${s.usernameLabel}: ${userAccount?.username ?: "-"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showChangePasswordDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("change_password_button")
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.changePasswordBtn)
                    }
                    Button(
                        onClick = onLockTeacherSession,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("logout_teacher_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepNavy)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.logoutBtn)
                    }
                }
                TextButton(
                    onClick = onSignOutGoogle,
                    modifier = Modifier.testTag("sign_out_google_button")
                ) {
                    Text(s.fullSignOutBtn, color = CrimsonRed)
                }
            }
        }

        // 2. MLC Settings
        item {
            SettingsCard(title = s.mlcSettingsSection) {
                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text(s.teacherNameLabel) },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mlcName,
                    onValueChange = { mlcName = it },
                    label = { Text(s.mlcNameLabel) },
                    leadingIcon = { Icon(Icons.Rounded.Business, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mlcLocation,
                    onValueChange = { mlcLocation = it },
                    label = { Text(s.mlcLocationLabel) },
                    leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BrandLogoAvatar(logoBase64 = logoData, size = 48.dp)
                    OutlinedButton(
                        onClick = {
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.uploadLogoBtn)
                    }
                    if (logoData != null) {
                        TextButton(onClick = { logoData = null }) { Text(s.removeLogoBtn) }
                    }
                }
                Button(
                    onClick = {
                        if (teacherName.isBlank() || mlcName.isBlank() || mlcLocation.isBlank()) {
                            onShowMessage(s.requiredFieldsErr)
                        } else {
                            scope.launch {
                                onSaveMlcProfile(teacherName, mlcName, mlcLocation, logoData)
                                    .onSuccess { onShowMessage(s.settingsSavedMsg) }
                                    .onFailure { onShowMessage(it.localizedMessage ?: "Error") }
                            }
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text(s.updateMlcBtn)
                }
            }
        }

        // 3. App Settings
        item {
            SettingsCard(title = s.appSettingsSection) {
                Text(s.languageSettingLabel, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = currentLanguage == lang,
                            onClick = {
                                scope.launch {
                                    val fineVal = fineAmountStr.toLongOrNull()?.coerceIn(1L, 1000L) ?: 5L
                                    onUpdateAppSettings(lang, fineVal, sundayHoliday, notifications)
                                        .onSuccess { onShowMessage(s.settingsSavedMsg) }
                                }
                            },
                            label = { Text(lang.nativeLabel) },
                            modifier = Modifier.testTag("settings_lang_${lang.code}")
                        )
                    }
                }

                OutlinedTextField(
                    value = fineAmountStr,
                    onValueChange = { fineAmountStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text(s.defaultFineSettingLabel) },
                    leadingIcon = { Icon(Icons.Rounded.CurrencyRupee, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("default_fine_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.sundayHolidaySettingLabel, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(s.sundayHolidaySettingDesc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = sundayHoliday,
                        onCheckedChange = { sundayHoliday = it },
                        modifier = Modifier.testTag("sunday_holiday_setting_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.notificationsSettingLabel, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(s.notificationsSettingDesc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = notifications,
                        onCheckedChange = { notifications = it }
                    )
                }

                Button(
                    onClick = {
                        val fineVal = (fineAmountStr.toLongOrNull() ?: 5L).coerceIn(1L, 1000L)
                        fineAmountStr = fineVal.toString()
                        scope.launch {
                            onUpdateAppSettings(currentLanguage, fineVal, sundayHoliday, notifications)
                                .onSuccess { onShowMessage(s.settingsSavedMsg) }
                                .onFailure { onShowMessage(it.localizedMessage ?: "Error") }
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_app_settings_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepNavy)
                ) {
                    Text(s.saveBtn)
                }
            }
        }

        // 4. Data & Backup Settings
        item {
            val todayKey = remember { DateUtils.todayKey() }
            SettingsCard(title = s.dataSettingsSection) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val bytes = ReportExporter.serializeBackupJson(
                                mlcProfile, settings, students, attendanceRecords, fineRecords, paymentRecords
                            )
                            pendingSaveBytes = bytes
                            createBackupLauncher.launch("malabar_mlc_backup_$todayKey.json")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_data_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepNavy)
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.backupDataBtn)
                    }
                    OutlinedButton(
                        onClick = {
                            restoreBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restore_data_button")
                    ) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.restoreDataBtn)
                    }
                }

                HorizontalDivider(color = CardBorderGrey)

                OutlinedButton(
                    onClick = {
                        val bytes = ReportExporter.buildCsv(
                            listOf("Student ID", "Full Name", "Parent", "Contact", "Active"),
                            students.map { listOf(it.studentCode, it.fullName, it.parentName ?: "", it.contactNumber ?: "", "${it.active}") }
                        )
                        pendingSaveBytes = bytes
                        createCsvLauncher.launch("mlc_students_$todayKey.csv")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(s.exportStudentsBtn)
                }

                OutlinedButton(
                    onClick = {
                        val bytes = ReportExporter.buildCsv(
                            listOf("Date", "Student ID", "Student Name", "Status"),
                            attendanceRecords.sortedByDescending { it.dateKey }.map {
                                listOf(it.dateKey, it.studentId, it.studentName, it.status)
                            }
                        )
                        pendingSaveBytes = bytes
                        createCsvLauncher.launch("mlc_attendance_$todayKey.csv")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(s.exportAttendanceBtn)
                }

                OutlinedButton(
                    onClick = {
                        val bytes = ReportExporter.buildCsv(
                            listOf("Date", "Time", "Student Name", "Amount Paid", "Remaining After", "Note"),
                            paymentRecords.sortedByDescending { it.paymentDateKey }.map {
                                listOf(it.paymentDateKey, it.paymentTimeText, it.studentName, "${it.amountPaid}", "${it.remainingAfter}", it.note ?: "")
                            }
                        )
                        pendingSaveBytes = bytes
                        createCsvLauncher.launch("mlc_fines_payments_$todayKey.csv")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(s.exportFinesPaymentsBtn)
                }
            }
        }
    }

    if (showChangePasswordDialog) {
        var currentPwd by remember { mutableStateOf("") }
        var newPwd by remember { mutableStateOf("") }
        var confirmPwd by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text(s.changePasswordBtn) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPwd,
                        onValueChange = { currentPwd = it },
                        label = { Text(s.currentPasswordLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPwd,
                        onValueChange = { newPwd = it },
                        label = { Text(s.newPasswordLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPwd,
                        onValueChange = { confirmPwd = it },
                        label = { Text(s.confirmPasswordLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (err != null) {
                        Text(err!!, color = CrimsonRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when {
                        newPwd.length < 6 -> err = s.passwordLengthErr
                        newPwd != confirmPwd -> err = s.passwordMismatchErr
                        else -> scope.launch {
                            val res = onChangePassword(currentPwd, newPwd)
                            res.onSuccess { ok ->
                                if (ok) {
                                    showChangePasswordDialog = false
                                    onShowMessage(s.settingsSavedMsg)
                                } else {
                                    err = s.invalidCredentialsErr
                                }
                            }.onFailure { err = it.localizedMessage }
                        }
                    }
                }) { Text(s.saveBtn) }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text(s.cancelBtn) }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, CardBorderGrey)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = DeepNavy,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}
