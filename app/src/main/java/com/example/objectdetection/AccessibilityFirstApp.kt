package com.example.objectdetection

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Slider // Added Slider import
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.objectdetection.ui.theme.HouseModeGreen
import com.example.objectdetection.ui.theme.RoadModeBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun Float.format(digits: Int) = "%.${digits}f".format(this)

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
fun AccessibilityFirstApp(
    detector: YOLODetector,
    ipAddress: String?,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit
) {
    val navController = rememberNavController()
    val dangerousItems = remember { mutableStateListOf<String>() }
    val context = LocalContext.current
    var detectionMode by remember { mutableStateOf(DetectionMode.AUTOMATIC) }
    var sortSearchList by remember { mutableStateOf(false) }
    var emergencyNumber by remember { mutableStateOf("911") }
    var selectedCamera by remember { mutableStateOf(Camera.PHONE) }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            Navigation(
                navController = navController,
                detector = detector,
                dangerousItems = dangerousItems,
                ipAddress = ipAddress,
                detectionMode = detectionMode,
                onDetectionModeChange = { detectionMode = it },
                sortSearchList = sortSearchList,
                onSortSearchListChange = { sortSearchList = it },
                emergencyNumber = emergencyNumber,
                onEmergencyNumberChange = { emergencyNumber = it },
                selectedCamera = selectedCamera,
                onCameraChange = { selectedCamera = it },
                settings = settings,
                onSettingsChange = onSettingsChange
            )
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
fun Navigation(
    navController: NavHostController,
    detector: YOLODetector,
    dangerousItems: MutableList<String>,
    ipAddress: String?,
    detectionMode: DetectionMode,
    onDetectionModeChange: (DetectionMode) -> Unit,
    sortSearchList: Boolean,
    onSortSearchListChange: (Boolean) -> Unit,
    emergencyNumber: String,
    onEmergencyNumberChange: (String) -> Unit,
    selectedCamera: Camera,
    onCameraChange: (Camera) -> Unit,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit
) {
    NavHost(navController, startDestination = NavigationItem.Home.route) {
        composable(NavigationItem.Home.route) {
            HomeScreen(
                detector = detector,
                dangerousItems = dangerousItems,
                detectionMode = detectionMode,
                sortSearchList = sortSearchList,
                emergencyNumber = emergencyNumber,
                selectedCamera = selectedCamera
            )
        }
        composable(NavigationItem.Settings.route) {
            SettingsScreen(
                dangerousItems = dangerousItems,
                ipAddress = ipAddress,
                detectionMode = detectionMode,
                onDetectionModeChange = onDetectionModeChange,
                sortSearchList = sortSearchList,
                onSortSearchListChange = onSortSearchListChange,
                navController = navController,
                emergencyNumber = emergencyNumber,
                onEmergencyNumberChange = onEmergencyNumberChange,
                selectedCamera = selectedCamera,
                onCameraChange = onCameraChange,
                settings = settings,
                onSettingsChange = onSettingsChange
            )
        }
        composable(NavigationItem.Help.route) {
            HelpScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    detector: YOLODetector,
    dangerousItems: List<String>,
    detectionMode: DetectionMode,
    sortSearchList: Boolean,
    emergencyNumber: String,
    selectedCamera: Camera
) {
    var operatingMode by remember { mutableStateOf(OperatingMode.HOUSE) }
    val backgroundColor = if (operatingMode == OperatingMode.HOUSE) HouseModeGreen.copy(alpha = 0.1f) else RoadModeBlue.copy(alpha = 0.1f)
    var selectedItem by remember { mutableStateOf(HOUSE_CLASSES[0]) }
    var showPreview by remember { mutableStateOf(false) }
    var isPreview by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(showPreview) {
        if (!showPreview) {
            ArduinoConnector.messages.collect { message ->
                when (message) {
                    "BUTTON_1_PRESSED" -> {
                        operatingMode = if (operatingMode == OperatingMode.HOUSE) OperatingMode.ROAD else OperatingMode.HOUSE
                        playAudioFeedback("Mode toggled to ${operatingMode.name}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Mode toggled to ${operatingMode.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "BUTTON_2_PRESSED" -> {
                        showPreview = true
                        isPreview = true
                        playAudioFeedback("Preview triggered")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Preview triggered", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "BUTTON_3_PRESSED" -> {
                        showPreview = true
                        isPreview = false
                        playAudioFeedback("Search triggered")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Search triggered", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "button3_pressed_twice" -> {
                        callEmergency(context, emergencyNumber)
                        playAudioFeedback("Emergency call initiated")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Emergency call initiated", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

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
                OperatingMode.HOUSE -> HouseModeScreenContent(
                    onSearch = {
                        selectedItem = it
                        showPreview = true
                        isPreview = false
                    },
                    onSpeak = {},
                    detectionMode = detectionMode,
                    sortSearchList = sortSearchList
                )
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
                initialSelectedItem = selectedItem,
                selectedCamera = selectedCamera
            )
        }
    }
}

private fun callEmergency(context: Context, emergencyNumber: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber"))
    context.startActivity(intent)
}

@Composable
fun HouseModeScreenContent(
    onSearch: (String) -> Unit,
    onSpeak: () -> Unit,
    detectionMode: DetectionMode,
    sortSearchList: Boolean
) {
    ModeScreenContent(
        operatingMode = OperatingMode.HOUSE,
        color = HouseModeGreen,
        onSearch = onSearch,
        onSpeak = onSpeak,
        detectionMode = detectionMode,
        sortSearchList = sortSearchList
    )
}

@Composable
fun RoadModeScreenContent(onSearch: (String) -> Unit, onSpeak: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Road Mode - Coming Soon!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ModeScreenContent(
    operatingMode: OperatingMode,
    color: Color,
    onSearch: (String) -> Unit,
    onSpeak: () -> Unit,
    detectionMode: DetectionMode,
    sortSearchList: Boolean
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
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
            showSearchButton = true,
            sortSearchList = sortSearchList
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
fun SearchSection(
    onSearch: (String) -> Unit,
    color: Color,
    showSearchButton: Boolean = true,
    sortSearchList: Boolean = false
) {
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
fun SettingsScreen(
    dangerousItems: MutableList<String>,
    ipAddress: String?,
    detectionMode: DetectionMode,
    onDetectionModeChange: (DetectionMode) -> Unit,
    sortSearchList: Boolean,
    onSortSearchListChange: (Boolean) -> Unit,
    navController: NavHostController,
    emergencyNumber: String,
    onEmergencyNumberChange: (String) -> Unit,
    selectedCamera: Camera,
    onCameraChange: (Camera) -> Unit,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit
) {
    var houseTimeGap by remember { mutableStateOf(1) }
    var roadTimeGap by remember { mutableStateOf(1) }
    val timeGaps = listOf(1, 2, 3, 4, 5)
    var showDangerousItemsPopup by remember { mutableStateOf(false) }
    var sortDangerousItems by remember { mutableStateOf(false) }

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
                    Text("Ultrasonic Thresholds", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Front Distance Threshold: ${settings.frontDistanceThreshold.format(2)} cm")
                    Slider(
                        value = settings.frontDistanceThreshold,
                        onValueChange = { onSettingsChange(settings.copy(frontDistanceThreshold = it)) },
                        valueRange = 0f..500f
                    )
                    Text("Overhead Distance Threshold: ${settings.overheadDistanceThreshold.format(2)} cm")
                    Slider(
                        value = settings.overheadDistanceThreshold,
                        onValueChange = { onSettingsChange(settings.copy(overheadDistanceThreshold = it)) },
                        valueRange = 0f..500f
                    )
                }
            }
        }
        item {
            DetectionModeSwitch(
                currentMode = detectionMode,
                onModeChange = onDetectionModeChange,
                color = MaterialTheme.colorScheme.primary
            )
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
                    Text("Emergency Number", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = emergencyNumber,
                        onValueChange = onEmergencyNumberChange,
                        label = { Text("Emergency Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
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
                    Text("Sort Search List", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = sortSearchList,
                        onCheckedChange = onSortSearchListChange
                    )
                }
            }
        }
        item {
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
                    Text("Sort Dangerous Items", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = sortDangerousItems,
                        onCheckedChange = { sortDangerousItems = it }
                    )
                }
            }
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
                    Text("Phone IP Address", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(ipAddress ?: "No local IP found", fontSize = 18.sp)
                    Text("TCP Port: 8080", fontSize = 18.sp)
                }
            }
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
                    onCameraChange(it)
                    playAudioFeedback("Selected ${if (it == Camera.PHONE) "Phone" else "ESP32"} Camera.")
                }
            )
        }
        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDangerousItemsPopup = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dangerous Items", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Select Dangerous Items"
                    )
                }
            }
        }
    }

    if (showDangerousItemsPopup) {
        DangerousItemsPopup(
            dangerousItems = dangerousItems,
            onDismiss = { showDangerousItemsPopup = false },
            onDangerousItemToggled = { item, isChecked ->
                if (isChecked) {
                    dangerousItems.add(item)
                } else {
                    dangerousItems.remove(item)
                }
            },
            sortDangerousItems = sortDangerousItems
        )
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

@Composable
fun DangerousItemsPopup(
    dangerousItems: MutableList<String>,
    onDismiss: () -> Unit,
    onDangerousItemToggled: (String, Boolean) -> Unit,
    sortDangerousItems: Boolean
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            DangerousItemsSelector(
                dangerousItems = dangerousItems,
                onDangerousItemToggled = onDangerousItemToggled,
                sortDangerousItems = sortDangerousItems
            )
        }
    }
}

@Composable
fun DangerousItemsSelector(
    dangerousItems: MutableList<String>,
    onDangerousItemToggled: (String, Boolean) -> Unit,
    sortDangerousItems: Boolean
) {
    val dangerousItemsList = if (sortDangerousItems) HOUSE_CLASSES.sorted() else HOUSE_CLASSES

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Dangerous Items", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { dangerousItems.clear() }) {
                Text("Deselect All")
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(items = dangerousItemsList) { item ->
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
}
