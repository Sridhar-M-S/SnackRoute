package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ShopMaster
import com.example.ui.AppViewModel
import com.example.ui.ReminderItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapRoutePlannerScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val dueReminders by viewModel.dueReminders.collectAsStateWithLifecycle()
    val shopsList by viewModel.shops.collectAsStateWithLifecycle()
    val locationsList by viewModel.locations.collectAsStateWithLifecycle()

    // Map locationNumber to locationName
    val locationMap = remember(locationsList) {
        locationsList.associate { it.locationNumber to it.locationName }
    }

    // 1. GPS Permissions & State
    var hasLocationPermission by remember { mutableStateOf(false) }
    var userLatitude by remember { mutableStateOf<Double?>(null) }
    var userLongitude by remember { mutableStateOf<Double?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    // Simulated location for development and easy testing
    var isSimulationMode by remember { mutableStateOf(true) }
    var simulatedLatitude by remember { mutableStateOf(12.9716) } // Bangalore default
    var simulatedLongitude by remember { mutableStateOf(77.5946) }

    // Map style / Layer state
    var mapLayerStyle by remember { mutableStateOf("Standard") } // Standard, Satellite
    var isTrafficLayerEnabled by remember { mutableStateOf(false) }

    // Search and Filter States
    var searchShopQuery by remember { mutableStateOf("") }
    var selectedLocationFilter by remember { mutableStateOf<String?>(null) }

    // Selected marker/shop details pane
    var selectedShop by remember { mutableStateOf<ShopMaster?>(null) }

    // Active Navigation / Routing State
    var isRouteStarted by remember { mutableStateOf(false) }
    var activeRouteIndex by remember { mutableStateOf(0) }

    // Offline / Network Connectivity Check
    var isOnline by remember { mutableStateOf(true) }

    // Map Camera settings (Zoom and Pan Offsets)
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffsetX by remember { mutableStateOf(0f) }
    var panOffsetY by remember { mutableStateOf(0f) }

    // Animating live location ripple pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )

    // Animated traffic dash phase
    val dashPhaseTransition = rememberInfiniteTransition(label = "dashPhase")
    val trafficDashPhase by dashPhaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "trafficDashPhase"
    )

    // Helper functions for checking connection
    fun checkConnectivity() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (cm != null) {
            val net = cm.activeNetwork
            val cap = cm.getNetworkCapabilities(net)
            isOnline = cap?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            isOnline = true
        }
    }

    // Check GPS Permissions
    fun checkGpsPermissions() {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        hasLocationPermission = fine || coarse
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            hasLocationPermission = fineGranted || coarseGranted
            if (hasLocationPermission) {
                isSimulationMode = false
            } else {
                Toast.makeText(context, "Location permission denied. Running in simulator mode.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // Fetch live user location using GPS
    fun fetchLiveGpsLocation() {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        if (lm == null || (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
            Toast.makeText(context, "GPS is disabled. Please turn on device location services.", Toast.LENGTH_SHORT).show()
            isSimulationMode = true
            return
        }

        isFetchingLocation = true
        try {
            val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            fusedClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                com.google.android.gms.tasks.CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                isFetchingLocation = false
                if (location != null) {
                    userLatitude = location.latitude
                    userLongitude = location.longitude
                    isSimulationMode = false
                } else {
                    // Fallback to last location
                    fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            userLatitude = lastLoc.latitude
                            userLongitude = lastLoc.longitude
                            isSimulationMode = false
                        } else {
                            Toast.makeText(context, "Unable to obtain direct GPS. Using simulator coordinates.", Toast.LENGTH_SHORT).show()
                            isSimulationMode = true
                        }
                    }
                }
            }.addOnFailureListener {
                isFetchingLocation = false
                Toast.makeText(context, "GPS failed: ${it.message}. Using simulator.", Toast.LENGTH_SHORT).show()
                isSimulationMode = true
            }
        } catch (e: SecurityException) {
            isFetchingLocation = false
            Toast.makeText(context, "Security error reading location.", Toast.LENGTH_SHORT).show()
            isSimulationMode = true
        }
    }

    // Trigger checks on launch
    LaunchedEffect(Unit) {
        checkConnectivity()
        checkGpsPermissions()
        if (hasLocationPermission) {
            fetchLiveGpsLocation()
        } else {
            isSimulationMode = true
        }
    }

    // Active Location Coordinates (either real GPS or simulation)
    val activeLat = if (isSimulationMode) simulatedLatitude else (userLatitude ?: simulatedLatitude)
    val activeLng = if (isSimulationMode) simulatedLongitude else (userLongitude ?: simulatedLongitude)

    // Filter pending shops based on reminders and chosen filter options
    val pendingShops: List<ShopMaster> = remember(dueReminders, selectedLocationFilter, locationMap) {
        dueReminders.map { it.shop }.filter { shop ->
            val shopLocName = locationMap[shop.locationNumber] ?: shop.locationNumber
            val matchLocation = selectedLocationFilter == null || shopLocName == selectedLocationFilter
            matchLocation
        }
    }

    // Calculate completed shops to display on the map as GREEN markers
    val completedShops: List<ShopMaster> = remember(shopsList, dueReminders) {
        val completedNumbers = shopsList.map { it.shopNumber }.toSet() - dueReminders.map { it.shop.shopNumber }.toSet()
        shopsList.filter { it.shopNumber in completedNumbers && it.latitude != null && it.longitude != null }
    }

    // 2. Nearest-Neighbor Optimization Engine
    val optimizedRoute: List<ShopMaster> = remember(activeLat, activeLng, pendingShops) {
        val unvisited = pendingShops.filter { it.latitude != null && it.longitude != null }.toMutableList()
        val route = mutableListOf<ShopMaster>()
        var currentLat = activeLat
        var currentLng = activeLng

        // If some pending shops lack coordinates, we deterministic assign nearby coordinates so they aren't lost
        val pendingWithoutCoords = pendingShops.filter { it.latitude == null || it.longitude == null }
        var mockAngle = 0.0
        val withCoords = unvisited.toMutableList()
        pendingWithoutCoords.forEach { shop ->
            val mockLat = activeLat + 0.005 * java.lang.Math.cos(mockAngle)
            val mockLng = activeLng + 0.005 * java.lang.Math.sin(mockAngle)
            mockAngle += 2 * java.lang.Math.PI / maxOf(1, pendingWithoutCoords.size)
            // Create a copy with simulated coordinates for route planner
            withCoords.add(shop.copy(latitude = mockLat, longitude = mockLng))
        }

        val tempUnvisited = withCoords.toMutableList()
        while (tempUnvisited.isNotEmpty()) {
            val nearest = tempUnvisited.minByOrNull { shop ->
                calculateDistance(currentLat, currentLng, shop.latitude ?: 0.0, shop.longitude ?: 0.0)
            }
            if (nearest != null) {
                route.add(nearest)
                tempUnvisited.remove(nearest)
                currentLat = nearest.latitude ?: currentLat
                currentLng = nearest.longitude ?: currentLng
            } else {
                break
            }
        }
        route
    }

    // Route summary calculations
    val routeStats = remember(activeLat, activeLng, optimizedRoute) {
        var totalDist = 0.0
        var currentLat = activeLat
        var currentLng = activeLng
        optimizedRoute.forEach { shop ->
            val sLat = shop.latitude ?: 0.0
            val sLng = shop.longitude ?: 0.0
            totalDist += calculateDistance(currentLat, currentLng, sLat, sLng)
            currentLat = sLat
            currentLng = sLng
        }

        val km = totalDist / 1000.0
        // Travel speed: average 25 km/h in urban streets + 5 mins per shop visit
        val durationMins = (km / 25.0 * 60.0).toInt() + (optimizedRoute.size * 5)
        Triple(km, durationMins, optimizedRoute.size)
    }

    // Automatically fit all shop markers into view bounding-box
    fun fitAllShopsOnMap() {
        panOffsetX = 0f
        panOffsetY = 0f
        zoomScale = 1.0f
    }

    // Highlight search matches
    val searchedShops = remember(searchShopQuery, optimizedRoute) {
        if (searchShopQuery.isBlank()) emptyList()
        else optimizedRoute.filter {
            it.storeName.contains(searchShopQuery, ignoreCase = true) ||
                    it.shopNumber.contains(searchShopQuery, ignoreCase = true)
        }
    }

    // Handle automated navigation steps
    fun moveToNextShop() {
        if (optimizedRoute.isNotEmpty()) {
            if (activeRouteIndex < optimizedRoute.size - 1) {
                activeRouteIndex++
                val nextShop = optimizedRoute[activeRouteIndex]
                selectedShop = nextShop
                if (isSimulationMode) {
                    simulatedLatitude = nextShop.latitude ?: simulatedLatitude
                    simulatedLongitude = nextShop.longitude ?: simulatedLongitude
                }
            } else {
                isRouteStarted = false
                Toast.makeText(context, "Amazing! You completed the route planner!", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun moveToPreviousShop() {
        if (activeRouteIndex > 0) {
            activeRouteIndex--
            val prevShop = optimizedRoute[activeRouteIndex]
            selectedShop = prevShop
            if (isSimulationMode) {
                simulatedLatitude = prevShop.latitude ?: simulatedLatitude
                simulatedLongitude = prevShop.longitude ?: simulatedLongitude
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map & Route Planner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("route_planner_back")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { checkConnectivity() }) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.CloudQueue else Icons.Default.CloudOff,
                            contentDescription = "Sync Status",
                            tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = { isSimulationMode = !isSimulationMode }) {
                        Icon(
                            imageVector = if (isSimulationMode) Icons.Default.DeveloperMode else Icons.Default.GpsFixed,
                            contentDescription = "Toggle Simulation Mode",
                            tint = if (isSimulationMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isWideScreen = maxWidth > 600.dp

            if (!isOnline) {
                // Offline banner warning
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(vertical = 4.dp, horizontal = 12.dp)
                        .align(Alignment.TopCenter)
                        .zIndex(5f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Offline Mode: Showing cached shop markers. Routing calculations local.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // Side control panel for Tablets / Expanded screens
                if (isWideScreen) {
                    Surface(
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        RouteSidebarPanel(
                            dueReminders = dueReminders,
                            optimizedRoute = optimizedRoute,
                            routeStats = routeStats,
                            activeRouteIndex = activeRouteIndex,
                            isRouteStarted = isRouteStarted,
                            selectedLocationFilter = selectedLocationFilter,
                            locationsList = locationsList,
                            locationMap = locationMap,
                            onLocationFilterSelect = { selectedLocationFilter = it },
                            searchShopQuery = searchShopQuery,
                            onSearchQueryChange = { searchShopQuery = it },
                            selectedShop = selectedShop,
                            onShopSelect = {
                                selectedShop = it
                                fitAllShopsOnMap()
                            },
                            onStartRoute = {
                                isRouteStarted = true
                                activeRouteIndex = 0
                                if (optimizedRoute.isNotEmpty()) {
                                    selectedShop = optimizedRoute[0]
                                }
                            },
                            onRecalculate = { fitAllShopsOnMap() },
                            onMarkCompleted = { shop ->
                                viewModel.markReminderCompleted(shop.shopNumber)
                                Toast.makeText(context, "${shop.storeName} reminder completed!", Toast.LENGTH_SHORT).show()
                                moveToNextShop()
                            }
                        )
                    }
                }

                // Main Interactive Map and Floating control boxes
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Custom GIS Visual Map Component
                    GisMapCanvas(
                        activeLat = activeLat,
                        activeLng = activeLng,
                        optimizedRoute = optimizedRoute,
                        completedShops = completedShops,
                        selectedShop = selectedShop,
                        searchedShops = searchedShops,
                        isRouteStarted = isRouteStarted,
                        activeRouteIndex = activeRouteIndex,
                        mapStyle = mapLayerStyle,
                        isTrafficEnabled = isTrafficLayerEnabled,
                        zoom = zoomScale,
                        panX = panOffsetX,
                        panY = panOffsetY,
                        pulseRadius = pulseRadius,
                        pulseAlpha = pulseAlpha,
                        trafficDashPhase = trafficDashPhase,
                        onPanChange = { dx, dy ->
                            panOffsetX += dx
                            panOffsetY += dy
                        },
                        onZoomChange = { scale ->
                            zoomScale = (zoomScale * scale).coerceIn(0.5f, 5.0f)
                        },
                        onMarkerClick = { shop ->
                            selectedShop = shop
                        }
                    )

                    // Map View Style Controls (Standard / Satellite / Traffic Layer)
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FloatingActionButton(
                            onClick = {
                                mapLayerStyle = if (mapLayerStyle == "Standard") "Satellite" else "Standard"
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("map_layer_toggle"),
                            elevation = FloatingActionButtonDefaults.elevation(2.dp)
                        ) {
                            Icon(
                                imageVector = if (mapLayerStyle == "Standard") Icons.Default.Layers else Icons.Default.Map,
                                contentDescription = "Map Style"
                            )
                        }

                        FloatingActionButton(
                            onClick = { isTrafficLayerEnabled = !isTrafficLayerEnabled },
                            containerColor = if (isTrafficLayerEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            contentColor = if (isTrafficLayerEnabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("map_traffic_toggle"),
                            elevation = FloatingActionButtonDefaults.elevation(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Traffic,
                                contentDescription = "Traffic Layer"
                            )
                        }
                    }

                    // Map Zoom + GPS Controls Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = if (!isWideScreen) 150.dp else 24.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FloatingActionButton(
                            onClick = {
                                fetchLiveGpsLocation()
                                fitAllShopsOnMap()
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("gps_my_location_button")
                        ) {
                            if (isFetchingLocation) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                            }
                        }

                        FloatingActionButton(
                            onClick = { fitAllShopsOnMap() },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("map_fit_all_button")
                        ) {
                            Icon(Icons.Default.ZoomOutMap, contentDescription = "Fit All Shops")
                        }

                        FloatingActionButton(
                            onClick = { zoomScale = (zoomScale * 1.2f).coerceAtMost(5f) },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In")
                        }

                        FloatingActionButton(
                            onClick = { zoomScale = (zoomScale / 1.2f).coerceAtLeast(0.5f) },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                        }
                    }

                    // Simulation controller overlay (shown in simulator mode)
                    if (isSimulationMode) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .width(220.dp)
                                .shadow(2.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("📍 Location Simulator", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Shift position manually to test routing:", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Button(
                                        onClick = { simulatedLatitude += 0.002 },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) { Text("North", fontSize = 8.sp) }
                                    Button(
                                        onClick = { simulatedLatitude -= 0.002 },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) { Text("South", fontSize = 8.sp) }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Button(
                                        onClick = { simulatedLongitude -= 0.002 },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) { Text("West", fontSize = 8.sp) }
                                    Button(
                                        onClick = { simulatedLongitude += 0.002 },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) { Text("East", fontSize = 8.sp) }
                                }
                            }
                        }
                    }

                    // Floating Bottom Drawer/Details card for Mobile/Portrait devices
                    if (!isWideScreen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            if (selectedShop != null) {
                                ShopMarkerDetailsCard(
                                    shop = selectedShop!!,
                                    userLat = activeLat,
                                    userLng = activeLng,
                                    locationMap = locationMap,
                                    dueReminders = dueReminders,
                                    onClose = { selectedShop = null },
                                    onNavigate = {
                                        val intentUri = Uri.parse("geo:${selectedShop!!.latitude},${selectedShop!!.longitude}?q=${selectedShop!!.latitude},${selectedShop!!.longitude}(${selectedShop!!.storeName})")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, intentUri)
                                        mapIntent.setPackage("com.google.android.apps.maps")
                                        if (mapIntent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(mapIntent)
                                        } else {
                                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${selectedShop!!.latitude},${selectedShop!!.longitude}"))
                                            context.startActivity(webIntent)
                                        }
                                    },
                                    onMarkCompleted = { shop ->
                                        viewModel.markReminderCompleted(shop.shopNumber)
                                        Toast.makeText(context, "${shop.storeName} completed!", Toast.LENGTH_SHORT).show()
                                        moveToNextShop()
                                    }
                                )
                            } else {
                                MobileRouteSummaryPanel(
                                    routeStats = routeStats,
                                    optimizedRoute = optimizedRoute,
                                    isRouteStarted = isRouteStarted,
                                    activeRouteIndex = activeRouteIndex,
                                    onStartRoute = {
                                        isRouteStarted = true
                                        activeRouteIndex = 0
                                        if (optimizedRoute.isNotEmpty()) {
                                            selectedShop = optimizedRoute[0]
                                        }
                                    },
                                    onNext = { moveToNextShop() },
                                    onPrev = { moveToPreviousShop() },
                                    selectedLocationFilter = selectedLocationFilter,
                                    locationsList = locationsList,
                                    locationMap = locationMap,
                                    onLocationFilterSelect = { selectedLocationFilter = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Sidebar route details panel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSidebarPanel(
    dueReminders: List<ReminderItem>,
    optimizedRoute: List<ShopMaster>,
    routeStats: Triple<Double, Int, Int>,
    activeRouteIndex: Int,
    isRouteStarted: Boolean,
    selectedLocationFilter: String?,
    locationsList: List<com.example.data.LocationMaster>,
    locationMap: Map<String, String>,
    onLocationFilterSelect: (String?) -> Unit,
    searchShopQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedShop: ShopMaster?,
    onShopSelect: (ShopMaster) -> Unit,
    onStartRoute: () -> Unit,
    onRecalculate: () -> Unit,
    onMarkCompleted: (ShopMaster) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchShopQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Find shop on map...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("route_search_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text("Filter:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Box {
                var expanded by remember { mutableStateOf(false) }
                FilterChip(
                    selected = selectedLocationFilter != null,
                    onClick = { expanded = true },
                    label = { Text(selectedLocationFilter ?: "All Areas", fontSize = 11.sp) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("All Areas") },
                        onClick = {
                            onLocationFilterSelect(null)
                            expanded = false
                        }
                    )
                    locationsList.forEach { loc ->
                        DropdownMenuItem(
                            text = { Text(loc.locationName) },
                            onClick = {
                                onLocationFilterSelect(loc.locationName)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Divider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Distance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(String.format(Locale.getDefault(), "%.1f km", routeStats.first), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Time Est.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("${routeStats.second} mins", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        if (!isRouteStarted) {
            Button(
                onClick = onStartRoute,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_route_sidebar_button")
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Optimized Route (${optimizedRoute.size} shops)")
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Active Navigation Mode", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (optimizedRoute.isNotEmpty()) {
                        val currentTarget = optimizedRoute[activeRouteIndex]
                        Text("Current Destination:", style = MaterialTheme.typography.labelSmall)
                        Text(currentTarget.storeName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onMarkCompleted(currentTarget) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Done", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = onRecalculate,
                                modifier = Modifier.weight(1.2f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Recalculate", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        Text("Route Visit Order", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (optimizedRoute.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text("No pending shops available. You are fully completed!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(optimizedRoute) { shop ->
                    val index = optimizedRoute.indexOf(shop)
                    val isCurrent = isRouteStarted && index == activeRouteIndex
                    val isSelected = selectedShop?.shopNumber == shop.shopNumber
                    val shopLocName = locationMap[shop.locationNumber] ?: shop.locationNumber

                    Card(
                        onClick = { onShopSelect(shop) },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isCurrent -> MaterialTheme.colorScheme.primaryContainer
                                isSelected -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isCurrent) 2.dp else if (isSelected) 1.dp else 0.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else if (isSelected) MaterialTheme.colorScheme.outline else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .testTag("route_list_shop_${shop.shopNumber}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(shop.storeName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${shop.shopNumber} • $shopLocName", style = MaterialTheme.typography.labelSmall)
                            }

                            val reminder = dueReminders.find { it.shop.shopNumber == shop.shopNumber }
                            if (reminder != null) {
                                val daysDiff = reminder.daysDifference
                                val statusColor = when {
                                    daysDiff < 0 -> Color(0xFFE53935)
                                    daysDiff == 0 -> Color(0xFFFB8C00)
                                    else -> Color(0xFF1E88E5)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(statusColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = when {
                                            daysDiff < 0 -> "Overdue"
                                            daysDiff == 0 -> "Today"
                                            else -> "Upcoming"
                                        },
                                        color = statusColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Details panel shown on click
@Composable
fun ShopMarkerDetailsCard(
    shop: ShopMaster,
    userLat: Double,
    userLng: Double,
    locationMap: Map<String, String>,
    dueReminders: List<ReminderItem>,
    onClose: () -> Unit,
    onNavigate: () -> Unit,
    onMarkCompleted: (ShopMaster) -> Unit
) {
    val reminder = remember(dueReminders, shop) { dueReminders.find { it.shop.shopNumber == shop.shopNumber } }
    val distStr = remember(userLat, userLng, shop) {
        val sLat = shop.latitude ?: 0.0
        val sLng = shop.longitude ?: 0.0
        val distMeters = calculateDistance(userLat, userLng, sLat, sLng)
        if (distMeters >= 1000) {
            String.format(Locale.getDefault(), "%.1f km away", distMeters / 1000.0)
        } else {
            String.format(Locale.getDefault(), "%d meters away", distMeters.toInt())
        }
    }
    val shopLocName = locationMap[shop.locationNumber] ?: shop.locationNumber

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = RoundedCornerShape(20.dp))
            .testTag("shop_details_marker_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(shop.storeName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Shop No: ${shop.shopNumber}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_shop_details")) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Area", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(shopLocName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Distance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(distStr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                if (reminder != null) {
                    Column {
                        Text("Days Since Sale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${reminder.daysSince} days", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (reminder != null) {
                val lastSaleStr = remember(reminder.lastSaleDate) {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    sdf.format(Date(reminder.lastSaleDate))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Last Sale Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(lastSaleStr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }

                    val daysDiff = reminder.daysDifference
                    val (statusLabel, statusColor) = when {
                        daysDiff < 0 -> Pair("Overdue Visit", Color(0xFFE53935))
                        daysDiff == 0 -> Pair("Due Today", Color(0xFFFB8C00))
                        else -> Pair("Upcoming", Color(0xFF1E88E5))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigate,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("navigate_google_maps_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Directions", fontSize = 12.sp)
                }

                Button(
                    onClick = { onMarkCompleted(shop) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mark_completed_details_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Visit Done", fontSize = 12.sp)
                }
            }
        }
    }
}

// Default overlay summary pane for mobile
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileRouteSummaryPanel(
    routeStats: Triple<Double, Int, Int>,
    optimizedRoute: List<ShopMaster>,
    isRouteStarted: Boolean,
    activeRouteIndex: Int,
    onStartRoute: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    selectedLocationFilter: String?,
    locationsList: List<com.example.data.LocationMaster>,
    locationMap: Map<String, String>,
    onLocationFilterSelect: (String?) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, shape = RoundedCornerShape(20.dp))
            .testTag("mobile_route_summary_panel"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!isRouteStarted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Optimized Route Planning", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("${routeStats.third} shops pending • ${String.format(Locale.getDefault(), "%.1f km", routeStats.first)}", style = MaterialTheme.typography.labelSmall)
                    }

                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter Area", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("All Areas") }, onClick = { onLocationFilterSelect(null); expanded = false })
                            locationsList.forEach { loc ->
                                DropdownMenuItem(text = { Text(loc.locationName) }, onClick = { onLocationFilterSelect(loc.locationName); expanded = false })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onStartRoute,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_optimized_route_mobile_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Navigation Route")
                }
            } else {
                if (optimizedRoute.isNotEmpty() && activeRouteIndex < optimizedRoute.size) {
                    val currentShop = optimizedRoute[activeRouteIndex]
                    val shopLocName = locationMap[currentShop.locationNumber] ?: currentShop.locationNumber
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("NAVIGATING: Stop ${activeRouteIndex + 1} of ${optimizedRoute.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                            Text(currentShop.storeName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(shopLocName, style = MaterialTheme.typography.labelSmall)
                        }

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                            CircularProgressIndicator(
                                progress = { (activeRouteIndex + 1).toFloat() / optimizedRoute.size },
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "${activeRouteIndex + 1}/${optimizedRoute.size}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPrev,
                            enabled = activeRouteIndex > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null)
                            Text("Prev", fontSize = 11.sp)
                        }

                        Button(
                            onClick = onNext,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next", fontSize = 11.sp)
                            Icon(Icons.Default.SkipNext, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

// Custom GIS Canvas-based Map
@Composable
fun GisMapCanvas(
    activeLat: Double,
    activeLng: Double,
    optimizedRoute: List<ShopMaster>,
    completedShops: List<ShopMaster>,
    selectedShop: ShopMaster?,
    searchedShops: List<ShopMaster>,
    isRouteStarted: Boolean,
    activeRouteIndex: Int,
    mapStyle: String,
    isTrafficEnabled: Boolean,
    zoom: Float,
    panX: Float,
    panY: Float,
    pulseRadius: Float,
    pulseAlpha: Float,
    trafficDashPhase: Float,
    onPanChange: (Float, Float) -> Unit,
    onZoomChange: (Float) -> Unit,
    onMarkerClick: (ShopMaster) -> Unit
) {
    val points = remember(activeLat, activeLng, optimizedRoute, completedShops) {
        val list = mutableListOf<Pair<Double, Double>>()
        list.add(Pair(activeLat, activeLng))
        optimizedRoute.forEach { s ->
            val lat = s.latitude
            val lng = s.longitude
            if (lat != null && lng != null) list.add(Pair(lat, lng))
        }
        completedShops.forEach { s ->
            val lat = s.latitude
            val lng = s.longitude
            if (lat != null && lng != null) list.add(Pair(lat, lng))
        }
        list
    }

    val bounds = remember(points) {
        var minL = 90.0
        var maxL = -90.0
        var minG = 180.0
        var maxG = -180.0
        points.forEach { (lat, lng) ->
            minL = minOf(minL, lat)
            maxL = maxOf(maxL, lat)
            minG = minOf(minG, lng)
            maxG = maxOf(maxG, lng)
        }
        val latDelta = maxOf(0.005, maxL - minL)
        val lngDelta = maxOf(0.005, maxG - minG)
        val mapMinLat = minL - latDelta * 0.15
        val mapMaxLat = maxL + latDelta * 0.15
        val mapMinLng = minG - lngDelta * 0.15
        val mapMaxLng = maxG + lngDelta * 0.15
        val mapWidthLng = mapMaxLng - mapMinLng
        val mapHeightLat = mapMaxLat - mapMinLat
        listOf(mapMinLat, mapMaxLat, mapMinLng, mapMaxLng, mapWidthLng, mapHeightLat)
    }

    val mapMinLat = bounds[0]
    val mapMaxLat = bounds[1]
    val mapMinLng = bounds[2]
    val mapMaxLng = bounds[3]
    val mapWidthLng = bounds[4]
    val mapHeightLat = bounds[5]

    val allPointsWithShops = remember(optimizedRoute, completedShops) {
        optimizedRoute + completedShops
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onPanChange(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(zoom, panX, panY, mapMinLat, mapMinLng, mapWidthLng, mapHeightLat, allPointsWithShops) {
                detectTapGestures { tapOffset ->
                    val clickedShop = allPointsWithShops.minByOrNull { shop ->
                        val sLat = shop.latitude ?: 0.0
                        val sLng = shop.longitude ?: 0.0
                        val pctX = (sLng - mapMinLng) / mapWidthLng
                        val pctY = 1.0 - (sLat - mapMinLat) / mapHeightLat
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val screenX = ((pctX * size.width - centerX) * zoom) + centerX + panX
                        val screenY = ((pctY * size.height - centerY) * zoom) + centerY + panY
                        val distSq = (tapOffset.x - screenX) * (tapOffset.x - screenX) + (tapOffset.y - screenY) * (tapOffset.y - screenY)
                        distSq
                    }
                    if (clickedShop != null) {
                        val sLat = clickedShop.latitude ?: 0.0
                        val sLng = clickedShop.longitude ?: 0.0
                        val pctX = (sLng - mapMinLng) / mapWidthLng
                        val pctY = 1.0 - (sLat - mapMinLat) / mapHeightLat
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val screenX = ((pctX * size.width - centerX) * zoom) + centerX + panX
                        val screenY = ((pctY * size.height - centerY) * zoom) + centerY + panY
                        val dist = java.lang.Math.sqrt(((tapOffset.x - screenX) * (tapOffset.x - screenX) + (tapOffset.y - screenY) * (tapOffset.y - screenY)).toDouble())
                        if (dist < 30.0 * zoom) {
                            onMarkerClick(clickedShop)
                        }
                    }
                }
            }
            .background(
                if (mapStyle == "Standard") Color(0xFFF2EFE9)
                else Color(0xFF1E2429)
            )
            .testTag("gis_map_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            fun convertToScreenCoords(lat: Double, lng: Double): Offset {
                val pctX = (lng - mapMinLng) / mapWidthLng
                val pctY = 1.0 - (lat - mapMinLat) / mapHeightLat
                val centerX = width / 2f
                val centerY = height / 2f
                val screenX = ((pctX * width - centerX) * zoom) + centerX + panX
                val screenY = ((pctY * height - centerY) * zoom) + centerY + panY
                return Offset(screenX.toFloat(), screenY.toFloat())
            }

            // Draw Background features
            if (mapStyle == "Standard") {
                val riverPath = Path().apply {
                    moveTo(0f, height * 0.2f)
                    cubicTo(width * 0.3f, height * 0.1f, width * 0.6f, height * 0.5f, width, height * 0.4f)
                }
                drawPath(riverPath, color = Color(0xFFA5C9EB), style = Stroke(width = 30f * zoom))

                drawRect(
                    color = Color(0xFFCBE6A3),
                    topLeft = Offset(width * 0.1f * zoom + panX, height * 0.6f * zoom + panY),
                    size = androidx.compose.ui.geometry.Size(width * 0.25f * zoom, height * 0.2f * zoom)
                )

                val streetColor = Color(0xFFE4E0D8)
                for (i in 0..10) {
                    val x = (width * 0.1f * i * zoom) + panX
                    drawLine(streetColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1.5f)
                    val y = (height * 0.1f * i * zoom) + panY
                    drawLine(streetColor, Offset(0f, y), Offset(width, y), strokeWidth = 1.5f)
                }
            } else {
                drawCircle(
                    color = Color(0xFF19321E),
                    radius = width * 0.3f * zoom,
                    center = Offset(width * 0.5f * zoom + panX, height * 0.4f * zoom + panY)
                )
                drawRect(
                    color = Color(0xFF0D2531),
                    topLeft = Offset(width * 0.6f * zoom + panX, height * 0.1f * zoom + panY),
                    size = androidx.compose.ui.geometry.Size(width * 0.25f * zoom, height * 0.3f * zoom)
                )
                val coordColor = Color(0xFF2E3B4E)
                for (i in 0..8) {
                    val x = (width * 0.15f * i * zoom) + panX
                    drawLine(coordColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
                    val y = (height * 0.15f * i * zoom) + panY
                    drawLine(coordColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
                }
            }

            if (isTrafficEnabled) {
                val path1 = Path().apply {
                    moveTo(0f, height * 0.45f * zoom + panY)
                    lineTo(width, height * 0.45f * zoom + panY)
                }
                val path2 = Path().apply {
                    moveTo(width * 0.5f * zoom + panX, 0f)
                    lineTo(width * 0.5f * zoom + panX, height)
                }
                drawPath(
                    path1,
                    color = Color(0xFF4CAF50),
                    style = Stroke(
                        width = 4f * zoom,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), trafficDashPhase)
                    )
                )
                drawPath(
                    path2,
                    color = Color(0xFFF44336),
                    style = Stroke(
                        width = 4f * zoom,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), -trafficDashPhase)
                    )
                )
            }

            if (optimizedRoute.isNotEmpty()) {
                val routePath = Path()
                val startScreen = convertToScreenCoords(activeLat, activeLng)
                routePath.moveTo(startScreen.x, startScreen.y)
                optimizedRoute.forEach { shop ->
                    val sLat = shop.latitude ?: 0.0
                    val sLng = shop.longitude ?: 0.0
                    val shopScreen = convertToScreenCoords(sLat, sLng)
                    routePath.lineTo(shopScreen.x, shopScreen.y)
                }

                drawPath(
                    routePath,
                    color = Color(0x5500A2E8),
                    style = Stroke(width = 10f * zoom, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    routePath,
                    color = Color(0xFF008CC9),
                    style = Stroke(
                        width = 4f * zoom,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(25f, 15f), trafficDashPhase)
                    )
                )
            }

            val userScreen = convertToScreenCoords(activeLat, activeLng)
            drawCircle(
                color = Color(0x661E88E5),
                radius = pulseRadius * zoom,
                center = userScreen,
                style = Fill
            )
            drawCircle(
                color = Color(0xFFFFFFFF),
                radius = 9f * zoom,
                center = userScreen,
                style = Fill
            )
            drawCircle(
                color = Color(0xFF1E88E5),
                radius = 7f * zoom,
                center = userScreen,
                style = Fill
            )

            completedShops.forEach { shop ->
                val sLat = shop.latitude ?: return@forEach
                val sLng = shop.longitude ?: return@forEach
                val screenPos = convertToScreenCoords(sLat, sLng)
                drawCircle(
                    color = Color(0x444CAF50),
                    radius = 12f * zoom,
                    center = screenPos
                )
                drawCircle(
                    color = Color(0xFF4CAF50),
                    radius = 6f * zoom,
                    center = screenPos
                )
            }

            optimizedRoute.forEach { shop ->
                val sLat = shop.latitude ?: return@forEach
                val sLng = shop.longitude ?: return@forEach
                val screenPos = convertToScreenCoords(sLat, sLng)
                val routeIndex = optimizedRoute.indexOf(shop)
                val isSelected = selectedShop?.shopNumber == shop.shopNumber
                val isSearched = searchedShops.any { it.shopNumber == shop.shopNumber }

                val pinColor = when {
                    routeIndex < activeRouteIndex && isRouteStarted -> Color(0xFF4CAF50)
                    routeIndex == activeRouteIndex && isRouteStarted -> Color(0xFF00E676)
                    routeIndex % 3 == 0 -> Color(0xFFE53935)
                    routeIndex % 3 == 1 -> Color(0xFFFB8C00)
                    else -> Color(0xFF1E88E5)
                }

                if (isSelected || isSearched) {
                    drawCircle(
                        color = Color(0x66FFEB3B),
                        radius = 24f * zoom,
                        center = screenPos,
                        style = Fill
                    )
                }

                val markerCenter = Offset(screenPos.x, screenPos.y - 14f * zoom)
                drawCircle(
                    color = pinColor,
                    radius = 12f * zoom,
                    center = markerCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 9f * zoom,
                    center = markerCenter
                )
                drawCircle(
                    color = pinColor,
                    radius = 7f * zoom,
                    center = markerCenter
                )
            }
        }
    }
}

// Distance computation helper using Haversine algorithm
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371e3 // Earth's Radius in meters
    val phi1 = java.lang.Math.toRadians(lat1)
    val phi2 = java.lang.Math.toRadians(lat2)
    val deltaPhi = java.lang.Math.toRadians(lat2 - lat1)
    val deltaLambda = java.lang.Math.toRadians(lon2 - lon1)

    val a = java.lang.Math.sin(deltaPhi / 2) * java.lang.Math.sin(deltaPhi / 2) +
            java.lang.Math.cos(phi1) * java.lang.Math.cos(phi2) *
            java.lang.Math.sin(deltaLambda / 2) * java.lang.Math.sin(deltaLambda / 2)
    val c = 2 * java.lang.Math.atan2(java.lang.Math.sqrt(a), java.lang.Math.sqrt(1 - a))
    return r * c
}
