package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.logic.AttendanceFineCalculator
import com.example.data.logic.DateUtils
import com.example.data.model.AppSettings
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.DashboardMetrics
import com.example.data.model.FineRecord
import com.example.data.model.MlcProfile
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.data.model.StudentSummary
import com.example.data.model.UiState
import com.example.data.model.UserAccount
import com.example.data.preferences.LocalPreferences
import com.example.data.repository.BackupPayload
import com.example.data.repository.TuitionRepository
import com.example.data.security.PasswordHasher
import com.example.localization.AppLanguage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: TuitionRepository,
    private val localPrefs: LocalPreferences,
    val currentUserId: String
) : ViewModel() {

    private fun <T> Flow<T>.toUiStateFlow(tag: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $tag", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $tag"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val userAccountState: StateFlow<UiState<UserAccount?>> =
        repository.observeUserAccount(currentUserId).toUiStateFlow("userAccount")

    val mlcProfileState: StateFlow<UiState<MlcProfile?>> =
        repository.observeMlcProfile(currentUserId).toUiStateFlow("mlcProfile")

    val appSettingsState: StateFlow<UiState<AppSettings?>> =
        repository.observeAppSettings(currentUserId).toUiStateFlow("appSettings")

    val studentsState: StateFlow<UiState<List<Student>>> =
        repository.observeStudents(currentUserId).toUiStateFlow("students")

    val attendanceState: StateFlow<UiState<List<AttendanceRecord>>> =
        repository.observeAttendanceRecords(currentUserId).toUiStateFlow("attendance")

    val finesState: StateFlow<UiState<List<FineRecord>>> =
        repository.observeFineRecords(currentUserId).toUiStateFlow("fines")

    val paymentsState: StateFlow<UiState<List<PaymentRecord>>> =
        repository.observePaymentRecords(currentUserId).toUiStateFlow("payments")

    private val _sessionManuallyUnlocked = MutableStateFlow(false)
    private val _sessionManuallyLocked = MutableStateFlow(false)

    val isTeacherSessionUnlocked: StateFlow<Boolean> = combine(
        userAccountState,
        localPrefs.prefsFlow,
        _sessionManuallyUnlocked,
        _sessionManuallyLocked
    ) { accountUi, prefs, manuallyUnlocked, manuallyLocked ->
        if (manuallyLocked) return@combine false
        if (manuallyUnlocked) return@combine true
        val account = (accountUi as? UiState.Success)?.data ?: return@combine false
        account.rememberMe && prefs.unlockedUid == currentUserId
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    val studentSummaries: StateFlow<List<StudentSummary>> = combine(
        studentsState,
        attendanceState,
        finesState,
        paymentsState
    ) { stuUi, attUi, fineUi, payUi ->
        val students = (stuUi as? UiState.Success)?.data.orEmpty()
        val attendance = (attUi as? UiState.Success)?.data.orEmpty()
        val fines = (fineUi as? UiState.Success)?.data.orEmpty()
        val payments = (payUi as? UiState.Success)?.data.orEmpty()
        AttendanceFineCalculator.computeStudentSummaries(students, attendance, fines, payments)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val pendingFineStudents: StateFlow<List<StudentSummary>> = studentSummaries
        .map { list -> list.filter { it.remainingFine > 0L }.sortedByDescending { it.remainingFine } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        studentsState,
        attendanceState,
        finesState,
        paymentsState,
        appSettingsState
    ) { stuUi, attUi, fineUi, payUi, settingsUi ->
        val students = (stuUi as? UiState.Success)?.data.orEmpty()
        val attendance = (attUi as? UiState.Success)?.data.orEmpty()
        val fines = (fineUi as? UiState.Success)?.data.orEmpty()
        val payments = (payUi as? UiState.Success)?.data.orEmpty()
        val settings = (settingsUi as? UiState.Success)?.data
        AttendanceFineCalculator.computeDashboardMetrics(
            students = students,
            attendanceRecords = attendance,
            fineRecords = fines,
            paymentRecords = payments,
            todayKey = DateUtils.todayKey(),
            sundayHolidayEnabled = settings?.sundayHolidayEnabled ?: true
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        DashboardMetrics(0, 0, 0, 0L, 0L, 0, DateUtils.isSunday(DateUtils.todayKey()), 0)
    )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    suspend fun registerTeacherAccount(
        username: String,
        password: String,
        recoveryCode: String,
        rememberMe: Boolean,
        initialLanguage: AppLanguage
    ): Result<Unit> {
        _isBusy.value = true
        return try {
            val pwdHash = PasswordHasher.hashSecret(password)
            val recHash = PasswordHasher.hashSecret(recoveryCode.trim())
            val res = repository.registerTeacherAccount(
                userId = currentUserId,
                username = username,
                passwordHash = pwdHash.hash,
                passwordSalt = pwdHash.salt,
                recoveryHash = recHash.hash,
                recoverySalt = recHash.salt,
                rememberMe = rememberMe
            )
            if (res.isSuccess) {
                repository.saveAppSettings(
                    userId = currentUserId,
                    languageCode = initialLanguage.code,
                    defaultFineAmount = 5L,
                    sundayHolidayEnabled = true,
                    notificationsEnabled = true,
                    existingSettings = null
                )
                _sessionManuallyLocked.value = false
                _sessionManuallyUnlocked.value = true
                if (rememberMe) {
                    localPrefs.setUnlockedUid(currentUserId)
                } else {
                    localPrefs.clearSessionLock()
                }
            }
            res
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun loginTeacher(
        account: UserAccount,
        username: String,
        password: String,
        rememberMe: Boolean
    ): Boolean {
        _isBusy.value = true
        return try {
            val usernameOk = account.username.equals(username.trim(), ignoreCase = true)
            val passwordOk = PasswordHasher.verifySecret(password, account.passwordHash, account.passwordSalt)
            if (usernameOk && passwordOk) {
                if (account.rememberMe != rememberMe) {
                    repository.updateRememberMe(currentUserId, rememberMe)
                }
                _sessionManuallyLocked.value = false
                _sessionManuallyUnlocked.value = true
                if (rememberMe) {
                    localPrefs.setUnlockedUid(currentUserId)
                } else {
                    localPrefs.clearSessionLock()
                }
                true
            } else {
                false
            }
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun resetPasswordWithRecovery(
        account: UserAccount,
        recoveryCode: String,
        newPassword: String
    ): Result<Boolean> {
        _isBusy.value = true
        return try {
            val ok = PasswordHasher.verifySecret(recoveryCode.trim(), account.recoveryHash, account.recoverySalt)
            if (!ok) return Result.success(false)
            val newHash = PasswordHasher.hashSecret(newPassword)
            val updateRes = repository.updatePassword(currentUserId, newHash.hash, newHash.salt)
            updateRes.map { true }
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun changePassword(
        account: UserAccount,
        currentPassword: String,
        newPassword: String
    ): Result<Boolean> {
        _isBusy.value = true
        return try {
            val ok = PasswordHasher.verifySecret(currentPassword, account.passwordHash, account.passwordSalt)
            if (!ok) return Result.success(false)
            val newHash = PasswordHasher.hashSecret(newPassword)
            val updateRes = repository.updatePassword(currentUserId, newHash.hash, newHash.salt)
            updateRes.map { true }
        } finally {
            _isBusy.value = false
        }
    }

    fun lockTeacherSession() {
        viewModelScope.launch {
            _sessionManuallyUnlocked.value = false
            _sessionManuallyLocked.value = true
            localPrefs.clearSessionLock()
        }
    }

    suspend fun saveMlcProfile(
        teacherName: String,
        mlcName: String,
        mlcLocation: String,
        logoData: String?
    ): Result<Unit> {
        _isBusy.value = true
        return try {
            val existing = (mlcProfileState.value as? UiState.Success)?.data
            repository.saveMlcProfile(currentUserId, teacherName, mlcName, mlcLocation, logoData, existing)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun updateSettings(
        language: AppLanguage,
        defaultFineAmount: Long,
        sundayHolidayEnabled: Boolean,
        notificationsEnabled: Boolean
    ): Result<Unit> {
        _isBusy.value = true
        return try {
            localPrefs.saveLanguage(language, markSelected = true)
            val existing = (appSettingsState.value as? UiState.Success)?.data
            repository.saveAppSettings(
                userId = currentUserId,
                languageCode = language.code,
                defaultFineAmount = defaultFineAmount,
                sundayHolidayEnabled = sundayHolidayEnabled,
                notificationsEnabled = notificationsEnabled,
                existingSettings = existing
            )
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun addStudent(fullName: String, parentName: String?, contactNumber: String?): Result<Unit> {
        _isBusy.value = true
        return try {
            val currentStudents = (studentsState.value as? UiState.Success)?.data.orEmpty()
            val code = AttendanceFineCalculator.generateNextStudentCode(currentStudents)
            repository.addStudent(currentUserId, code, fullName, parentName, contactNumber)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun updateStudent(
        studentId: String,
        fullName: String,
        parentName: String?,
        contactNumber: String?
    ): Result<Unit> {
        _isBusy.value = true
        return try {
            repository.updateStudent(currentUserId, studentId, fullName, parentName, contactNumber)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun deleteStudent(studentId: String): Result<Unit> {
        _isBusy.value = true
        return try {
            repository.deleteStudent(currentUserId, studentId)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun saveDailyAttendance(
        dateKey: String,
        statuses: Map<String, AttendanceStatus>
    ): Result<Unit> {
        _isBusy.value = true
        return try {
            val students = (studentsState.value as? UiState.Success)?.data.orEmpty()
            val attendance = (attendanceState.value as? UiState.Success)?.data.orEmpty()
            val fines = (finesState.value as? UiState.Success)?.data.orEmpty()
            val fineAmount = (appSettingsState.value as? UiState.Success)?.data?.defaultFineAmount ?: 5L
            repository.saveDailyAttendanceBatch(
                userId = currentUserId,
                dateKey = dateKey,
                students = students,
                statuses = statuses,
                existingAttendance = attendance,
                existingFines = fines,
                defaultFineAmount = fineAmount
            )
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun collectFinePayment(
        summary: StudentSummary,
        amountPaid: Long,
        note: String?
    ): Result<Unit> {
        _isBusy.value = true
        return try {
            val allFines = (finesState.value as? UiState.Success)?.data.orEmpty()
            val studentOpenFines = allFines.filter { it.studentId == summary.student.id }
            repository.collectFinePayment(
                userId = currentUserId,
                student = summary.student,
                amountPaid = amountPaid,
                currentRemainingFine = summary.remainingFine,
                openFinesForStudent = studentOpenFines,
                note = note
            )
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun restoreBackup(payload: BackupPayload): Result<Unit> {
        _isBusy.value = true
        return try {
            val existingProfile = (mlcProfileState.value as? UiState.Success)?.data
            val existingSettings = (appSettingsState.value as? UiState.Success)?.data
            val existingStudents = (studentsState.value as? UiState.Success)?.data.orEmpty()
            val existingAttendance = (attendanceState.value as? UiState.Success)?.data.orEmpty()
            val existingFines = (finesState.value as? UiState.Success)?.data.orEmpty()
            val existingPayments = (paymentsState.value as? UiState.Success)?.data.orEmpty()
            val res = repository.restoreBackup(
                userId = currentUserId,
                backup = payload,
                existingProfile = existingProfile,
                existingSettings = existingSettings,
                existingStudents = existingStudents,
                existingAttendance = existingAttendance,
                existingFines = existingFines,
                existingPayments = existingPayments
            )
            if (res.isSuccess && payload.appSettings != null) {
                localPrefs.saveLanguage(AppLanguage.fromCode(payload.appSettings.languageCode), true)
            }
            res
        } finally {
            _isBusy.value = false
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
