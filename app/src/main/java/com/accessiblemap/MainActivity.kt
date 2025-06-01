package com.accessiblemap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf // Değişti: mutableStateOf'tan
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope // CoroutineScope için
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.accessiblemap.ui.theme.AccessibleMapTheme // temanız
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerInfoWindow
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ApiAccessibilityOptions(
    val wheelchairAccessibleParking: Boolean? = null,
    val wheelchairAccessibleEntrance: Boolean? = null,
    val wheelchairAccessibleRestroom: Boolean? = null,
    val wheelchairAccessibleSeating: Boolean? = null
)

@Serializable
data class ApiPlaceInfo(
    val place_id: String,
    val name: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val accessibilityOptions: ApiAccessibilityOptions?,
    val hasAtLeastOneTrueAccessibilityFeature: Boolean?,
    val googlePlaceTypes: List<String>? = null,
    val searched_as_type: String? = null,
    val vicinity: String? = null,
    val rating: Float? = null,
    val user_ratings_total: Int? = null,
    val business_status: String? = null,
    val icon: String? = null
) {
    fun getAccessibilitySummaryForMarker(): String {
        if (hasAtLeastOneTrueAccessibilityFeature != true) return "Belirtilmiş erişilebilirlik özelliği yok."
        val features = mutableListOf<String>()
        accessibilityOptions?.let {
            if (it.wheelchairAccessibleEntrance == true) features.add("✔️ Giriş Uygun")
            if (it.wheelchairAccessibleParking == true) features.add("✔️ Otopark Uygun")
            if (it.wheelchairAccessibleRestroom == true) features.add("✔️ Tuvalet Uygun")
            if (it.wheelchairAccessibleSeating == true) features.add("✔️ Oturma Alanı Uygun")
        }
        return if (features.isEmpty()) "Belirtilmiş erişilebilirlik özelliği yok." else features.joinToString("\n")
    }
}


// Ktor HTTP Client
val ktorHttpClient = HttpClient(Android) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}


suspend fun fetchAccessiblePlacesFromApi(latitude: Double, longitude: Double, types: String = "cafe,restaurant,store", radius: Int = 1500): List<ApiPlaceInfo> {
    val apiUrl = "http://10.0.2.2:5000/nearby-places-with-accessibility"
    Log.d("ApiService", "Requesting API: $apiUrl?lat=$latitude&lon=$longitude&types=$types&radius=$radius")
    return try {
        ktorHttpClient.get(apiUrl) {
            parameter("lat", latitude)
            parameter("lon", longitude)
            parameter("types", types)
            parameter("radius", radius)
        }.body<List<ApiPlaceInfo>>()
    } catch (e: Exception) {
        Log.e("ApiService", "API isteği başarısız: ${e.localizedMessage}", e)
        emptyList()
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            AccessibleMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocationPermissionAndMapScreen()
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun LocationPermissionAndMapScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userLocation by remember { mutableStateOf<Location?>(null) }
    val apiAccessiblePlaces = remember { mutableStateListOf<ApiPlaceInfo>() }
    var isLoadingApiPlaces by remember { mutableStateOf(false) }
    var initialApiFetchAttempted by remember { mutableStateOf(false) }
    var initialCameraZoomDone by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationRequest = remember {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000L)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(30000L)
            .setMaxUpdateDelayMillis(60000L)
            .build()
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    userLocation = location
                }
            }
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission = fineLocationGranted && coarseLocationGranted

        if (hasLocationPermission) {
            Log.i("PermissionGrant", "Location permission GRANTED.")
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                Log.e("PermissionGrant", "SecurityException after permission grant.", e)
            }
        } else {
            Log.w("PermissionGrant", "Location permission DENIED.")
        }
    }

    LaunchedEffect(key1 = hasLocationPermission) {
        if (hasLocationPermission) {
            Log.d("LaunchedEffectPerm", "Permission IS granted. Requesting location updates.")
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                Log.e("LaunchedEffectPerm", "SecurityException on requesting location updates.", e)
            }
        } else {
            Log.d("LaunchedEffectPerm", "Permission NOT granted. Launching permission request if needed.")
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    LaunchedEffect(userLocation, hasLocationPermission, initialApiFetchAttempted) {
        if (hasLocationPermission && userLocation != null && !initialApiFetchAttempted) {
            initialApiFetchAttempted = true
            isLoadingApiPlaces = true
            Log.i("ApiService", "Kullanıcı konumu: ${userLocation!!.latitude}, ${userLocation!!.longitude}. Flask API çağrılıyor...")

            scope.launch { // Coroutine içinde API çağrısı
                val fetchedPlaces = fetchAccessiblePlacesFromApi(
                    userLocation!!.latitude,
                    userLocation!!.longitude,
                    types = "cafe,restaurant,store,park,museum,hospital", // İstediğiniz türler
                    radius = 2000 // Arama yarıçapı (metre)
                )
                apiAccessiblePlaces.clear()
                apiAccessiblePlaces.addAll(fetchedPlaces.filter { it.hasAtLeastOneTrueAccessibilityFeature == true })
                isLoadingApiPlaces = false
                Log.i("ApiService", "${apiAccessiblePlaces.size} adet erişilebilir yer API'den yüklendi.")
                if (fetchedPlaces.isNotEmpty() && apiAccessiblePlaces.isEmpty()) {
                    Log.w("ApiService", "API'den yerler geldi ancak hiçbiri 'hasAtLeastOneTrueAccessibilityFeature' kriterini karşılamadı.")
                }
            }
        } else if (hasLocationPermission && userLocation == null && !initialApiFetchAttempted) {
            Log.i("ApiService", "Konum izni var ama userLocation henüz null. API çağrısı için bekleniyor...")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d("DisposableEffect", "Removing location updates.")
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    val defaultInitialCameraPosition = LatLng(41.0082, 28.9784) // İstanbul
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultInitialCameraPosition, 10f)
    }

    LaunchedEffect(userLocation, initialCameraZoomDone) {
        userLocation?.let { loc ->
            if (!initialCameraZoomDone) {
                val userLatLng = LatLng(loc.latitude, loc.longitude)
                Log.d("CameraUpdate", "Animating camera to initial user location: $userLatLng")
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newCameraPosition(
                        CameraPosition(userLatLng, 15f, 0f, 0f)
                    ),
                    durationMs = 1000
                )
                initialCameraZoomDone = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = hasLocationPermission
            ),
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission
            )
        ) {
            userLocation?.let { loc ->
                Marker(
                    state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                    title = "Konumunuz",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE) // Mavi renk
                )
            }

            apiAccessiblePlaces.forEach { placeInfo ->
                if (placeInfo.latitude != null && placeInfo.longitude != null) {
                    MarkerInfoWindow(
                        state = MarkerState(position = LatLng(placeInfo.latitude, placeInfo.longitude)),
                        title = placeInfo.name ?: "Bilinmeyen Mekan",
                        snippet = "Erişilebilirlik detayları için dokunun"
                    ) { marker ->
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                placeInfo.name ?: "Bilinmeyen Mekan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            placeInfo.address?.let {
                                Text(
                                    it,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                                )
                            }
                            Text(
                                placeInfo.getAccessibilitySummaryForMarker(),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (isLoadingApiPlaces) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (!hasLocationPermission && userLocation == null && !initialApiFetchAttempted) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = {
                        requestPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }) { Text("Tekrar Dene") }
                }
            ) {
                Text("Konum izni reddedildi. Uygulamanın çalışması için izin vermelisiniz.")
            }
        }
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
}