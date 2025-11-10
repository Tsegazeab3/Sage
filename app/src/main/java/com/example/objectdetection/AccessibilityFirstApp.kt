package com.example.objectdetection

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.objectdetection.ui.theme.DangerRed
import com.example.objectdetection.ui.theme.HouseModeGreen
import com.example.objectdetection.ui.theme.RoadModeBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Enums to define the different modes of the app
enum class OperatingMode {
    HOUSE, ROAD
}

enum class DetectionMode {
    AUTOMATIC, MANUAL
}

enum class Camera {
    PHONE, ESP32
}

// Placeholder functions for AI and audio feedback
// IMPORTANT: These functions should not block the main thread.
// They should be suspend functions that do their work on a background thread.
suspend fun startObjectDetection(operatingMode: OperatingMode, detectionMode: DetectionMode, playAudio: Boolean = true) {
    withContext(Dispatchers.IO) {
        // Simulate a long-running operation
        delay(2000)
        if (playAudio) {
            playAudioFeedback("Started ${detectionMode.name.lowercase()} detection in ${operatingMode.name.lowercase()} mode.")
        }
    }
}

suspend fun stopObjectDetection() {
    withContext(Dispatchers.IO) {
        // Simulate a long-running operation
        delay(1000)
        playAudioFeedback("Stopped detection.")
    }
}

fun searchForObject(query: String, operatingMode: OperatingMode) {
    playAudioFeedback("Searching for $query in ${operatingMode.name.lowercase()} mode.")
}

suspend fun checkForDanger(operatingMode: OperatingMode) {
    withContext(Dispatchers.IO) {
        // Simulate a long-running operation
        delay(3000)
        playAudioFeedback("Checking for danger in ${operatingMode.name.lowercase()} mode.")
    }
}

fun playAudioFeedback(message: String) {
    println("AUDIO FEEDBACK: $message")
}

sealed class NavigationItem(var route: String, var icon: ImageVector, var title: String) {
    object Home : NavigationItem("home", Icons.Default.Home, "Home")
    object Settings : NavigationItem("settings", Icons.Default.Settings, "Settings")
    object Help : NavigationItem("help", Icons.Default.Info, "Help")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityFirstApp(detector: YOLODetector) {
    val navController = rememberNavController()
    val dangerousItems = remember { mutableStateListOf<String>() }
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            Navigation(navController = navController, detector = detector, dangerousItems = dangerousItems)
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        NavigationItem.Home,
        NavigationItem.Settings,
        NavigationItem.Help
    )
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(text = item.title) },
                alwaysShowLabel = true,
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        navController.graph.startDestinationRoute?.let { route ->
                            popUpTo(route) {
                                saveState = true
                            }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun Navigation(navController: NavHostController, detector: YOLODetector, dangerousItems: MutableList<String>) {
    NavHost(navController, startDestination = NavigationItem.Home.route) {
        composable(NavigationItem.Home.route) {
            HomeScreen(detector = detector, dangerousItems = dangerousItems)
        }
        composable(NavigationItem.Settings.route) {
            SettingsScreen(dangerousItems = dangerousItems)
        }
        composable(NavigationItem.Help.route) {
            HelpScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(detector: YOLODetector, dangerousItems: List<String>) {
    var operatingMode by remember { mutableStateOf(OperatingMode.HOUSE) }
    val backgroundColor = if (operatingMode == OperatingMode.HOUSE) HouseModeGreen.copy(alpha = 0.1f) else RoadModeBlue.copy(alpha = 0.1f)
    var selectedItem by remember { mutableStateOf(HOUSE_CLASSES[0]) }
    var showPreview by remember { mutableStateOf(false) }
    var isPreview by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            ModeSwitch(
                currentMode = operatingMode,
                onModeChange = { newMode ->
                    operatingMode = newMode
                }
            )
        }

        item {
            Button(onClick = {
                showPreview = true
                isPreview = true
            }) {
                Text("Preview")
            }
        }

        item {
            when (operatingMode) {
                OperatingMode.HOUSE -> HouseModeScreenContent(onSearch = {
                    selectedItem = it
                    showPreview = true
                    isPreview = false
                }, onSpeak = {})
                OperatingMode.ROAD -> RoadModeScreenContent(onSearch = {
                    selectedItem = it
                    showPreview = true
                    isPreview = false
                }, onSpeak = {})
            }
        }
    }

    if (showPreview) {
        Dialog(
            onDismissRequest = { showPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PreviewScreen(
                detector = detector,
                onDismiss = { showPreview = false },
                isPreview = isPreview,
                dangerousItems = dangerousItems,
                initialSelectedItem = selectedItem
            )
        }
    }
}

@Composable
fun HouseModeScreenContent(onSearch: (String) -> Unit, onSpeak: () -> Unit) {
    ModeScreenContent(operatingMode = OperatingMode.HOUSE, color = HouseModeGreen, onSearch = onSearch, onSpeak = onSpeak)
}

@Composable
fun RoadModeScreenContent(onSearch: (String) -> Unit, onSpeak: () -> Unit) {
    var audioFeedbackEnabled by remember { mutableStateOf(true) }
    ModeScreenContent(
        operatingMode = OperatingMode.ROAD,
        color = RoadModeBlue,
        audioFeedbackEnabled = audioFeedbackEnabled,
        onAudioFeedbackToggle = { audioFeedbackEnabled = it },
        onSearch = onSearch,
        onSpeak = onSpeak
    )
}

@Composable
fun ModeScreenContent(
    operatingMode: OperatingMode,
    color: Color,
    audioFeedbackEnabled: Boolean? = null,
    onAudioFeedbackToggle: ((Boolean) -> Unit)? = null,
    onSearch: (String) -> Unit,
    onSpeak: () -> Unit
) {
    var detectionMode by remember { mutableStateOf(DetectionMode.AUTOMATIC) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (operatingMode == OperatingMode.ROAD && onAudioFeedbackToggle != null && audioFeedbackEnabled != null) {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Audio Feedback", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = audioFeedbackEnabled,
                        onCheckedChange = onAudioFeedbackToggle,
                        modifier = Modifier.semantics { contentDescription = "Toggle audio feedback for object detection" }
                    )
                }
            }
        }
        DetectionModeSwitch(
            currentMode = detectionMode,
            onModeChange = { detectionMode = it },
            color = color
        )

        if (detectionMode == DetectionMode.MANUAL) {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Button(
                    onClick = { onSpeak() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .semantics { contentDescription = "Trigger manual detection" },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) {
                    Text("Trigger Detection", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        SearchSection(
            onSearch = onSearch,
            color = color,
            showSearchButton = true
        )
    }
}

@Composable
fun ModeSwitch(currentMode: OperatingMode, onModeChange: (OperatingMode) -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModeButton(
                text = "House Mode",
                onClick = { onModeChange(OperatingMode.HOUSE) },
                isSelected = currentMode == OperatingMode.HOUSE,
                color = HouseModeGreen,
                contentDescription = "Switch to House Mode"
            )
            ModeButton(
                text = "Road Mode",
                onClick = { onModeChange(OperatingMode.ROAD) },
                isSelected = currentMode == OperatingMode.ROAD,
                color = RoadModeBlue,
                contentDescription = "Switch to Road Mode"
            )
        }
    }
}

@Composable
fun DetectionModeSwitch(currentMode: DetectionMode, onModeChange: (DetectionMode) -> Unit, color: Color) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModeButton(
                text = "Automatic",
                onClick = { onModeChange(DetectionMode.AUTOMATIC) },
                isSelected = currentMode == DetectionMode.AUTOMATIC,
                color = color,
                contentDescription = "Switch to Automatic Detection Mode"
            )
            ModeButton(
                text = "Manual",
                onClick = { onModeChange(DetectionMode.MANUAL) },
                isSelected = currentMode == DetectionMode.MANUAL,
                color = color,
                contentDescription = "Switch to Manual Detection Mode"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSection(onSearch: (String) -> Unit, color: Color, showSearchButton: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf(HOUSE_CLASSES[0]) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedItem,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select an object to search") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .semantics { contentDescription = "Dropdown menu for selecting an object to search" },
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    HOUSE_CLASSES.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                            onClick = {
                                selectedItem = item
                                expanded = false
                                if (!showSearchButton) {
                                    onSearch(item)
                                }
                                playAudioFeedback("Selected $item for search.")
                            },
                            modifier = Modifier.semantics { contentDescription = "Select $item" }
                        )
                    }
                }
            }

            if (showSearchButton) {
                Button(
                    onClick = { onSearch(selectedItem) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .semantics { contentDescription = "Start search for the selected object" },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) {
                    Text("Search", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun RowScope.ModeButton(text: String, onClick: () -> Unit, isSelected: Boolean, color: Color, contentDescription: String) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(70.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else Color.LightGray,
            contentColor = if (isSelected) Color.White else Color.DarkGray
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsScreen(dangerousItems: MutableList<String>) {
    var houseTimeGap by remember { mutableStateOf(1) }
    var roadTimeGap by remember { mutableStateOf(1) }
    val timeGaps = listOf(1, 2, 3, 4, 5)
    var selectedCamera by remember { mutableStateOf(Camera.PHONE) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        item {
            TimeGapSelector(
                title = "House Mode AI Response Time",
                timeGaps = timeGaps,
                selectedTime = houseTimeGap,
                onTimeSelected = {
                    houseTimeGap = it
                    playAudioFeedback("House mode response time set to $it seconds.")
                },
                color = HouseModeGreen
            )
        }
        item {
            TimeGapSelector(
                title = "Road Mode AI Response Time",
                timeGaps = timeGaps,
                selectedTime = roadTimeGap,
                onTimeSelected = {
                    roadTimeGap = it
                    playAudioFeedback("Road mode response time set to $it seconds.")
                },
                color = RoadModeBlue
            )
        }
        item {
            CameraSelection(
                selectedCamera = selectedCamera,
                onCameraSelected = {
                    selectedCamera = it
                    playAudioFeedback("Selected ${if (it == Camera.PHONE) "Phone" else "ESP32"} Camera.")
                }
            )
        }
        item {
            DangerousItemsSelector(
                dangerousItems = dangerousItems,
                onDangerousItemToggled = { item, isChecked ->
                    if (isChecked) {
                        dangerousItems.add(item)
                    } else {
                        dangerousItems.remove(item)
                    }
                }
            )
        }
    }
}

@Composable
fun DangerousItemsSelector(
    dangerousItems: List<String>,
    onDangerousItemToggled: (String, Boolean) -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Dangerous Items", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            YOLO_CLASSES.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item, fontSize = 18.sp)
                    Checkbox(
                        checked = dangerousItems.contains(item),
                        onCheckedChange = { isChecked ->
                            onDangerousItemToggled(item, isChecked)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CameraSelection(
    selectedCamera: Camera,
    onCameraSelected: (Camera) -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Camera Selection", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ModeButton(
                    text = "Phone Camera",
                    onClick = { onCameraSelected(Camera.PHONE) },
                    isSelected = selectedCamera == Camera.PHONE,
                    color = MaterialTheme.colorScheme.primary,
                    contentDescription = "Select Phone Camera"
                )
                ModeButton(
                    text = "ESP32 Camera",
                    onClick = { onCameraSelected(Camera.ESP32) },
                    isSelected = selectedCamera == Camera.ESP32,
                    color = MaterialTheme.colorScheme.primary,
                    contentDescription = "Select ESP32 Camera"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeGapSelector(
    title: String,
    timeGaps: List<Int>,
    selectedTime: Int,
    onTimeSelected: (Int) -> Unit,
    color: Color
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = "$selectedTime seconds",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select time gap") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .semantics { contentDescription = "Dropdown menu for selecting time gap" },
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    timeGaps.forEach { time ->
                        DropdownMenuItem(
                            text = { Text("$time seconds", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                            onClick = {
                                onTimeSelected(time)
                                expanded = false
                            },
                            modifier = Modifier.semantics { contentDescription = "Select $time seconds" }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HelpScreen() {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("Help", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "For any assistance, please visit our support website.",
                        fontSize = 18.sp,
                    )
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://support.example.com"))
                            context.startActivity(intent)
                            playAudioFeedback("Opening support website.")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .semantics { contentDescription = "Open support website" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Visit Support Website", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}