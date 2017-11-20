package com.example.edwar.kotlinuncover

import android.annotation.SuppressLint
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.graphics.Color
import android.support.v4.content.ContextCompat
import android.view.View
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.maps.model.*
import android.view.ViewGroup




class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var mCameraPosition: CameraPosition? = null
    private var mLocationPermissionGranted: Boolean = false

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private var mLastKnownLocation: Location? = null
    private val DEFAULT_ZOOM: Float = 18.0f

    // Keys for storing activity state.
    private val KEY_CAMERA_POSITION = "camera_position"
    private val KEY_LOCATION = "location"
    private val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1

    private var mLocationCallback: LocationCallback? = null
    private val mPastLocations: MutableList<Location> = mutableListOf<Location>()
    private var mLayoutReady: Boolean = false

    private var mStartCornerNorthWest: LatLng? = null
    private var mWidthOfTiles: Double = 0.0
    private var mHeightOfTiles: Double = 0.0
    private val mNumberAcross = 7
    private val mNumberDown = 12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        getLocationPermission()

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        startLocationUpdates()

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val viewGroup = (this
                .findViewById<View>(android.R.id.content) as ViewGroup).getChildAt(0) as ViewGroup

        viewGroup.viewTreeObserver.addOnGlobalLayoutListener {
            mLayoutReady = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState!!.putParcelable(KEY_CAMERA_POSITION, mMap.cameraPosition)
        outState.putParcelable(KEY_LOCATION, mLastKnownLocation)
        super.onSaveInstanceState(outState)
    }

    fun createLocationRequest() : LocationRequest {
        val locationRequest = LocationRequest()
        locationRequest.interval = 10000
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.smallestDisplacement = 5f
        return locationRequest
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if(!mLocationPermissionGranted)
            return

        mLocationCallback = object: LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult?.let {updateLocation(locationResult.lastLocation)}
            }
        }

        mFusedLocationProviderClient.requestLocationUpdates(
                createLocationRequest(),
                mLocationCallback,
                null /* Looper */)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        updateLocationUI()

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(
                        50.0,
                        -10.0),
                DEFAULT_ZOOM))
    }

    private fun getLocationPermission() {
        if (ContextCompat.checkSelfPermission(this.applicationContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        mLocationPermissionGranted = false
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true
                }
            }
        }
        updateLocationUI()
    }

    private fun updateLocationUI() {
        try {
            mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
            mMap.uiSettings.isMyLocationButtonEnabled = false

            if (mLocationPermissionGranted) {
                mMap.isMyLocationEnabled = true

            } else {
                mMap.isMyLocationEnabled = false
                mLastKnownLocation = null
                getLocationPermission()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message)
        }
    }

    private var mTiles: MutableCollection<Polygon> = mutableListOf()

    private fun updateLocation(lastLocation: Location) {
        mLastKnownLocation = lastLocation

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(
                        lastLocation.latitude,
                        lastLocation.longitude),
                DEFAULT_ZOOM),
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        DrawTiles()
                    }
                    override fun onCancel() {

                    }
                }
                )



        mPastLocations.add(lastLocation)

        mMap.addMarker(MarkerOptions().position(LatLng(lastLocation.latitude,lastLocation.longitude)))

        //val visibleRegion = mMap.projection.visibleRegion

     //   //Remove squares in view
     //   for(tile in mTiles) {
     //       val lb = LatLngBounds.Builder()
     //       for (p in tile.points)
     //           lb.include(p)
     //       val tileBound = lb.build()
//
     //       val locationsInView = getLocationsInView(visibleRegion)
     //       locationsInView
     //               .filter { tileBound.contains(LatLng(it.latitude, it.longitude)) }
     //               .forEach { tile.remove() }
     //   }
//

    }

    private fun DrawTiles() {
        val visibleRegion = mMap.projection.visibleRegion

        // Remove squares outside view
        var inlist = mTiles.filter { tile -> tile.points.all { !visibleRegion.latLngBounds.contains(it) } }
        for (t in inlist){
            t.remove()
            mTiles.remove(t)
        }
        //
        // Add Squares


        val drawArea = visibleRegion.latLngBounds

        //todo can only use once
        if(mWidthOfTiles == 0.0) {
            val visibleWidth = drawArea.northeast.longitude - drawArea.southwest.longitude
            mWidthOfTiles = Math.abs(visibleWidth / mNumberAcross)
            val visibleHeight = drawArea.northeast.latitude - drawArea.southwest.latitude
            mHeightOfTiles = Math.abs(visibleHeight / mNumberDown)

            if (mStartCornerNorthWest == null)
                mStartCornerNorthWest = LatLng(drawArea.northeast.latitude, drawArea.southwest.longitude)

        }
        val diffDown = drawArea.northeast.latitude - mStartCornerNorthWest!!.latitude
        val topLeftLat = ceilingToMult(diffDown, mHeightOfTiles) + mStartCornerNorthWest!!.latitude
        val bottomLeftLat = floorToMult(diffDown - (mNumberDown * mHeightOfTiles), mHeightOfTiles) + mStartCornerNorthWest!!.latitude

        var diffAcross = drawArea.southwest.longitude - mStartCornerNorthWest!!.longitude
        var topLeftLong = floorToMult(diffAcross, mWidthOfTiles) + mStartCornerNorthWest!!.longitude
        var topRightLong = ceilingToMult(diffAcross + (mNumberAcross * mWidthOfTiles), mWidthOfTiles) + mStartCornerNorthWest!!.longitude
        val lb = LatLngBounds.Builder()
        lb.include(LatLng(topLeftLat, topLeftLong))
        lb.include(LatLng(bottomLeftLat, topRightLong))

     //   mMap.addPolyline(PolylineOptions()
     //           .add(LatLng(topLeftLat, topLeftLong))
      //          .add(LatLng(bottomLeftLat, topLeftLong))
      //          .add(LatLng(bottomLeftLat, topRightLong))
      //          .add(LatLng(topLeftLat, topRightLong))
      //          .add(LatLng(topLeftLat, topLeftLong)))

        addTilesToMap(lb.build(), mWidthOfTiles, mHeightOfTiles, getLocationsInView(visibleRegion))
    }

    private fun floorToMult(number: Double, multiple: Double): Double
    {
        return Math.floor(number/multiple) * multiple
    }
    private fun ceilingToMult(number: Double, multiple: Double): Double
    {
        return Math.ceil(number/multiple) * multiple
    }

    private fun drawInitialGrid()
    {

        //addTilesToMap(drawArea, mWidthOfTiles, mHeightOfTiles, locationsInRange)
    }

    private fun addTilesToMap(
            drawArea: LatLngBounds,
            widthOfTiles: Double,
            heightOfTiles: Double,
            locationsInRange: List<Location>) {

        //val tiles: MutableList<PolygonOptions> = mutableListOf()


     //   var list2: MutableList<String> = mutableListOf()
     //   var list1 : MutableList<String> = mutableListOf()

        for (long in drawArea.southwest.longitude..drawArea.northeast.longitude step widthOfTiles) {
       //     var l= mutableListOf<String>()
       //     var l2= mutableListOf<String>()
            for (lat in drawArea.southwest.latitude..drawArea.northeast.latitude step heightOfTiles) {

                val tileSquare = mTiles.firstOrNull{tile ->
                    val lb = LatLngBounds.Builder()
                    for (p in tile.points)
                        lb.include(p)
                    val tileBound = lb.build()

                    tileBound.contains(LatLng(lat+(heightOfTiles/2), long+ (widthOfTiles/2)))}

                val hasPointInSquare = HasPointsInSquare(locationsInRange, long, widthOfTiles, lat, heightOfTiles)
         //       l.add("${if(hasPointInSquare){"P"}else{"*"}}")

                if(tileSquare != null && hasPointInSquare) {
                    tileSquare.remove()
                    mTiles.remove(tileSquare)
          //          l2.add("R")
                }
                else if(tileSquare != null && !hasPointInSquare) {
           //         l2.add("t")
                    continue
                }
                else if(tileSquare == null && !hasPointInSquare) {
                    AddTile(lat, long, widthOfTiles, heightOfTiles)
             //       l2.add("T")
                }
                else if(tileSquare == null && hasPointInSquare) {
               //     l2.add("b")
                    continue
                }

            }
            //list1.add( l.joinToString(" "))
            //list2.add( l2.joinToString(" "))
        }

        //for(l in list1)
        //    Log.i("____",l)
        //for(l in list2)
        //  Log.i("____",l)

        //Log.i("____","End of view")
    }

    private fun AddTile(lat: Double, long: Double, widthOfTiles: Double, heightOfTiles: Double) {
        val smallOutlay = +0.000000
        val tile = PolygonOptions()
        tile.add(LatLng(lat + smallOutlay, long + smallOutlay))
                .add(LatLng(lat + smallOutlay, long + widthOfTiles - smallOutlay))
                .add(LatLng(lat + heightOfTiles - smallOutlay, long + widthOfTiles - smallOutlay))
                .add(LatLng(lat + heightOfTiles - smallOutlay, long + smallOutlay))
                .add(LatLng(lat + smallOutlay, long + smallOutlay))
                .fillColor(Color.argb(225, 200, 200, 200))
                .strokeWidth(1f)

        mTiles.add(mMap.addPolygon(tile))
    }

    private fun HasPointsInSquare(locationsInRange: List<Location>, long: Double, widthOfTiles: Double, lat: Double, heightOfTiles: Double): Boolean {
        return locationsInRange.any { l: Location ->
            l.longitude < long + widthOfTiles &&
                    l.longitude > long &&
                    l.latitude < lat + heightOfTiles &&
                    l.latitude > lat
        }
    }

    private fun getLocationsInView(visibleRegion: VisibleRegion): List<Location> {
        return mPastLocations.filter { l ->
            l.latitude < visibleRegion.farRight.latitude &&
                    l.latitude > visibleRegion.nearRight.latitude &&
                    l.longitude < visibleRegion.farRight.longitude &&
                    l.longitude > visibleRegion.farLeft.longitude
        }
    }

    infix fun ClosedRange<Double>.step(step: Double): Iterable<Double> {
        require(start.isFinite())
        require(endInclusive.isFinite())
        require(step > 0.0) { "Step must be positive, was: $step." }
        val sequence = generateSequence(start) { previous ->
            if (previous == Double.POSITIVE_INFINITY) return@generateSequence null
            val next = previous + step
            if (next > endInclusive) null else next
        }
        return sequence.asIterable()
    }
}
