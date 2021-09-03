package com.trajikworks.uberclonechallenge

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.directions.route.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.text.SimpleDateFormat
import java.util.*

class HistoryPageActivity : AppCompatActivity(), OnMapReadyCallback, RoutingListener {

    private lateinit var mMap:GoogleMap
    private lateinit var mMapFragment: SupportMapFragment
    private lateinit var rideId: String
    private lateinit var currentUserId: String
    private lateinit var customerId: String
    private lateinit var workerId: String
    private lateinit var userWorkerOrCustomer: String

    private lateinit var destinationLatLng: LatLng
    private lateinit var pickupLatLng: LatLng

    private lateinit var historyRideInfoRef: DatabaseReference

    private lateinit var locationRide: TextView
    private lateinit var distanceRide: TextView
    private lateinit var dateRide: TextView
    private lateinit var userName: TextView
    private lateinit var userNumber: TextView

    private lateinit var userImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_page)
        mMapFragment = supportFragmentManager.findFragmentById(R.id.hp_map) as SupportMapFragment
        rideId = intent.extras!!.getString("rideId").toString()
        mMapFragment.getMapAsync(this)

        locationRide = findViewById(R.id.hp_ride_loc)
        distanceRide = findViewById(R.id.hp_ride_distance)
        dateRide = findViewById(R.id.hp_ride_date)
        userName = findViewById(R.id.hp_user_name)
        userNumber = findViewById(R.id.hp_user_number)
        userImage = findViewById(R.id.hp_user_image)

        polylines = ArrayList()

        currentUserId = FirebaseAuth.getInstance().currentUser?.uid.toString()

        historyRideInfoRef = FirebaseDatabase.getInstance().reference.child("history")
                                .child(rideId)

        getRideInfo()
    }

    private fun getRideInfo() {
        historyRideInfoRef.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()){
                    for(child in snapshot.children){
                        if (child.key.equals("customer")){
                            customerId = child.value.toString()
                            if (currentUserId != customerId){
                                userWorkerOrCustomer = "Drivers"
                                getUserInfo("Customers",customerId)
                            }
                        }
                        if (child.key == ("worker")){
                            workerId = child.value.toString()
                            if (currentUserId != workerId){
                                userWorkerOrCustomer = "Customers"
                                getUserInfo("Workers",workerId)
                            }
                        }
                        if (child.key == ("timestamp")){
                            dateRide.text = getDate(java.lang.Long.valueOf(child.value.toString()))

                        }
                        if (child.key == ("destination")){
                            locationRide.text = child.value.toString()

                        }
                        if (child.key == ("location")){
                            pickupLatLng = LatLng((child.child("from").child("lat").value.toString()).toDouble(),
                                            (child.child("from").child("lng").value.toString()).toDouble())

                            destinationLatLng = LatLng((child.child("to").child("lat").value.toString()).toDouble(),
                                                (child.child("to").child("lng").value.toString()).toDouble())

                            if (destinationLatLng != LatLng(0.0,0.0)){ getRouteToMarker(pickupLatLng) }
                        }
                    }

                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun getUserInfo(otherUserWorkerOrCustomer: String, userId: String) {
       val mOtherUserRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("Users")
            .child(otherUserWorkerOrCustomer).child(userId)

        mOtherUserRef.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()){
                    val map: Map<String, Any> = snapshot.value as Map<String, Any>

                    if(map["name"] != null){ userName.text = map["name"].toString() }

                    if(map["number"] != null){ userNumber.text = map["number"].toString() }

                    if (map["profileImageUrl"] != null){

                        val mProfileImageUrl= Firebase.storage.reference.child("profile_images")
                            .child(userId)

                        mProfileImageUrl.downloadUrl.addOnSuccessListener {Uri->

                            val imageURL = Uri.toString()

                            Glide.with(application)
                                .load(imageURL)
                                .into(userImage)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    @SuppressLint("SimpleDateFormat")
    private fun getDate(timestamp: Long): String {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.timeInMillis = timestamp * 1000

        val formatter = SimpleDateFormat("dd-MM-yyyy hh:mm")

        return formatter.format(cal.timeInMillis)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
    }

    override fun onRoutingFailure(p0: RouteException?) {
        if(p0 != null) {
            Toast.makeText(this, "Error: " + p0.message, Toast.LENGTH_LONG).show()
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRoutingStart() {}

    private var polylines: List<Polyline>? = null
    private val COLORS = intArrayOf(R.color.primary_dark_material_light)

    override fun onRoutingSuccess(route: ArrayList<Route>?, shortestRouteIndex: Int) {

        val builder: LatLngBounds.Builder = LatLngBounds.Builder()

        builder.include(pickupLatLng)
        builder.include(destinationLatLng)

        val bounds :LatLngBounds = builder.build()

        val width = resources.displayMetrics.widthPixels
        val padding = (width*0.2).toInt()

        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)

        mMap.animateCamera(cameraUpdate)

        mMap.addMarker(MarkerOptions().position(pickupLatLng).title("pickup location").icon(
        BitmapDescriptorFactory.fromResource(R.mipmap.pin_marker_foreground)))

        mMap.addMarker(MarkerOptions().position(destinationLatLng).title("pickup location"))

        if (polylines!!.isNotEmpty()) {
            for (poly in polylines!!) { poly.remove() }
        }

        polylines = ArrayList()

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

    override fun onRoutingCancelled() {}

    private fun erasePolyLines(){

        for (line in polylines!!) { line.remove() }
        (polylines as ArrayList<Polyline>).clear()

    }

    private fun getRouteToMarker(pickupLatLng: LatLng) {
        val routing = Routing.Builder()
            .travelMode(AbstractRouting.TravelMode.DRIVING)
            .key("AIzaSyCzfz6PW_rfqOrn3woTuYDCPY4u4PaPFjA" )
            .withListener(this)
            .alternativeRoutes(false)
            .waypoints(pickupLatLng,destinationLatLng)
            .build()
        routing.execute()
    }
}