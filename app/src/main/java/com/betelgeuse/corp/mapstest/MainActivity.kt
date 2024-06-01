package com.betelgeuse.corp.mapstest

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKit
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import android.Manifest
import android.graphics.Color
import android.graphics.PointF
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouter
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.DrivingSession.DrivingRouteListener
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.RotationType
import com.yandex.mapkit.map.VisibleRegionUtils
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManager
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.Session
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.Error
import com.yandex.runtime.image.ImageProvider
import com.yandex.runtime.network.NetworkError
import com.yandex.runtime.network.RemoteError

class MainActivity : AppCompatActivity(), UserLocationObjectListener, Session.SearchListener, CameraListener , DrivingRouteListener {

    lateinit var mapView: MapView
    lateinit var trafficBtn: Button
    lateinit var locationMapKit: UserLocationLayer
    lateinit var searchEditText: EditText
    lateinit var searchManager: SearchManager
    lateinit var searchSession: Session
    private var isUserInteractingWithMap = false
    private var userLocation: Point? = null

    //Точка назначения (Б)
    private val ROUTE_END_LOCATION = Point(56.833933, 60.635647)

    private var mapObjects: MapObjectCollection? = null
    private var drivingRouter: DrivingRouter? = null
    private var drivingSession: DrivingSession? = null

    private fun submitQuery(query: String) {
        searchSession = searchManager.submit(query, VisibleRegionUtils.toPolygon(mapView.map.visibleRegion),
            SearchOptions(), this
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapKitFactory.setApiKey("58e0941b-794f-497e-ba2e-a3cb4ac457cd")
        MapKitFactory.initialize(this)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        requestLocationPermission()
        searchEditText = findViewById(R.id.searchID)

        mapView = findViewById(R.id.mapView)
        //Отправная точка (А)
        mapView.map.move(CameraPosition(Point(55.787134, 37.464118), 11.0f, 0.0f, 0.0f),
            Animation(Animation.Type.SMOOTH, 300f), null)
        val mapKit: MapKit = MapKitFactory.getInstance()
        val probs = mapKit.createTrafficLayer(mapView.mapWindow)
        var probsIsOn = false
        trafficBtn = findViewById(R.id.trafficBtn)
        trafficBtn.setOnClickListener {
            when (probsIsOn) {
                false -> {
                    probsIsOn = true
                    probs.isTrafficVisible = true
                    trafficBtn.setBackgroundResource(R.drawable.visibility_on)
                }
                true -> {
                    probsIsOn = false
                    probs.isTrafficVisible = false
                    trafficBtn.setBackgroundResource(R.drawable.visibility_off)
                }
            }
        }
        locationMapKit = mapKit.createUserLocationLayer(mapView.mapWindow)

        locationMapKit.isVisible = true
        locationMapKit.setObjectListener(this)

        SearchFactory.initialize(this)

        searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
        mapView.map.addCameraListener(this)
        searchEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitQuery(searchEditText.text.toString())
            }
            false
        }

        drivingRouter = DirectionsFactory.getInstance().createDrivingRouter()
        mapObjects = mapView.map.mapObjects.addCollection()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION_PERMISSION_CODE)
        }
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION_CODE = 1
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onStart() {
        mapView.onStart()
        MapKitFactory.getInstance().onStart()
        super.onStart()
    }

    override fun onObjectAdded(userLocationView: UserLocationView) {
        //При раскоментировании закрепляет камеру на пользователе
//        locationMapKit.setAnchor(
//            PointF((mapView.width * 0.5).toFloat(), (mapView.height * 0.5).toFloat()),
//            PointF((mapView.width * 0.5).toFloat(), (mapView.height * 0.83).toFloat())
//        )
    }

    override fun onObjectRemoved(view: UserLocationView) {}

    override fun onObjectUpdated(view: UserLocationView, event: ObjectEvent) {
        userLocation = view.arrow.geometry
        if (userLocation != null) {
            submitRequest(userLocation!!)
        }
    }

    //Поиск не реализован
    override fun onSearchResponse(response: Response) {
//        val mapObjects: MapObjectCollection = mapView.map.mapObjects
//        for (searchResult in response.collection.children) {
//            val resultLocation = searchResult.obj!!.geometry[0].point!!
//            if (response != null) {
////                mapObjects.addPlacemark(resultLocation, ImageProvider.fromResource(this, R.drawable.my_location))
//            }
//        }
    }

    override fun onSearchError(error: Error) {
        var errorMessage = "Неизвестная Ошибка!"
        if (error is RemoteError) {
            errorMessage = "Беспроводная ошибка!"
        } else if (error is NetworkError) {
            errorMessage = "Проблема с интернетом"
        }
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    override fun onCameraPositionChanged(
        map: Map,
        cameraPosition: CameraPosition,
        cameraUpdateReason: CameraUpdateReason,
        finished: Boolean
    ) {
        if (finished && !isUserInteractingWithMap) {
            submitQuery(searchEditText.text.toString())
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> isUserInteractingWithMap = true
            MotionEvent.ACTION_UP -> isUserInteractingWithMap = false
        }
        return super.onTouchEvent(event)
    }

    override fun onDrivingRoutes(routes: MutableList<DrivingRoute>) {
        for (route in routes) {
            mapObjects!!.addPolyline(route.geometry)
        }
    }

    override fun onDrivingRoutesError(error: Error) {
        val errorMessage = "Неизвестная ошибка"
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    private fun submitRequest(startLocation: Point) {
        val drivingOptions = DrivingOptions()
        val vehicleOptions = VehicleOptions()
        val requestPoints: ArrayList<RequestPoint> = ArrayList()
        requestPoints.add(RequestPoint(startLocation, RequestPointType.WAYPOINT, null))
        requestPoints.add(RequestPoint(ROUTE_END_LOCATION, RequestPointType.WAYPOINT, null))
        drivingSession = drivingRouter!!.requestRoutes(requestPoints, drivingOptions, vehicleOptions, this)
    }
}


