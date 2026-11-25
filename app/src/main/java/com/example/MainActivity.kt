package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.GoogleAuthHelper
import com.example.data.model.UiState
import com.example.data.preferences.LocalAppPrefs
import com.example.data.preferences.LocalPreferences
import com.example.data.repository.FirestoreTuitionRepository
import com.example.localization.AppLanguage
import com.example.localization.LocalStrings
import com.example.localization.stringsFor
import com.example.ui.AppShell
import com.example.ui.screens.GoogleCloudAuthScreen
import com.example.ui.screens.LanguageSelectionScreen
import com.example.ui.screens.MlcSetupScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TeacherLoginScreen
import com.example.ui.screens.TeacherRegistrationScreen
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.SoftLightGrey
import com.example.ui.theme.TuitionManagementTheme
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TuitionManagementTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localPrefs = remember(context) { LocalPreferences(context.applicationContext) }
    val prefsOrNull by localPrefs.prefsFlow.collectAsStateWithLifecycle(initialValue = null)

    val prefs = prefsOrNull ?: run {
        Box(modifier = Modifier.fillMaxSize().background(DeepNavy), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        }
        return
    }

    var selectedLangBeforeContinue by remember(prefs.language) { mutableStateOf(prefs.language) }
    var splashCompleted by rememberSaveable { mutableStateOf(false) }

    val activeLanguage = if (prefs.languageSelected) prefs.language else selectedLangBeforeContinue
    val strings = remember(activeLanguage) { stringsFor(activeLanguage) }

    CompositionLocalProvider(LocalStrings provides strings) {
        if (!prefs.languageSelected) {
            LanguageSelectionScreen(
                selectedLanguage = selectedLangBeforeContinue,
                onSelectLanguage = { selectedLangBeforeContinue = it },
                onContinue = {
                    scope.launch {
                        localPrefs.saveLanguage(selectedLangBeforeContinue, markSelected = true)
                    }
                }
            )
        } else if (!splashCompleted) {
            SplashScreen(onFinished = { splashCompleted = true })
        } else {
            AuthenticatedGate(
                localPrefs = localPrefs,
                localLanguage = prefs.language
            )
        }
    }
}

@Composable
private fun AuthenticatedGate(
    localPrefs: LocalPreferences,
    localLanguage: AppLanguage
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            GoogleAuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        GoogleCloudAuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            TeacherWorkspaceRoot(
                currentUserId = uid,
                localPrefs = localPrefs,
                localLanguage = localLanguage,
                onSignOutGoogle = {
                    scope.launch {
                        localPrefs.clearSessionLock()
                        GoogleAuthHelper.signOut(credentialManager, scope) {
                            currentUser = null
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun TeacherWorkspaceRoot(
    currentUserId: String,
    localPrefs: LocalPreferences,
    localLanguage: AppLanguage,
    onSignOutGoogle: () -> Unit
) {
    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(
                    repository = FirestoreTuitionRepository(db),
                    localPrefs = localPrefs,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val s = LocalStrings.current
    val userAccountUi by viewModel.userAccountState.collectAsStateWithLifecycle()
    val mlcProfileUi by viewModel.mlcProfileState.collectAsStateWithLifecycle()
    val appSettingsUi by viewModel.appSettingsState.collectAsStateWithLifecycle()
    val studentsUi by viewModel.studentsState.collectAsStateWithLifecycle()
    val attendanceUi by viewModel.attendanceState.collectAsStateWithLifecycle()
    val finesUi by viewModel.finesState.collectAsStateWithLifecycle()
    val paymentsUi by viewModel.paymentsState.collectAsStateWithLifecycle()

    val isUnlocked by viewModel.isTeacherSessionUnlocked.collectAsStateWithLifecycle()
    val summaries by viewModel.studentSummaries.collectAsStateWithLifecycle()
    val pendingFines by viewModel.pendingFineStudents.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val cloudSettings = (appSettingsUi as? UiState.Success)?.data
    val effectiveLanguage = cloudSettings?.let { AppLanguage.fromCode(it.languageCode) } ?: localLanguage
    val syncedStrings = remember(effectiveLanguage) { stringsFor(effectiveLanguage) }

    // Keep local DataStore language aligned with cloud preference
    LaunchedEffect(cloudSettings?.languageCode) {
        val code = cloudSettings?.languageCode ?: return@LaunchedEffect
        if (code != localLanguage.code) {
            localPrefs.saveLanguage(AppLanguage.fromCode(code), markSelected = true)
        }
    }

    CompositionLocalProvider(LocalStrings provides syncedStrings) {
        when (val accState = userAccountUi) {
            is UiState.Loading -> LoadingWorkspaceView(syncedStrings.loadingLabel)
            is UiState.Error -> ErrorWorkspaceView(accState.message, onSignOutGoogle)
            is UiState.Success -> {
                val account = accState.data
                if (account == null) {
                    TeacherRegistrationScreen(
                        isBusy = isBusy,
                        onCreateAccount = { u, p, rec, rem ->
                            viewModel.registerTeacherAccount(u, p, rec, rem, effectiveLanguage)
                        },
                        onSignOutGoogle = onSignOutGoogle
                    )
                } else if (!isUnlocked) {
                    TeacherLoginScreen(
                        account = account,
                        isBusy = isBusy,
                        onLogin = { u, p, rem -> viewModel.loginTeacher(account, u, p, rem) },
                        onResetPassword = { rec, newP -> viewModel.resetPasswordWithRecovery(account, rec, newP) },
                        onSignOutGoogle = onSignOutGoogle
                    )
                } else {
                    when (val profileState = mlcProfileUi) {
                        is UiState.Loading -> LoadingWorkspaceView(syncedStrings.loadingLabel)
                        is UiState.Error -> ErrorWorkspaceView(profileState.message, onSignOutGoogle)
                        is UiState.Success -> {
                            val profile = profileState.data
                            if (profile == null) {
                                MlcSetupScreen(
                                    isBusy = isBusy,
                                    onSaveMlcProfile = { t, m, l, logo ->
                                        viewModel.saveMlcProfile(t, m, l, logo)
                                    }
                                )
                            } else {
                                AppShell(
                                    userAccount = account,
                                    mlcProfile = profile,
                                    settings = cloudSettings,
                                    currentLanguage = effectiveLanguage,
                                    metrics = metrics,
                                    students = (studentsUi as? UiState.Success)?.data.orEmpty(),
                                    studentSummaries = summaries,
                                    pendingFineStudents = pendingFines,
                                    attendanceRecords = (attendanceUi as? UiState.Success)?.data.orEmpty(),
                                    fineRecords = (finesUi as? UiState.Success)?.data.orEmpty(),
                                    paymentRecords = (paymentsUi as? UiState.Success)?.data.orEmpty(),
                                    isBusy = isBusy,
                                    onAddStudent = { n, p, c -> viewModel.addStudent(n, p, c) },
                                    onUpdateStudent = { id, n, p, c -> viewModel.updateStudent(id, n, p, c) },
                                    onDeleteStudent = { id -> viewModel.deleteStudent(id) },
                                    onSaveAttendance = { d, map -> viewModel.saveDailyAttendance(d, map) },
                                    onCollectPayment = { sum, amt, note -> viewModel.collectFinePayment(sum, amt, note) },
                                    onChangePassword = { cur, newP -> viewModel.changePassword(account, cur, newP) },
                                    onLockTeacherSession = { viewModel.lockTeacherSession() },
                                    onSignOutGoogle = onSignOutGoogle,
                                    onSaveMlcProfile = { t, m, l, logo -> viewModel.saveMlcProfile(t, m, l, logo) },
                                    onUpdateAppSettings = { lang, fine, sun, notif ->
                                        viewModel.updateSettings(lang, fine, sun, notif)
                                    },
                                    onRestoreBackup = { payload -> viewModel.restoreBackup(payload) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingWorkspaceView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftLightGrey),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = DeepNavy)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorWorkspaceView(message: String, onSignOutGoogle: () -> Unit) {
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftLightGrey)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onSignOutGoogle) {
                Text(s.switchGoogleAccount)
            }
        }
    }
}
