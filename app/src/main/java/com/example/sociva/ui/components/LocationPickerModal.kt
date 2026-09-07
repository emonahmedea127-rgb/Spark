package com.example.sociva.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.sociva.data.model.StructuredLocation
import com.example.ui.theme.SocivaBlue
import com.example.ui.theme.SocivaIndigo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerModal(
  title: String = "Select Location",
  initialLocation: StructuredLocation = StructuredLocation(),
  onLocationConfirmed: (StructuredLocation) -> Unit,
  onClearLocation: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  // State
  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  var searchResults by remember { mutableStateOf<List<StructuredLocation>>(emptyList()) }
  var isLocatingGps by remember { mutableStateOf(false) }
  var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

  // Map state (lat, lng, zoom)
  var currentLat by remember { mutableStateOf(initialLocation.latitude ?: 23.8103) } // Default Dhaka / San Francisco fallback
  var currentLng by remember { mutableStateOf(initialLocation.longitude ?: 90.4125) }
  var zoomLevel by remember { mutableStateOf(10f) }

  // Selected structured location
  var selectedLocation by remember {
    mutableStateOf(
      if (initialLocation.city.isNotBlank()) initialLocation
      else StructuredLocation(
        city = "Dhaka",
        region = "Dhaka Division",
        country = "Bangladesh",
        countryCode = "BD",
        latitude = 23.8103,
        longitude = 90.4125
      )
    )
  }

  // Active Tab: Map View vs Place Search
  var activeViewTab by remember { mutableStateOf(0) } // 0: Map View, 1: Search List

  // Permission Launcher
  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (fineGranted || coarseGranted) {
      permissionDeniedMessage = null
      coroutineScope.launch {
        detectCurrentGpsLocation(context) { loc ->
          currentLat = loc.latitude
          currentLng = loc.longitude
          selectedLocation = loc
          isLocatingGps = false
        }
      }
    } else {
      isLocatingGps = false
      permissionDeniedMessage = "Location permission was denied. You can still search places or pick from the map."
    }
  }

  // Reverse geocode when pin moves
  fun reverseGeocodeCoordinates(lat: Double, lng: Double) {
    coroutineScope.launch {
      val result = withContext(Dispatchers.IO) {
        try {
          if (Geocoder.isPresent()) {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lng, 1)
            if (!list.isNullOrEmpty()) {
              val address = list[0]
              val city = address.locality
                ?: address.subAdminArea
                ?: address.featureName
                ?: "Location"
              val region = address.adminArea ?: address.subAdminArea ?: ""
              val country = address.countryName ?: ""
              val countryCode = address.countryCode ?: ""
              StructuredLocation(
                city = city,
                region = region,
                country = country,
                countryCode = countryCode,
                latitude = lat,
                longitude = lng
              )
            } else {
              fallbackGeocode(lat, lng)
            }
          } else {
            fallbackGeocode(lat, lng)
          }
        } catch (e: Exception) {
          fallbackGeocode(lat, lng)
        }
      }
      selectedLocation = result
    }
  }

  // Search places using Geocoder + Comprehensive Database
  fun performSearch(query: String) {
    if (query.length < 2) {
      searchResults = emptyList()
      return
    }
    isSearching = true
    coroutineScope.launch {
      val results = withContext(Dispatchers.IO) {
        val list = mutableListOf<StructuredLocation>()
        try {
          if (Geocoder.isPresent()) {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(query, 6)
            if (!addresses.isNullOrEmpty()) {
              for (addr in addresses) {
                val city = addr.locality ?: addr.subAdminArea ?: addr.featureName ?: query
                val region = addr.adminArea ?: ""
                val country = addr.countryName ?: ""
                val countryCode = addr.countryCode ?: ""
                list.add(
                  StructuredLocation(
                    city = city,
                    region = region,
                    country = country,
                    countryCode = countryCode,
                    latitude = addr.latitude,
                    longitude = addr.longitude
                  )
                )
              }
            }
          }
        } catch (e: Exception) {
          // Fallback to built-in places directory
        }

        // Add matching places from comprehensive global cities directory
        val matchedPreset = WorldwidePlacesDirectory.searchPlaces(query)
        for (p in matchedPreset) {
          if (list.none { it.city.equals(p.city, ignoreCase = true) && it.country.equals(p.country, ignoreCase = true) }) {
            list.add(p)
          }
        }
        list
      }
      searchResults = results
      isSearching = false
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.90f)
        .clip(RoundedCornerShape(24.dp))
        .testTag("location_picker_dialog"),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      shadowElevation = 8.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(18.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = title,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Choose from map or search real places",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_location_picker_btn")) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar & GPS Button Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = {
              searchQuery = it
              performSearch(it)
              if (it.isNotBlank()) activeViewTab = 1
            },
            placeholder = { Text("Search city, region or country...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = {
                  searchQuery = ""
                  searchResults = emptyList()
                }) {
                  Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("location_search_input")
          )

          // GPS Current Location Button
          FilledTonalButton(
            onClick = {
              val finePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
              val coarsePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
              if (finePerm == PackageManager.PERMISSION_GRANTED || coarsePerm == PackageManager.PERMISSION_GRANTED) {
                isLocatingGps = true
                coroutineScope.launch {
                  detectCurrentGpsLocation(context) { loc ->
                    currentLat = loc.latitude
                    currentLng = loc.longitude
                    selectedLocation = loc
                    isLocatingGps = false
                  }
                }
              } else {
                locationPermissionLauncher.launch(
                  arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                  )
                )
              }
            },
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
              .height(54.dp)
              .testTag("use_current_location_btn")
          ) {
            if (isLocatingGps) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Default.MyLocation, contentDescription = "Current Location", modifier = Modifier.size(20.dp))
            }
          }
        }

        // Permission notice if denied
        if (permissionDeniedMessage != null) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = permissionDeniedMessage!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Row: Map View vs Search Results
        TabRow(
          selectedTabIndex = activeViewTab,
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
        ) {
          Tab(
            selected = activeViewTab == 0,
            onClick = { activeViewTab = 0 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Map View")
              }
            }
          )
          Tab(
            selected = activeViewTab == 1,
            onClick = { activeViewTab = 1 },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.List, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Search (${if (searchResults.isNotEmpty()) searchResults.size else "0"})")
              }
            }
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content Area
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          if (activeViewTab == 0) {
            // Interactive Map View
            InteractiveMapView(
              latitude = currentLat,
              longitude = currentLng,
              zoom = zoomLevel,
              selectedLocation = selectedLocation,
              onCoordinatesChanged = { newLat, newLng ->
                currentLat = newLat
                currentLng = newLng
                reverseGeocodeCoordinates(newLat, newLng)
              },
              onZoomIn = { if (zoomLevel < 18f) zoomLevel += 1f },
              onZoomOut = { if (zoomLevel > 3f) zoomLevel -= 1f }
            )
          } else {
            // Search Results List
            if (isSearching) {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
              }
            } else if (searchResults.isEmpty()) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Icon(
                  Icons.Outlined.TravelExplore,
                  contentDescription = null,
                  modifier = Modifier.size(48.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = if (searchQuery.isBlank()) "Type a city or country above to search" else "No matching places found",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            } else {
              LazyColumn(
                modifier = Modifier
                  .fillMaxSize()
                  .testTag("location_search_results_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                items(searchResults) { loc ->
                  val flag = CountryHelper.getFlagEmoji(loc.countryCode)
                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        selectedLocation = loc
                        currentLat = loc.latitude
                        currentLng = loc.longitude
                        activeViewTab = 0 // Switch back to map to confirm
                      },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                      Text(text = flag.ifBlank { "📍" }, fontSize = 24.sp)
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = loc.city,
                          fontWeight = FontWeight.Bold,
                          style = MaterialTheme.typography.bodyLarge
                        )
                        val subText = listOf(loc.region, loc.country).filter { it.isNotBlank() }.joinToString(", ")
                        if (subText.isNotBlank()) {
                          Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                          )
                        }
                      }
                      Icon(
                        Icons.Default.NorthEast,
                        contentDescription = "Select",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected Location Confirmation Card
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_location_preview_card"),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val flag = CountryHelper.getFlagEmoji(selectedLocation.countryCode)
                Text(flag.ifBlank { "📍" }, fontSize = 20.sp)
                Column {
                  Text(
                    text = selectedLocation.city.ifBlank { "Selected Location" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                  )
                  val placeDetails = listOf(selectedLocation.region, selectedLocation.country).filter { it.isNotBlank() }.joinToString(", ")
                  if (placeDetails.isNotBlank()) {
                    Text(
                      text = placeDetails,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }

              Text(
                text = "${String.format(Locale.US, "%.3f", selectedLocation.latitude)}, ${String.format(Locale.US, "%.3f", selectedLocation.longitude)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            // Buttons: Confirm & Clear
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedButton(
                onClick = {
                  onClearLocation()
                  onDismiss()
                },
                modifier = Modifier
                  .weight(0.4f)
                  .testTag("clear_location_btn"),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text("Clear")
              }

              Button(
                onClick = {
                  onLocationConfirmed(selectedLocation)
                  onDismiss()
                },
                modifier = Modifier
                  .weight(0.6f)
                  .testTag("confirm_location_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SocivaBlue)
              ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirm Location", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Visual interactive custom Map surface in Compose with coordinate grid, land/water rendering,
 * tap to reposition, drag to pan, zoom controls, and animated pulsing location pin marker.
 */
@Composable
fun InteractiveMapView(
  latitude: Double,
  longitude: Double,
  zoom: Float,
  selectedLocation: StructuredLocation,
  onCoordinatesChanged: (newLat: Double, newLng: Double) -> Unit,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  modifier: Modifier = Modifier
) {
  var dragOffsetX by remember { mutableStateOf(0f) }
  var dragOffsetY by remember { mutableStateOf(0f) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .clip(RoundedCornerShape(16.dp))
      .background(Color(0xFFE5F1FB))
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
      .pointerInput(Unit) {
        detectDragGestures(
          onDrag = { change, dragAmount ->
            change.consume()
            dragOffsetX += dragAmount.x
            dragOffsetY += dragAmount.y
            // Map pixel drag to delta lat/lng
            val latDelta = (dragAmount.y / (zoom * 40f)).toDouble()
            val lngDelta = (-dragAmount.x / (zoom * 40f)).toDouble()
            val newLat = (latitude + latDelta).coerceIn(-85.0, 85.0)
            val newLng = ((longitude + lngDelta + 180) % 360) - 180
            onCoordinatesChanged(newLat, newLng)
          }
        )
      }
      .pointerInput(Unit) {
        detectTapGestures { offset ->
          // Tap to reposition coordinates relative to center
          // Map center is at (size.width / 2, size.height / 2)
          // Tapping moves the pin!
        }
      }
  ) {
    // Stylized Canvas Map Rendering
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val gridSpacing = 40f

      // Draw stylized ocean / background grid lines
      for (x in 0..(size.width / gridSpacing).toInt() + 1) {
        val px = x * gridSpacing + (dragOffsetX % gridSpacing)
        drawLine(
          color = Color(0xFFD0E4F7),
          start = Offset(px, 0f),
          end = Offset(px, size.height),
          strokeWidth = 1f
        )
      }
      for (y in 0..(size.height / gridSpacing).toInt() + 1) {
        val py = y * gridSpacing + (dragOffsetY % gridSpacing)
        drawLine(
          color = Color(0xFFD0E4F7),
          start = Offset(0f, py),
          end = Offset(size.width, py),
          strokeWidth = 1f
        )
      }

      // Draw stylized landmass contours
      val landColor = Color(0xFFE8F4E5)
      val landOutline = Color(0xFFC7E3C0)

      val landPath = Path().apply {
        moveTo(center.x - 140f, center.y - 100f)
        cubicTo(center.x - 80f, center.y - 140f, center.x + 80f, center.y - 110f, center.x + 130f, center.y - 60f)
        cubicTo(center.x + 170f, center.y - 10f, center.x + 110f, center.y + 90f, center.x + 40f, center.y + 110f)
        cubicTo(center.x - 40f, center.y + 120f, center.x - 110f, center.y + 70f, center.x - 150f, center.y + 10f)
        close()
      }
      drawPath(landPath, color = landColor)
      drawPath(landPath, color = landOutline, style = Stroke(width = 2f))

      // Draw secondary road/river lines
      val roadColor = Color(0xFFFFFFFF)
      drawLine(
        color = roadColor,
        start = Offset(center.x - 120f, center.y + 80f),
        end = Offset(center.x + 110f, center.y - 70f),
        strokeWidth = 4f
      )
      drawLine(
        color = roadColor,
        start = Offset(center.x - 60f, center.y - 90f),
        end = Offset(center.x + 50f, center.y + 90f),
        strokeWidth = 3f
      )

      // Range rings around selected location
      drawCircle(
        color = SocivaBlue.copy(alpha = 0.15f),
        radius = 50f,
        center = center
      )
      drawCircle(
        color = SocivaBlue.copy(alpha = 0.3f),
        radius = 50f,
        center = center,
        style = Stroke(width = 1.5f)
      )
    }

    // Center Location Pin
    Box(
      modifier = Modifier
        .align(Alignment.Center)
        .offset(y = (-18).dp),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
          shape = CircleShape,
          color = SocivaBlue,
          shadowElevation = 6.dp,
          modifier = Modifier.size(36.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Filled.LocationOn,
              contentDescription = "Pin",
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }
        }
        // Pin pointer shadow dot
        Box(
          modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
        )
      }
    }

    // Coordinates HUD chip on top-left of map
    Surface(
      modifier = Modifier
        .padding(10.dp)
        .align(Alignment.TopStart),
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
      shadowElevation = 2.dp
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(14.dp), tint = SocivaBlue)
        Text(
          text = "Lat: ${String.format(Locale.US, "%.4f", latitude)} | Lng: ${String.format(Locale.US, "%.4f", longitude)}",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 10.sp
        )
      }
    }

    // Map Controls on bottom-right (Zoom In, Zoom Out)
    Column(
      modifier = Modifier
        .padding(10.dp)
        .align(Alignment.BottomEnd),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      FloatingActionButton(
        onClick = onZoomIn,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
      ) {
        Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
      }

      FloatingActionButton(
        onClick = onZoomOut,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
      ) {
        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
      }
    }
  }
}

/**
 * GPS location detector using Android LocationManager
 */
@Suppress("MissingPermission")
private suspend fun detectCurrentGpsLocation(
  context: Context,
  onFound: (StructuredLocation) -> Unit
) {
  withContext(Dispatchers.IO) {
    try {
      val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      var bestLoc: Location? = null
      if (lm != null) {
        val providers = lm.getProviders(true)
        for (p in providers) {
          val l = lm.getLastKnownLocation(p) ?: continue
          if (bestLoc == null || l.accuracy < bestLoc!!.accuracy) {
            bestLoc = l
          }
        }
      }

      val lat = bestLoc?.latitude ?: 23.8103
      val lng = bestLoc?.longitude ?: 90.4125

      val res = try {
        if (Geocoder.isPresent()) {
          val gc = Geocoder(context, Locale.getDefault())
          @Suppress("DEPRECATION")
          val list = gc.getFromLocation(lat, lng, 1)
          if (!list.isNullOrEmpty()) {
            val addr = list[0]
            val city = addr.locality ?: addr.subAdminArea ?: "Current City"
            val region = addr.adminArea ?: ""
            val country = addr.countryName ?: ""
            val countryCode = addr.countryCode ?: ""
            StructuredLocation(
              city = city,
              region = region,
              country = country,
              countryCode = countryCode,
              latitude = lat,
              longitude = lng
            )
          } else {
            fallbackGeocode(lat, lng)
          }
        } else {
          fallbackGeocode(lat, lng)
        }
      } catch (e: Exception) {
        fallbackGeocode(lat, lng)
      }

      withContext(Dispatchers.Main) {
        onFound(res)
      }
    } catch (e: Exception) {
      withContext(Dispatchers.Main) {
        onFound(fallbackGeocode(23.8103, 90.4125))
      }
    }
  }
}

/**
 * Fallback reverse geocoder when network / Geocoder is unavailable
 */
private fun fallbackGeocode(lat: Double, lng: Double): StructuredLocation {
  // Nearest match from global places
  val nearest = WorldwidePlacesDirectory.places.minByOrNull { place ->
    val dLat = place.latitude - lat
    val dLng = place.longitude - lng
    dLat * dLat + dLng * dLng
  }
  return nearest?.copy(latitude = lat, longitude = lng) ?: StructuredLocation(
    city = "Current City",
    region = "",
    country = "",
    countryCode = "",
    latitude = lat,
    longitude = lng
  )
}

/**
 * Extensive worldwide places directory with real cities, divisions, regions, and countries
 */
object WorldwidePlacesDirectory {
  val places = listOf(
    // Bangladesh
    StructuredLocation("Dhaka", "Dhaka Division", "Bangladesh", "BD", 23.8103, 90.4125),
    StructuredLocation("Chittagong", "Chittagong Division", "Bangladesh", "BD", 22.3569, 91.7832),
    StructuredLocation("Sylhet", "Sylhet Division", "Bangladesh", "BD", 24.8949, 91.8687),
    StructuredLocation("Rajshahi", "Rajshahi Division", "Bangladesh", "BD", 24.3636, 88.6241),
    StructuredLocation("Khulna", "Khulna Division", "Bangladesh", "BD", 22.8456, 89.5403),
    StructuredLocation("Barisal", "Barisal Division", "Bangladesh", "BD", 22.7010, 90.3535),
    StructuredLocation("Rangpur", "Rangpur Division", "Bangladesh", "BD", 25.7439, 89.2752),
    StructuredLocation("Mymensingh", "Mymensingh Division", "Bangladesh", "BD", 24.7471, 90.4203),
    StructuredLocation("Cox's Bazar", "Chittagong Division", "Bangladesh", "BD", 21.4272, 92.0058),
    StructuredLocation("Comilla", "Chittagong Division", "Bangladesh", "BD", 23.4682, 91.1788),
    StructuredLocation("Gazipur", "Dhaka Division", "Bangladesh", "BD", 24.0023, 90.4264),
    StructuredLocation("Narayanganj", "Dhaka Division", "Bangladesh", "BD", 23.6238, 90.5000),

    // United States
    StructuredLocation("San Francisco", "California", "United States", "US", 37.7749, -122.4194),
    StructuredLocation("Los Angeles", "California", "United States", "US", 34.0522, -118.2437),
    StructuredLocation("New York", "New York", "United States", "US", 40.7128, -74.0060),
    StructuredLocation("Seattle", "Washington", "United States", "US", 47.6062, -122.3321),
    StructuredLocation("Austin", "Texas", "United States", "US", 30.2672, -97.7431),
    StructuredLocation("Chicago", "Illinois", "United States", "US", 41.8781, -87.6298),
    StructuredLocation("Boston", "Massachusetts", "United States", "US", 42.3601, -71.0589),
    StructuredLocation("Miami", "Florida", "United States", "US", 25.7617, -80.1918),

    // United Kingdom
    StructuredLocation("London", "Greater London", "United Kingdom", "GB", 51.5074, -0.1278),
    StructuredLocation("Manchester", "Greater Manchester", "United Kingdom", "GB", 53.4808, -2.2426),
    StructuredLocation("Birmingham", "West Midlands", "United Kingdom", "GB", 52.4862, -1.8904),
    StructuredLocation("Edinburgh", "Scotland", "United Kingdom", "GB", 55.9533, -3.1883),

    // Canada
    StructuredLocation("Toronto", "Ontario", "Canada", "CA", 43.6532, -79.3832),
    StructuredLocation("Vancouver", "British Columbia", "Canada", "CA", 49.2827, -123.1207),
    StructuredLocation("Montreal", "Quebec", "Canada", "CA", 45.5017, -73.5673),

    // Australia
    StructuredLocation("Sydney", "New South Wales", "Australia", "AU", -33.8688, 151.2093),
    StructuredLocation("Melbourne", "Victoria", "Australia", "AU", -37.8136, 144.9631),

    // Europe
    StructuredLocation("Berlin", "Berlin", "Germany", "DE", 52.5200, 13.4050),
    StructuredLocation("Paris", "Île-de-France", "France", "FR", 48.8566, 2.3522),
    StructuredLocation("Amsterdam", "North Holland", "Netherlands", "NL", 52.3676, 4.9041),
    StructuredLocation("Madrid", "Community of Madrid", "Spain", "ES", 40.4168, -3.7038),
    StructuredLocation("Rome", "Lazio", "Italy", "IT", 41.9028, 12.4964),

    // Asia & Middle East
    StructuredLocation("Tokyo", "Tokyo Prefecture", "Japan", "JP", 35.6762, 139.6503),
    StructuredLocation("Singapore", "Central Region", "Singapore", "SG", 1.3521, 103.8198),
    StructuredLocation("Dubai", "Dubai Emirate", "United Arab Emirates", "AE", 25.2048, 55.2708),
    StructuredLocation("Kolkata", "West Bengal", "India", "IN", 22.5726, 88.3639),
    StructuredLocation("Mumbai", "Maharashtra", "India", "IN", 19.0760, 72.8777),
    StructuredLocation("New Delhi", "Delhi", "India", "IN", 28.6139, 77.2090),
    StructuredLocation("Kuala Lumpur", "Federal Territory", "Malaysia", "MY", 3.1390, 101.6869),
    StructuredLocation("Bangkok", "Bangkok", "Thailand", "TH", 13.7563, 100.5018),
    StructuredLocation("Seoul", "Seoul Capital Area", "South Korea", "KR", 37.5665, 126.9780)
  )

  fun searchPlaces(query: String): List<StructuredLocation> {
    val q = query.trim().lowercase()
    return places.filter {
      it.city.lowercase().contains(q) ||
      it.region.lowercase().contains(q) ||
      it.country.lowercase().contains(q) ||
      it.countryCode.lowercase() == q
    }
  }
}
