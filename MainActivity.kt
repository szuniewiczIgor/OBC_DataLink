package com.example.obc_datalink

import android.Manifest // WAŻNY IMPORT
import android.content.Context // NOWY IMPORT
import android.content.pm.PackageManager // WAŻNY IMPORT
import android.hardware.Sensor // NOWY IMPORT
import android.hardware.SensorEvent // NOWY IMPORT
import android.hardware.SensorEventListener // NOWY IMPORT
import android.hardware.SensorManager // NOWY IMPORT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult // WAŻNY IMPORT
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts // WAŻNY IMPORT
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect // NOWY IMPORT
import androidx.compose.runtime.LaunchedEffect // WAŻNY IMPORT
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext // WAŻNY IMPORT
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat // WAŻNY IMPORT
import com.example.obc_datalink.ui.theme.OBC_DataLinkTheme
import com.google.android.gms.location.LocationServices // WAŻNY IMPORT
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.clickable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay

data class NavItem(
    val label: String,
    val icon: ImageVector
)

data class FlightRecord(
    val date: String,
    val time: String,
    val apogee: String,
    val tta: String,
    val lov: String,
    val ma: String,
    val mv: String,
    val ert: String,
    val ft: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }

        enableEdgeToEdge()
        setContent {
            OBC_DataLinkTheme {
                LaunchedEffect(Unit) {
                    //delay(500)
                    keepSplashScreen = false
                }
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val APOGEE = "1250 m"
    val TTA = "14 s"
    val LOV = "12 m/s"
    val MA = "4.2 G"
    val MV = "140 m/s"
    val ERT = "3.5 s"
    val FT = "45 s"

    val navItems = listOf(
        NavItem("Home", Icons.Default.Home),
        NavItem("Data", Icons.Default.Analytics),
        NavItem("Flights", Icons.Default.RocketLaunch),
        NavItem("Manage", Icons.Default.Settings)
    )

    var selectedIndex by remember { mutableIntStateOf(0) }
    var BtIsConnected by remember { mutableStateOf(false) }
    var isDataDownloaded by remember { mutableStateOf(false) }
    val savedFlights = remember { mutableStateListOf<FlightRecord>() }

    //ZMIENNE DO GPS
    val context = LocalContext.current
    var gpsText by remember { mutableStateOf("Waiting for GPS...") }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    //ZMIENNE DO CZUJNIKÓW
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    //BAROMETR
    var pressureText by remember { mutableStateOf("Waiting for Barometer...") }
    val pressureSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) }

    //MAGNETOMETR
    var magnetometerText by remember { mutableStateOf("Waiting for Magnetometer...") }
    val magneticSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }

    //ORIENTACJA (WYMAGA MAGNETOMETRU I AKCELEROMETRU)
    var orientationText by remember { mutableStateOf("Waiting for Orientation...") }
    val accelerometerSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    //Tablice do obliczeń orientacji
    val accelerometerReading = remember { FloatArray(3) }
    val magnetometerReading = remember { FloatArray(3) }
    val rotationMatrix = remember { FloatArray(9) }
    val orientationAngles = remember { FloatArray(3) }


    //Obsługa Barometru
    DisposableEffect(Unit) {
        if (pressureSensor == null) {
            pressureText = "No Barometer Sensor found"
            onDispose {}
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.let {
                        pressureText = "${it.values[0]} hPa"
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, pressureSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    //Obsługa Magnetometru
    DisposableEffect(Unit) {
        if (magneticSensor == null) {
            magnetometerText = "No Magnetometer Sensor found"
            onDispose {}
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.let {
                        //Wyświetlanie surowych danych
                        magnetometerText = "X: ${"%.1f".format(it.values[0])} uT\n" +
                                "Y: ${"%.1f".format(it.values[1])} uT\n" +
                                "Z: ${"%.1f".format(it.values[2])} uT"

                        //Kopiowanie danych do obliczeń orientacji
                        System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, magneticSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    //Obsługa Akcelerometru (Dla Orientacji)
    DisposableEffect(Unit) {
        if (accelerometerSensor == null) {
            orientationText = "No Accelerometer found"
            onDispose {}
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.let {
                        //Kopiowanie danych akcelerometru
                        System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)

                        //Obliczanie orientacji
                        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)

                        //Konwersja na stopnie
                        val azimuth = Math.toDegrees(orientationAngles[0].toDouble())
                        val pitch = Math.toDegrees(orientationAngles[1].toDouble())
                        val roll = Math.toDegrees(orientationAngles[2].toDouble())

                        orientationText = "Azimuth: ${"%.0f".format(azimuth)}°\n" +
                                "Pitch: ${"%.0f".format(pitch)}°\n" +
                                "Roll: ${"%.0f".format(roll)}°"
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    // Launcher uprawnień
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.reduce { acc, next -> acc && next }
        if (isGranted) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        gpsText = "Lat: ${location.latitude}\nLng: ${location.longitude}"
                    } else {
                        gpsText = "GPS enabled but no fix yet"
                    }
                }
            }
        } else {
            gpsText = "GPS Permission Denied"
        }
    }

    // Zapytanie o uprawnienia przy starcie ekranu
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(navItems[selectedIndex].label) },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = if (BtIsConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                            contentDescription = "Status Bluetooth",
                            tint = if (BtIsConnected) Color.Blue else Color.Gray
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomBar(
                items = navItems,
                selectedIndex = selectedIndex,
                onItemSelected = { selectedIndex = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selectedIndex == 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    //WYŚWIETLANIE GPS
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Phone Sensor Data (GPS)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = gpsText, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    //WYŚWIETLANIE MAGNETOMETRU
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Phone Sensor Data (Magnetometer)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = magnetometerText, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    //WYŚWIETLANIE ORIENTACJI
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Phone Sensor Data (Orientation)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = orientationText, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            BtIsConnected = !BtIsConnected
                            if (!BtIsConnected) isDataDownloaded = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (BtIsConnected) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (BtIsConnected) "Disconnect from OBC" else "Connect to OBC")
                    }
                    Button(
                        onClick = {
                            selectedIndex = 1
                            isDataDownloaded = true
                        },
                        enabled = BtIsConnected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download Flight Data")
                    }
                }
            } else if (selectedIndex == 1) {
                if (!BtIsConnected) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { selectedIndex = 0 }) {
                            Text("Connect to OBC first")
                        }
                    }
                } else {
                    if (!isDataDownloaded) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No data downloaded.")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { selectedIndex = 0 }) {
                                Text("Go to Home to Download")
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Flight Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    DataRowDisplay("Apogee", APOGEE)
                                    DataRowDisplay("Time to apogee", TTA)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    DataRowDisplay("Liftoff velocity", LOV)
                                    DataRowDisplay("Max acceleration", MA)
                                    DataRowDisplay("Max Flight velocity", MV)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    DataRowDisplay("Engine time", ERT)
                                    DataRowDisplay("Flight time", FT)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            //WYŚWIETLANIE BAROMETRU
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Phone Sensor Data (Barometer)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = pressureText, style = MaterialTheme.typography.bodyLarge)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val currentDateTime = LocalDateTime.now()
                                    val dateStr = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                    val timeStr = currentDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                                    savedFlights.add(0, FlightRecord(dateStr, timeStr, APOGEE, TTA, LOV, MA, MV, ERT, FT))
                                    selectedIndex = 2
                                },
                                modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
                            ) {
                                Text("Confirm and save Data")
                            }
                        }
                    }
                }
            } else if (selectedIndex == 2) {
                if (savedFlights.isEmpty()) {
                    Text("No saved flights yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(savedFlights) { flight -> FlightItemCard(flight) }
                    }
                }
            }
        }
    }
}

@Composable
fun DataRowDisplay(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FlightItemCard(flight: FlightRecord) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "Date: ${flight.date}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "Time: ${flight.time}", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!expanded) {
                        Text("Apogee: ${flight.apogee}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.padding(4.dp))
                    }
                    Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Expand")
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DataRowDisplay("Apogee", flight.apogee)
                    DataRowDisplay("Time to apogee", flight.tta)
                    DataRowDisplay("Liftoff velocity", flight.lov)
                    DataRowDisplay("Max acceleration", flight.ma)
                    DataRowDisplay("Max velocity", flight.mv)
                    DataRowDisplay("Engine time", flight.ert)
                    DataRowDisplay("Flight time", flight.ft)
                }
            }
        }
    }
}

@Composable
fun BottomBar(items: List<NavItem>, selectedIndex: Int, onItemSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp))) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = (index == selectedIndex),
                onClick = { onItemSelected(index) },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    OBC_DataLinkTheme {
        MainScreen()
    }
}