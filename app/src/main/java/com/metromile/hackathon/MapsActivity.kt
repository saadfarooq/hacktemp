package com.metromile.hackathon

import android.Manifest
import android.annotation.SuppressLint
import android.databinding.DataBindingUtil
import android.databinding.ObservableField
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.places.Places
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.tbruyelle.rxpermissions2.RxPermissions
import me.tatarka.bindingcollectionadapter2.ItemBinding
import net.danlew.android.joda.JodaTimeAndroid
import org.joda.time.DateTime


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private var mGoogleApiClient: GoogleApiClient? = null
    private val TAG = "MapsActivity"
    lateinit var mapView : MapView
    val parking = ObservableField<ParkingViewModel>()
    val photoItemBinding = ItemBinding.of<String>(BR.imageUrl, R.layout.recycler_image_view)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<MapsActivityBinding>(this, R.layout.activity_maps)
        binding.activity = this

        JodaTimeAndroid.init(this)


        mapView = findViewById<MapView>(R.id.map)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        mGoogleApiClient = GoogleApiClient.Builder(this)
                .addApi(Places.GEO_DATA_API)
                .enableAutoManage(this, { Log.d(TAG, "Connection result: $it") })
                .build()

    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                        this, R.raw.style_json))

        RxPermissions(this).request(Manifest.permission.ACCESS_FINE_LOCATION)
                .filter { it }
                .subscribe({ moveToCurrentLocation(googleMap) },
                        { Log.e(TAG, "Error getting permissions", it) })
    }

    @SuppressLint("MissingPermission")
    private fun moveToCurrentLocation(googleMap: GoogleMap) {
        googleMap.isMyLocationEnabled = true
        LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener {
                    Log.d(TAG, "Location: $it")
                    LatLng(it.latitude, it.longitude)
                            .let {
                                googleMap.apply {
                                    isBuildingsEnabled = true
                                    animateCamera(CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                    .target(it)      // Sets the center of the map to Mountain View
                                                    .zoom(15f)                   // Sets the zoom
                                                    .bearing(90f)                // Sets the orientation of the camera to east
                                                    .tilt(15f)                   // Sets the tilt of the camera to 30 degrees
                                                    .build()
                                    ))
                                }
                            }

                    // https://api.parkwhiz.com/v4/quotes/?q=coordinates:41.8857256,-87.6369590
                    // &start_time=2017-12-23T12:00
                    // &end_time=2017-12-23T20:00
                    // &api_key=62d882d8cfe5680004fa849286b6ce20

                    Fuel.Companion.get("https://api.parkwhiz.com/v4/quotes/",
                            listOf("q" to "coordinates:${it.latitude},${it.longitude}",
                                    "start_time" to DateTime.now().toString(),
                                    "end_time" to DateTime.now().plusDays(1).toString(),
                                    "api_key" to "62d882d8cfe5680004fa849286b6ce20")
                    )
                            .responseObject(ParkingResponse.Deserializer(), { _, _, result ->
                                val response = result.get()
                                Log.d(TAG, "result: $response")
                                showNearbyPlaces(googleMap, response)
                            })
                }
    }

    private fun showNearbyPlaces(googleMap: GoogleMap, response: List<ParkingResponse>) {
        response.filter {
            it.purchase_options.any { it.amenities.contains(Amenity("Covered", true)) }
        }
        response.forEach {
            googleMap.addMarker(MarkerOptions()
                    .position(it._embedded.parkingLocation.entrances[0].latLng())
//                    .title(it._embedded.parkingLocation.name)
            ).apply { tag = it }
        }

        googleMap.setOnMarkerClickListener {
            val parkingResponse = it.tag as ParkingResponse

            parking.set(
                    ParkingViewModel(
                            parkingResponse._embedded.parkingLocation.name,
                            parkingResponse._embedded.parkingLocation.photos
                                    .map { it.sizes["original"] }
                                    .filterNotNull()
                                    .map { it.url }))
            false
        }
    }
}

data class ParkingResponse(val _embedded: EmbeddedParkingData,
                           val purchase_options: List<ParkingPurchaseOptions>) {
    class Deserializer : ResponseDeserializable<List<ParkingResponse>> {
        override fun deserialize(content: String): List<ParkingResponse> {
            return Gson().fromJson(content, object: TypeToken<List<ParkingResponse>>(){}.type)
        }
    }
}

data class ParkingViewModel(val name : String, val photos: List<String>)

data class ParkingPurchaseOptions(val amenities: List<Amenity>)

data class Amenity(val name: String, val enabled: Boolean)
data class EmbeddedParkingData(@SerializedName("pw:location") val parkingLocation: ParkingLocation)
data class ParkingPhoto(val sizes: Map<String, PhotoDetail>)
data class PhotoDetail(@SerializedName("URL") val url: String, val width: String, val height: String)
data class ParkingLocation(val name: String, val entrances: List<ParkingEntrance>, val photos: List<ParkingPhoto>)
data class ParkingEntrance(private val coordinates: List<Double>) {
    fun latLng() : LatLng {
        return LatLng(coordinates[0], coordinates[1])
    }
}

//data class PlaceResult(val geometry: Geometry, val types: List<String>, val vicinity: String, val place_id: String)
//data class Geometry(val location: PlacesLocation)
//data class PlacesLocation(val lat: Double, val lng: Double) {
//    fun toLatLng() : LatLng {
//        return LatLng(lat, lng)
//    }
//}
