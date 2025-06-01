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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.accessiblemap.ui.theme.AccessibleMapTheme
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import kotlinx.serialization.builtins.ListSerializer

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


@Serializable
data class UserAccessibilityFeedback(
    val lat: Double,
    val lng: Double,
    val placeName: String?,
    val wheelchairEntrance: Boolean = false,
    val wheelchairParking: Boolean = false,
    val wheelchairRestroom: Boolean = false,
    val wheelchairSeating: Boolean = false,
    val rampAvailable: Boolean = false,
    val elevatorAvailable: Boolean = false,
    val brailleSupport: Boolean = false,
    val signLanguageSupport: Boolean = false,
    val suitableForVisuallyImpaired: Boolean = false,
    val suitableForPhysicallyDisabled: Boolean = false,
    val suitableForHearingImpaired: Boolean = false,
    val visionMenu: Boolean = false,
    val visionStaff: Boolean = false,
    val generalAccessible: Boolean = false,
    val notes: String? = null
)

fun saveUserFeedbacks(context: Context, feedbacks: List<UserAccessibilityFeedback>) {
    val prefs = context.getSharedPreferences("user_feedbacks", Context.MODE_PRIVATE)
    val json = kotlinx.serialization.json.Json.encodeToString(ListSerializer(UserAccessibilityFeedback.serializer()), feedbacks)
    prefs.edit().putString("feedbacks", json).apply()
}

fun loadUserFeedbacks(context: Context): List<UserAccessibilityFeedback> {
    val prefs = context.getSharedPreferences("user_feedbacks", Context.MODE_PRIVATE)
    val json = prefs.getString("feedbacks", null) ?: return emptyList()
    return try {
        kotlinx.serialization.json.Json.decodeFromString(ListSerializer(UserAccessibilityFeedback.serializer()), json)
    } catch (e: Exception) {
        emptyList()
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
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

            scope.launch {
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

    // Bottom sheet için state'ler
    val bottomSheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var selectedPlaceName by remember { mutableStateOf<String?>(null) }
    // Kullanıcıdan alınan feedback'ler localde tutuluyor
    val userFeedbacks = remember { mutableStateListOf<UserAccessibilityFeedback>().apply { addAll(loadUserFeedbacks(context)) } }
    // Chip seçim state'leri
    var wheelchairEntrance by remember { mutableStateOf(false) }
    var wheelchairParking by remember { mutableStateOf(false) }
    var wheelchairRestroom by remember { mutableStateOf(false) }
    var wheelchairSeating by remember { mutableStateOf(false) }
    var visionMenu by remember { mutableStateOf(false) }
    var visionStaff by remember { mutableStateOf(false) }
    var generalAccessible by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    var rampAvailable by remember { mutableStateOf(false) }
    var elevatorAvailable by remember { mutableStateOf(false) }
    var brailleSupport by remember { mutableStateOf(false) }
    var signLanguageSupport by remember { mutableStateOf(false) }
    var suitableForVisuallyImpaired by remember { mutableStateOf(false) }
    var suitableForPhysicallyDisabled by remember { mutableStateOf(false) }
    var suitableForHearingImpaired by remember { mutableStateOf(false) }

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
            ),
            onMapLongClick = { latLng ->
                selectedLatLng = latLng
                selectedPlaceName = null // İleride reverse geocode ile isim alınabilir
                // Chip'leri sıfırla
                wheelchairEntrance = false
                wheelchairParking = false
                wheelchairRestroom = false
                wheelchairSeating = false
                visionMenu = false
                visionStaff = false
                generalAccessible = false
                notes = ""
                showBottomSheet = true
            }
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
                    var accessibilityOptionsCount = 0
                    if(placeInfo.accessibilityOptions!=null){
                        if(placeInfo.accessibilityOptions.wheelchairAccessibleEntrance == true)
                            accessibilityOptionsCount++
                        if(placeInfo.accessibilityOptions.wheelchairAccessibleRestroom == true)
                            accessibilityOptionsCount++
                        if(placeInfo.accessibilityOptions.wheelchairAccessibleParking == true)
                            accessibilityOptionsCount++
                        if(placeInfo.accessibilityOptions.wheelchairAccessibleSeating == true)
                            accessibilityOptionsCount++
                    }
                    val markerHue = when (accessibilityOptionsCount) {
                        1 -> BitmapDescriptorFactory.HUE_RED
                        2 -> BitmapDescriptorFactory.HUE_ORANGE
                        3 -> BitmapDescriptorFactory.HUE_YELLOW
                        4 -> BitmapDescriptorFactory.HUE_GREEN
                        else -> BitmapDescriptorFactory.HUE_ROSE
                    }
                    MarkerInfoWindow(
                        state = MarkerState(position = LatLng(placeInfo.latitude, placeInfo.longitude)),
                        title = placeInfo.name ?: "Bilinmeyen Mekan",
                        snippet = "Erişilebilirlik detayları için dokunun",
                        icon = BitmapDescriptorFactory.defaultMarker(markerHue)
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

            // Kendi kaydedilen yerleri göster (beyaz pin)
            userFeedbacks.forEach { feedback ->
                MarkerInfoWindow(
                    state = MarkerState(position = LatLng(feedback.lat, feedback.lng)),
                    title = feedback.placeName ?: "Kayıtlı Yer",
                    snippet = "Detaylar için dokunun",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)
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
                            feedback.placeName ?: "Kayıtlı Yer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            getUserFeedbackSummary(feedback),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        feedback.notes?.let {
                            if (it.isNotBlank()) Text(
                                "Not: $it",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
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

        if (showBottomSheet && selectedLatLng != null) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = bottomSheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = selectedPlaceName?.takeIf { it.isNotBlank() } ?: "Seçilen Konum: ${selectedLatLng!!.latitude.format(5)}, ${selectedLatLng!!.longitude.format(5)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text("Erişilebilirlik Özellikleri:", fontWeight = FontWeight.SemiBold)
                    val chipOptions = listOf(
                        Triple("Giriş Uygun", wheelchairEntrance, { wheelchairEntrance = !wheelchairEntrance }),
                        Triple("Otopark Uygun", wheelchairParking, { wheelchairParking = !wheelchairParking }),
                        Triple("Tuvalet Uygun", wheelchairRestroom, { wheelchairRestroom = !wheelchairRestroom }),
                        Triple("Oturma Alanı Uygun", wheelchairSeating, { wheelchairSeating = !wheelchairSeating }),
                        Triple("Rampa Var", rampAvailable, { rampAvailable = !rampAvailable }),
                        Triple("Asansör Var", elevatorAvailable, { elevatorAvailable = !elevatorAvailable }),
                        Triple("Braille Desteği", brailleSupport, { brailleSupport = !brailleSupport }),
                        Triple("İşaret Dili Desteği", signLanguageSupport, { signLanguageSupport = !signLanguageSupport }),
                        Triple("Görme Engelliler İçin Uygun", suitableForVisuallyImpaired, { suitableForVisuallyImpaired = !suitableForVisuallyImpaired }),
                        Triple("Fiziksel Engelliler İçin Uygun", suitableForPhysicallyDisabled, { suitableForPhysicallyDisabled = !suitableForPhysicallyDisabled }),
                        Triple("İşitme Engelliler İçin Uygun", suitableForHearingImpaired, { suitableForHearingImpaired = !suitableForHearingImpaired }),
                        Triple("Görme Engelli Menüsü", visionMenu, { visionMenu = !visionMenu }),
                        Triple("Görme Engelliye Yardımcı Personel", visionStaff, { visionStaff = !visionStaff }),
                        Triple("Genel Olarak Uygun", generalAccessible, { generalAccessible = !generalAccessible })
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                        userScrollEnabled = false
                    ) {
                        items(chipOptions) { (label, selected, onClick) ->
                            FilterChip(
                                selected = selected,
                                onClick = onClick,
                                label = { Text(label) },
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Ek Notlar") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = {
                            // Feedback'i kaydet
                            userFeedbacks.add(
                                UserAccessibilityFeedback(
                                    lat = selectedLatLng!!.latitude,
                                    lng = selectedLatLng!!.longitude,
                                    placeName = selectedPlaceName,
                                    wheelchairEntrance = wheelchairEntrance,
                                    wheelchairParking = wheelchairParking,
                                    wheelchairRestroom = wheelchairRestroom,
                                    wheelchairSeating = wheelchairSeating,
                                    rampAvailable = rampAvailable,
                                    elevatorAvailable = elevatorAvailable,
                                    brailleSupport = brailleSupport,
                                    signLanguageSupport = signLanguageSupport,
                                    suitableForVisuallyImpaired = suitableForVisuallyImpaired,
                                    suitableForPhysicallyDisabled = suitableForPhysicallyDisabled,
                                    suitableForHearingImpaired = suitableForHearingImpaired,
                                    visionMenu = visionMenu,
                                    visionStaff = visionStaff,
                                    generalAccessible = generalAccessible,
                                    notes = notes.ifBlank { null }
                                )
                            )
                            saveUserFeedbacks(context, userFeedbacks)
                            showBottomSheet = false
                        },
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
                    ) {
                        Text("Kaydet")
                    }
                }
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

fun getUserFeedbackSummary(feedback: UserAccessibilityFeedback): String {
    val features = mutableListOf<String>()
    if (feedback.wheelchairEntrance) features.add("✔️ Giriş Uygun")
    if (feedback.wheelchairParking) features.add("✔️ Otopark Uygun")
    if (feedback.wheelchairRestroom) features.add("✔️ Tuvalet Uygun")
    if (feedback.wheelchairSeating) features.add("✔️ Oturma Alanı Uygun")
    if (feedback.rampAvailable) features.add("✔️ Rampa Var")
    if (feedback.elevatorAvailable) features.add("✔️ Asansör Var")
    if (feedback.brailleSupport) features.add("✔️ Braille Desteği")
    if (feedback.signLanguageSupport) features.add("✔️ İşaret Dili Desteği")
    if (feedback.suitableForVisuallyImpaired) features.add("✔️ Görme Engelliler İçin Uygun")
    if (feedback.suitableForPhysicallyDisabled) features.add("✔️ Fiziksel Engelliler İçin Uygun")
    if (feedback.suitableForHearingImpaired) features.add("✔️ İşitme Engelliler İçin Uygun")
    if (feedback.visionMenu) features.add("✔️ Görme Engelli Menüsü")
    if (feedback.visionStaff) features.add("✔️ Görme Engelliye Yardımcı Personel")
    if (feedback.generalAccessible) features.add("✔️ Genel Olarak Uygun")
    return if (features.isEmpty()) "Belirtilmiş erişilebilirlik özelliği yok." else features.joinToString("\n")
}
