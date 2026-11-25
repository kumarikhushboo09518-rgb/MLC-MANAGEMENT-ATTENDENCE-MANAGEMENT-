package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppSettings
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.DashboardMetrics
import com.example.data.model.FineRecord
import com.example.data.model.MlcProfile
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.data.model.StudentSummary
import com.example.data.model.UserAccount
import com.example.data.repository.BackupPayload
import com.example.localization.AppLanguage
import com.example.localization.LocalStrings
import com.example.ui.components.BrandLogoAvatar
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PaymentHistoryScreen
import com.example.ui.screens.PendingFinesScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudentsScreen
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.DeepNavyContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldTint
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftLightGrey
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class AppSection(val icon: ImageVector, val testTag: String) {
    DASHBOARD(Icons.Rounded.Dashboard, "nav_dashboard"),
    STUDENTS(Icons.Rounded.Groups, "nav_students"),
    ATTENDANCE(Icons.AutoMirrored.Rounded.FactCheck, "nav_attendance"),
    PENDING_FINES(Icons.Rounded.AccountBalanceWallet, "nav_pending_fines"),
    PAYMENT_HISTORY(Icons.AutoMirrored.Rounded.ReceiptLong, "nav_payment_history"),
    REPORTS(Icons.Rounded.Assessment, "nav_reports"),
    SETTINGS(Icons.Rounded.Settings, "nav_settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    userAccount: UserAccount,
    mlcProfile: MlcProfile,
    settings: AppSettings?,
    currentLanguage: AppLanguage,
    metrics: DashboardMetrics,
    students: List<Student>,
    studentSummaries: List<StudentSummary>,
    pendingFineStudents: List<StudentSummary>,
    attendanceRecords: List<AttendanceRecord>,
    fineRecords: List<FineRecord>,
    paymentRecords: List<PaymentRecord>,
    isBusy: Boolean,
    onAddStudent: suspend (String, String?, String?) -> Result<Unit>,
    onUpdateStudent: suspend (String, String, String?, String?) -> Result<Unit>,
    onDeleteStudent: suspend (String) -> Result<Unit>,
    onSaveAttendance: suspend (String, Map<String, AttendanceStatus>) -> Result<Unit>,
    onCollectPayment: suspend (StudentSummary, Long, String?) -> Result<Unit>,
    onChangePassword: suspend (String, String) -> Result<Boolean>,
    onLockTeacherSession: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onSaveMlcProfile: suspend (String, String, String, String?) -> Result<Unit>,
    onUpdateAppSettings: suspend (AppLanguage, Long, Boolean, Boolean) -> Result<Unit>,
    onRestoreBackup: suspend (BackupPayload) -> Result<Unit>
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentSection by rememberSaveable { mutableStateOf(AppSection.DASHBOARD) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    BackHandler(enabled = currentSection != AppSection.DASHBOARD) {
        currentSection = AppSection.DASHBOARD
    }

    fun sectionLabel(sec: AppSection): String = when (sec) {
        AppSection.DASHBOARD -> s.navDashboard
        AppSection.STUDENTS -> s.navStudents
        AppSection.ATTENDANCE -> s.navAttendance
        AppSection.PENDING_FINES -> s.navPendingFines
        AppSection.PAYMENT_HISTORY -> s.navPaymentHistory
        AppSection.REPORTS -> s.navReports
        AppSection.SETTINGS -> s.navSettings
    }

    val showMessage: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    @Composable
    fun MainSectionContent() {
        when (currentSection) {
            AppSection.DASHBOARD -> DashboardScreen(
                mlcProfile = mlcProfile,
                settings = settings,
                metrics = metrics,
                students = students,
                attendanceRecords = attendanceRecords,
                fineRecords = fineRecords,
                onNavigateAttendance = { currentSection = AppSection.ATTENDANCE },
                onNavigatePendingFines = { currentSection = AppSection.PENDING_FINES },
                onNavigatePaymentHistory = { currentSection = AppSection.PAYMENT_HISTORY },
                onNavigateStudents = { currentSection = AppSection.STUDENTS }
            )
            AppSection.STUDENTS -> StudentsScreen(
                summaries = studentSummaries,
                attendanceRecords = attendanceRecords,
                paymentRecords = paymentRecords,
                isBusy = isBusy,
                onAddStudent = onAddStudent,
                onUpdateStudent = onUpdateStudent,
                onDeleteStudent = onDeleteStudent,
                onShowMessage = showMessage
            )
            AppSection.ATTENDANCE -> AttendanceScreen(
                students = students,
                attendanceRecords = attendanceRecords,
                fineRecords = fineRecords,
                settings = settings,
                isBusy = isBusy,
                onSaveAttendance = onSaveAttendance,
                onShowMessage = showMessage
            )
            AppSection.PENDING_FINES -> PendingFinesScreen(
                pendingStudents = pendingFineStudents,
                isBusy = isBusy,
                onCollectPayment = onCollectPayment,
                onShowMessage = showMessage
            )
            AppSection.PAYMENT_HISTORY -> PaymentHistoryScreen(
                paymentRecords = paymentRecords
            )
            AppSection.REPORTS -> ReportsScreen(
                mlcProfile = mlcProfile,
                students = students,
                summaries = studentSummaries,
                attendanceRecords = attendanceRecords,
                fineRecords = fineRecords,
                paymentRecords = paymentRecords,
                onShowMessage = showMessage
            )
            AppSection.SETTINGS -> SettingsScreen(
                userAccount = userAccount,
                mlcProfile = mlcProfile,
                settings = settings,
                currentLanguage = currentLanguage,
                students = students,
                attendanceRecords = attendanceRecords,
                fineRecords = fineRecords,
                paymentRecords = paymentRecords,
                isBusy = isBusy,
                onChangePassword = onChangePassword,
                onLockTeacherSession = onLockTeacherSession,
                onSignOutGoogle = onSignOutGoogle,
                onSaveMlcProfile = onSaveMlcProfile,
                onUpdateAppSettings = onUpdateAppSettings,
                onRestoreBackup = onRestoreBackup,
                onShowMessage = showMessage
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            // Desktop / Tablet Sidebar Navigation
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(DeepNavy)
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        BrandLogoAvatar(logoBase64 = mlcProfile.logoData, size = 44.dp)
                        Column {
                            Text(s.organizationName, style = MaterialTheme.typography.labelSmall, color = EmeraldGreen)
                            Text(
                                mlcProfile.mlcName,
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(color = PureWhite.copy(alpha = 0.14f), modifier = Modifier.padding(vertical = 4.dp))

                    AppSection.entries.forEach { sec ->
                        val selected = currentSection == sec
                        NavigationDrawerItem(
                            selected = selected,
                            onClick = { currentSection = sec },
                            icon = { Icon(sec.icon, contentDescription = sectionLabel(sec)) },
                            label = { Text(sectionLabel(sec), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = EmeraldGreen,
                                selectedIconColor = PureWhite,
                                selectedTextColor = PureWhite,
                                unselectedContainerColor = DeepNavy,
                                unselectedIconColor = PureWhite.copy(alpha = 0.78f),
                                unselectedTextColor = PureWhite.copy(alpha = 0.85f)
                            ),
                            modifier = Modifier.testTag(sec.testTag)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = mlcProfile.teacherName,
                        style = MaterialTheme.typography.labelMedium,
                        color = PureWhite.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = s.createdBy,
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite.copy(alpha = 0.5f)
                    )
                }

                Scaffold(
                    containerColor = SoftLightGrey,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = sectionLabel(currentSection),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = PureWhite,
                                titleContentColor = TextPrimary
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        MainSectionContent()
                    }
                }
            }
        } else {
            // Mobile Responsive Bottom Bar + Full 7-Section Navigation Drawer
            val bottomSections = listOf(
                AppSection.DASHBOARD,
                AppSection.ATTENDANCE,
                AppSection.STUDENTS,
                AppSection.PENDING_FINES,
                AppSection.REPORTS
            )

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = DeepNavy,
                        drawerContentColor = PureWhite
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                BrandLogoAvatar(logoBase64 = mlcProfile.logoData, size = 48.dp)
                                Column {
                                    Text(s.organizationName, style = MaterialTheme.typography.labelSmall, color = EmeraldGreen)
                                    Text(mlcProfile.mlcName, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                    Text(mlcProfile.teacherName, style = MaterialTheme.typography.bodySmall, color = PureWhite.copy(alpha = 0.75f))
                                }
                            }
                            HorizontalDivider(color = PureWhite.copy(alpha = 0.14f), modifier = Modifier.padding(vertical = 4.dp))

                            AppSection.entries.forEach { sec ->
                                NavigationDrawerItem(
                                    selected = currentSection == sec,
                                    onClick = {
                                        currentSection = sec
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Icon(sec.icon, contentDescription = sectionLabel(sec)) },
                                    label = { Text(sectionLabel(sec)) },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = EmeraldGreen,
                                        selectedIconColor = PureWhite,
                                        selectedTextColor = PureWhite,
                                        unselectedContainerColor = DeepNavyContainer.copy(alpha = 0.25f),
                                        unselectedIconColor = PureWhite.copy(alpha = 0.8f),
                                        unselectedTextColor = PureWhite.copy(alpha = 0.9f)
                                    ),
                                    modifier = Modifier.testTag("drawer_${sec.testTag}")
                                )
                            }
                        }
                    }
                }
            ) {
                Scaffold(
                    containerColor = SoftLightGrey,
                    contentWindowInsets = WindowInsets.navigationBars,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_menu_button")
                                ) {
                                    Icon(Icons.Rounded.Menu, contentDescription = s.navMenu, tint = DeepNavy)
                                }
                            },
                            title = {
                                Column {
                                    Text(
                                        text = sectionLabel(currentSection),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = mlcProfile.mlcName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { currentSection = AppSection.PAYMENT_HISTORY },
                                    modifier = Modifier.testTag("topbar_payments_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = s.navPaymentHistory, tint = DeepNavy)
                                }
                                IconButton(
                                    onClick = { currentSection = AppSection.SETTINGS },
                                    modifier = Modifier.testTag("topbar_settings_button")
                                ) {
                                    Icon(Icons.Rounded.Settings, contentDescription = s.navSettings, tint = DeepNavy)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = PureWhite,
                                titleContentColor = TextPrimary
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = PureWhite,
                            tonalElevation = 4.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            bottomSections.forEach { sec ->
                                val selected = currentSection == sec
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentSection = sec },
                                    icon = { Icon(sec.icon, contentDescription = sectionLabel(sec)) },
                                    label = {
                                        Text(
                                            text = sectionLabel(sec),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DeepNavy,
                                        selectedTextColor = DeepNavy,
                                        indicatorColor = EmeraldTint,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(sec.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        MainSectionContent()
                    }
                }
            }
        }
    }
}
