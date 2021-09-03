package com.trajikworks.uberclonechallenge

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.directions.route.Route
import com.directions.route.RouteException
import com.directions.route.RoutingListener
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.util.*
import com.directions.route.AbstractRouting

import com.directions.route.Routing
import android.widget.Toast
import com.google.android.gms.maps.model.*

import kotlin.collections.HashMap


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener,
    RoutingListener{

    private lateinit var mLogout: Button
    private lateinit var mProfile: Button
    private lateinit var mRideStatus: Button
    private lateinit var mSetAvailability: Button

    private lateinit var mCustomerInfo: LinearLayout

    private lateinit var mCustomerProfileImage: ImageView

    private lateinit var mCustomerName: TextView
    private lateinit var mCustomerNumber: TextView
    private lateinit var mCustomerDestination: TextView

    private lateinit var mMap: GoogleMap
    private lateinit var mGoogleApiClient: GoogleApiClient
    private lateinit var mLastLocation: Location
    private lateinit var mLocationRequest: LocationRequest
    private var userWorkerID = FirebaseAuth.getInstance().currentUser?.uid
    private var workerMarker: Marker? = null
    private lateinit var mapFragment: SupportMapFragment

    private lateinit var destinationLatLng: LatLng

    private var status = 0
    private var destination: String? = null
    private var customerId: String = ""
    private var available: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        userWorkerID = FirebaseAuth.getInstance().currentUser?.uid
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
         mapFragment = supportFragmentManager
            .findFragmentById(R.id.wmmap) as SupportMapFragment

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationRequestCode)
        }else{mapFragment.getMapAsync(this)}

        mCustomerInfo = findViewById(R.id.wm_customer_info)
        mCustomerProfileImage = findViewById(R.id.wm_customer_profileImage)
        mCustomerName = findViewById(R.id.wm_customer_name)
        mCustomerNumber = findViewById(R.id.wm_customer_number)
        mCustomerDestination = findViewById(R.id.wm_customer_destination)

        mLogout = findViewById(R.id.wm_logout)
        mProfile = findViewById(R.id.wm_worker_profile)
        mSetAvailability = findViewById(R.id.setavailability)
        mRideStatus = findViewById(R.id.wm_ridestatus)

        mRideStatus.setOnClickListener {
            when(status){
                1 ->  { status = 2
                        erasePolyLines()
                        if (destinationLatLng.latitude != 0.0 && destinationLatLng.longitude != 0.0){
                            getRouteToMarker(destinationLatLng)
                        }
                    mRideStatus.text = "Ride completed"
                    return@setOnClickListener
                }
                2 ->  {
                        recordWork()
                        endWork()
                        return@setOnClickListener}
            }
        }

        polylines = ArrayList()

        mProfile.setOnClickListener {
            val intent = Intent(this@MapsActivity, WorkerProfileActivity::class.java)
            startActivity(intent)
        }

        mSetAvailability.setOnClickListener {
            if (!available){
                setWorkerConnect(mLastLocation)
                getAssignedCustomer()
            }
            else{ setWorkerDisconnect()
                  endWork()}
        }

        mLogout.setOnClickListener{
            setWorkerDisconnect()

            FirebaseAuth.getInstance().signOut()

            val intent = Intent(this@MapsActivity, MainActivity::class.java)
            startActivity(intent)
            finish()

            return@setOnClickListener
        }

    }

    @SuppressLint("SetTextI18n")
    private fun endWork(){
        mRideStatus.text = "PickUp Customer"
        erasePolyLines()

        val workerRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("Users")
            .child("Workers").child(userWorkerID!!).child("customerRequest")
        workerRef.removeValue()

        val ref: DatabaseReference = FirebaseDatabase.getInstance().getReference("customerRequests")
        val geoFire = GeoFire(ref)
        geoFire.removeLocation(customerId)

        if(pickUpMarker != null){ removeMarkerFromMap(pickUpMarker)}

        if(customerPickupLocationRefListener != null) {
            customerPickupLocationRef.removeEventListener(customerPickupLocationRefListener!!)
        }

        mCustomerName.text = ""
        mCustomerNumber.text = ""
        mCustomerProfileImage.setImageResource(R.drawable.ic_baseline_person_24)
        mCustomerInfo.visibility = View.GONE
        mCustomerDestination.text = "Destination: --"
        customerId = ""
    }

    private fun recordWork(){

        val workerRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("Users")
            .child("Workers").child(userWorkerID!!).child("history")

        val customerRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("Users")
            .child("Customers").child(customerId).child("history")

        val historyRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("history")

        val requestId:String = historyRef.push().key!!

        workerRef.child(requestId).setValue(true)
        customerRef.child(requestId).setValue(true)

        val map = HashMap<String,Any>()
        map["worker"] = userWorkerID!!
        map["customer"] = customerId
        map["rating"] = 0
        map["timestamp"] = getCurrentTimestamp()
        map["destination"] = destination!!
        map["location/from/lat"] = pickupLatLng!!.latitude
        map["location/from/lng"] = pickupLatLng!!.longitude
        map["location/to/lat"] = destinationLatLng.latitude
        map["location/to/lng"] = destinationLatLng.latitude

        historyRef.child(requestId).updateChildren(map)
    }

    private fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis()/1000
    }

    private var pickUpMarker: Marker? = null
    private lateinit var customerPickupLocationRef: DatabaseReference
    private var customerPickupLocationRefListener: ValueEventListener? = null

    private fun getAssignedCustomer() {

        val assignedCustomerRef: DatabaseReference = FirebaseDatabase.getInstance().reference
            .child("Users").child("Workers").child(userWorkerID!!)
            .child("customerRequest").child("customerJobId")

        assignedCustomerRef.addValueEventListener(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()){
                    status = 1
                    customerId = snapshot.value.toString()
                    getAssignedCustomerPickupLocation()
                    getAssignedCustomerInfo()
                    getAssignedCustomerDestination()
                }
                else{ endWork() }
            }

            override fun onCancelled(error: DatabaseError) {}

        })
    }

    private var pickupLatLng: LatLng? = null

    private fun getAssignedCustomerPickupLocation() {
        customerPickupLocationRef = FirebaseDatabase.getInstance().reference.child("customerRequests")
                                                    .child(customerId).child("l")

        customerPickupLocationRefListener = customerPickupLocationRef.addValueEventListener(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                if (snapshot.exists() && customerId != ""){

                    val map: List<Any> = snapshot.value as List<Any>

                    val locationLat = map[0].toString().toDouble()

                    val locationLng = map[1].toString().toDouble()

                    pickupLatLng= LatLng(locationLat,locationLng)

                    pickUpMarker  =  addMarkerToMap(pickupLatLng!!,R.mipmap.pin_marker_foreground,"Pick Up Marker")

                    getRouteToMarker(pickupLatLng!!)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun getRouteToMarker(pickupLatLng: LatLng) {
        val routing = Routing.Builder()
            .travelMode(AbstractRouting.TravelMode.DRIVING)
            .key("AIzaSyCzfz6PW_rfqOrn3woTuYDCPY4u4PaPFjA" )
            .withListener(this)
            .alternativeRoutes(false)
            .waypoints(LatLng(mLastLocation.latitude,mLastLocation.longitude), pickupLatLng)
            .build()
            routing.execute()
    }

    private fun getAssignedCustomerDestination() {

        val assignedCustomerRef: DatabaseReference = FirebaseDatabase.getInstance().reference
            .child("Users").child("Workers").child(userWorkerID!!)
            .child("customerRequest")

        assignedCustomerRef.addListenerForSingleValueEvent(object: ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val map: Map<String, Any> = snapshot.value as Map<String, Any>

                    if (map["destination"] != null){
                        destination = map["destination"].toString()
                        mCustomerDestination.text = "Destination$destination"

                    }
                    else{
                        mCustomerDestination.text = "Destination --"
                    }

                    var destinationLat = 0.0
                    var destinationLng = 0.0

                    if (map["destinationLat"] != null){
                        destinationLat =  map["destinationLat"].toString().toDouble()
                    }
                    if (map["destinationLng"] != null){
                         destinationLng =  map["destinationLng"].toString().toDouble()
                         destinationLatLng = LatLng(destinationLat,destinationLng)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun getAssignedCustomerInfo(){

        val mCustomerDatabase = FirebaseDatabase.getInstance().reference.child("Users")
            .child("Customers").child(customerId)

        mCustomerDatabase.addListenerForSingleValueEvent(object: ValueEventListener{

            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.childrenCount >0){

                    mCustomerInfo.visibility = View.VISIBLE
                    val map: Map<String, Any> = snapshot.value as Map<String, Any>

                    if (map["name"] != null){ mCustomerName.text = map["name"].toString() }

                    if (map["number"] != null){ mCustomerNumber.text = map["number"].toString() }

                    if (map["profileImageUrl"] != null){

                        val mProfileImageUrl= Firebase.storage.reference.child("profile_images")
                                              .child(customerId)

                        mProfileImageUrl.downloadUrl.addOnSuccessListener {Uri->

                            val imageURL = Uri.toString()
                            Glide.with(application).load(imageURL).into(mCustomerProfileImage)

                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun addMarkerToMap(pos:LatLng, resourceID : Int, title: String?): Marker? {

        return  mMap.addMarker(MarkerOptions().position(pos).title(title).icon(
                    BitmapDescriptorFactory.fromResource(resourceID)))
    }

    private fun removeMarkerFromMap(marker: Marker?){ marker!!.remove() }

    @Synchronized
    private fun buildGoogleApiClient() {
        mGoogleApiClient = GoogleApiClient.Builder(this).
        addConnectionCallbacks(this).
        addOnConnectionFailedListener(this).
        addApi(LocationServices.API).build()
        mGoogleApiClient.connect()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        )
        { return }

        buildGoogleApiClient()
        mMap.isMyLocationEnabled = true

    }

    override fun onConnected(p0: Bundle?) {
        mLocationRequest = LocationRequest()

        with(mLocationRequest){
            interval = 1000
            fastestInterval = 1000
            setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationRequestCode)
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,mLocationRequest,this)
    }

    override fun onLocationChanged(location: Location ) {

        if(applicationContext != null){

            mLastLocation = location

            val latLng = LatLng(location.latitude,location.longitude)

            with(mMap) {
                moveCamera(CameraUpdateFactory.newLatLng(latLng))
                animateCamera(CameraUpdateFactory.zoomTo(17F))
            }

            checkWorkerCondition(location)

        }
    }

    override fun onConnectionSuspended(p0: Int) {}
    override fun onConnectionFailed(p0: ConnectionResult) {}

    private fun checkWorkerCondition(location: Location ) {
        userWorkerID = FirebaseAuth.getInstance().currentUser?.uid

        val refAvailable: DatabaseReference = FirebaseDatabase.getInstance().getReference("workersAvailable")
        val refWorking: DatabaseReference = FirebaseDatabase.getInstance().getReference("workersWorking")

        val geoFireAvailable = GeoFire(refAvailable)
        val geoFireWorking = GeoFire(refWorking)

        when (customerId){

            "" -> { if(available) { swapWorkingCondition(geoFireAvailable,geoFireWorking,location) }
                        return }

            else -> { if(available) { swapWorkingCondition(geoFireWorking,geoFireAvailable,location) }
                        return }
        }
    }

    private fun swapWorkingCondition(to: GeoFire, from: GeoFire,at: Location) {
        from.removeLocation(userWorkerID)
        to.setLocation(userWorkerID,GeoLocation(at.latitude, at.longitude))
    }

    @SuppressLint("SetTextI18n")
    private fun setWorkerConnect(location: Location){

        userWorkerID = FirebaseAuth.getInstance().currentUser!!.uid
        val ref: DatabaseReference = FirebaseDatabase.getInstance().getReference("workersAvailable")

        val geoFire = GeoFire(ref)
        geoFire.setLocation(userWorkerID, GeoLocation(location.latitude,location.longitude))
        available = true

        workerMarker = addMarkerToMap(LatLng(location.latitude,location.longitude),
                        R.mipmap.worker_marker_foreground,"Worker Marker")

        mSetAvailability.text = "Change To Not Available"

    }

    @SuppressLint("SetTextI18n")
    private fun setWorkerDisconnect(){
        try {
            userWorkerID = FirebaseAuth.getInstance().currentUser?.uid
            val ref: DatabaseReference = FirebaseDatabase.getInstance().getReference("workersAvailable")

            val geoFire = GeoFire(ref)
            geoFire.removeLocation(userWorkerID)

            available = false

            removeMarkerFromMap(workerMarker)

            mSetAvailability.text = "Change To Available"
        }catch (ignored: NullPointerException){ }

    }

    private val locationRequestCode = 1
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)


        when(requestCode){
            locationRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mapFragment.getMapAsync(this)
                }else{
                    Toast.makeText(applicationContext,"Please provide permission", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        setWorkerDisconnect()
    }

    private var polylines: List<Polyline>? = null
    private val COLORS = intArrayOf(R.color.primary_dark_material_light)

    private fun mapBounds(loc1: LatLng, loc2: LatLng){
        val builder: LatLngBounds.Builder = LatLngBounds.Builder()

        builder.include(loc1)
        builder.include(loc2)

        val bounds : LatLngBounds = builder.build()

        val width = resources.displayMetrics.widthPixels
        val padding = (width*0.2).toInt()

        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)

        mMap.animateCamera(cameraUpdate)
    }

    override fun onRoutingSuccess(route: ArrayList<Route>?, shortestRouteIndex: Int) {


        if (polylines!!.isNotEmpty()) {
            for (poly in polylines!!) { poly.remove() }
        }

        polylines = ArrayList()
        //add route(s) to the map.
        //add route(s) to the map.
        for (i in 0 until route!!.size) {

            //In case of more than 5 alternative routes
            val colorIndex: Int = i % COLORS.size
            val polyOptions = PolylineOptions()
            polyOptions.color(resources.getColor(COLORS[colorIndex]))
            polyOptions.width((10 + i * 3).toFloat())
            polyOptions.addAll(route[i].points)
            val polyline: Polyline = mMap.addPolyline(polyOptions)
            (polylines as ArrayList<Polyline>).add(polyline)
            Toast.makeText(applicationContext,
                "Route " + (i + 1) + ": distance - " + route[i]
                    .distanceValue + ": duration - " + route[i].durationValue,
                Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRoutingFailure(p0: RouteException?) {
        if(p0 != null) {
            Toast.makeText(this, "Error: " + p0.message, Toast.LENGTH_LONG).show()
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRoutingStart() {}
    override fun onRoutingCancelled() {}

    private fun erasePolyLines(){

        for (line in polylines!!) { line.remove() }
        (polylines as ArrayList<Polyline>).clear()

    }
}