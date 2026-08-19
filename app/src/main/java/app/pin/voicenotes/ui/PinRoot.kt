package app.pin.voicenotes.ui

/*
 * ── বাংলা ব্যাখ্যা ──────────────────────────────────────────────
 * অ্যাপের কঙ্কাল: ৩টা ট্যাব (Home/Notes/Reminders) + মাঝখানে একটাই বড়
 * মাইক বাটন — সব স্ক্রিনে একই capture ব্যবস্থা, ছড়ানো-ছিটানো বাটন নেই।
 * মাইক চাপলে আগে RECORD_AUDIO পারমিশন দেখা হয়; deny করলে টাইপিং মোড খোলে।
 * নোটিফিকেশন ট্যাপ → এখান থেকেই সরাসরি সেই নোটের পাতায় নেভিগেট হয়।
 * Snackbar-এর Undo সহ সব ইভেন্ট এই এক জায়গায় জড়ো হয়।
 */

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import app.pin.voicenotes.R
import app.pin.voicenotes.ui.capture.CaptureEvent
import app.pin.voicenotes.ui.capture.CaptureSheet
import app.pin.voicenotes.ui.capture.CaptureViewModel
import app.pin.voicenotes.ui.detail.NoteDetailScreen
import app.pin.voicenotes.ui.home.HomeScreen
import app.pin.voicenotes.ui.notes.NotesScreen
import app.pin.voicenotes.ui.reminders.RemindersScreen
import app.pin.voicenotes.ui.settings.SettingsSheet
import app.pin.voicenotes.ui.theme.PinColors
import app.pin.voicenotes.ui.theme.PinIcons
import kotlinx.coroutines.launch

private val topLevelRoutes = setOf("home", "notes", "reminders")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinRoot(
    requestedNoteId: Long?,
    onNoteRequestConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val captureViewModel: CaptureViewModel = viewModel(factory = AppViewModelProvider.Factory)

    var showSettings by remember { mutableStateOf(false) }

    // Reminder notification tap → open that exact note.
    LaunchedEffect(requestedNoteId) {
        if (requestedNoteId != null) {
            navController.navigate("note/$requestedNoteId") {
                launchSingleTop = true
            }
            onNoteRequestConsumed()
        }
    }

    val showSnackbar: (String, String?, (() -> Unit)?) -> Unit = { message, label, action ->
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = label,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) action?.invoke()
        }
    }

    // Capture flow events: snackbars, navigation, permission requests.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { captureViewModel.onNotificationPermissionResult() }

    LaunchedEffect(Unit) {
        captureViewModel.events.collect { event ->
            when (event) {
                is CaptureEvent.Snackbar ->
                    showSnackbar(event.message, event.actionLabel, event.action)
                is CaptureEvent.NavigateToNote ->
                    navController.navigate("note/${event.id}") { launchSingleTop = true }
                is CaptureEvent.NavigateRoute ->
                    navController.navigate(event.route) { launchSingleTop = true }
                CaptureEvent.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    } else {
                        captureViewModel.onNotificationPermissionResult()
                    }
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) captureViewModel.startVoice()
        else captureViewModel.onMicPermissionDenied()
    }

    val startCapture: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) captureViewModel.startVoice()
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("?")
    val onTopLevel = currentRoute in topLevelRoutes

    Scaffold(
        containerColor = PinColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (onTopLevel) {
                PinBottomBar(navController, currentRoute)
            }
        },
        floatingActionButton = {
            if (onTopLevel) {
                MicButton(onClick = startCapture)
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("home") {
                    HomeScreen(
                        onOpenNote = { navController.navigate("note/$it") },
                        onOpenPinned = { navController.navigate("notes?filter=pinned") },
                        onOpenSettings = { showSettings = true },
                    )
                }
                composable(
                    route = "notes?query={query}&filter={filter}",
                    arguments = listOf(
                        navArgument("query") { type = NavType.StringType; defaultValue = "" },
                        navArgument("filter") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    NotesScreen(
                        onOpenNote = { navController.navigate("note/$it") },
                        initialQuery = entry.arguments?.getString("query"),
                        initialFilter = entry.arguments?.getString("filter"),
                    )
                }
                composable("reminders") {
                    RemindersScreen(
                        onOpenNote = { navController.navigate("note/$it") },
                    )
                }
                composable(
                    route = "note/{noteId}",
                    arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
                ) {
                    NoteDetailScreen(
                        onBack = { navController.popBackStack() },
                        onShowSnackbar = showSnackbar,
                    )
                }
            }
        }
    }

    CaptureSheet(viewModel = captureViewModel)

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}

@Composable
private fun MicButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = PinColors.Accent,
        shadowElevation = 8.dp,
        modifier = Modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                PinIcons.Mic,
                contentDescription = stringResource(R.string.start_voice),
                tint = PinColors.OnAccent,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun PinBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(
        containerColor = PinColors.Surface,
        tonalElevation = 0.dp,
    ) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = PinColors.Accent,
            selectedTextColor = PinColors.Accent,
            unselectedIconColor = PinColors.InkGhost,
            unselectedTextColor = PinColors.InkGhost,
            indicatorColor = PinColors.Accent.copy(alpha = 0.14f),
        )

        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { navigateTop(navController, "home") },
            icon = { Icon(PinIcons.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_home)) },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = currentRoute == "notes",
            onClick = { navigateTop(navController, "notes") },
            icon = { Icon(PinIcons.Notes, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_notes)) },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = currentRoute == "reminders",
            onClick = { navigateTop(navController, "reminders") },
            icon = { Icon(PinIcons.Bell, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_reminders)) },
            colors = itemColors,
        )
    }
}

private fun navigateTop(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
