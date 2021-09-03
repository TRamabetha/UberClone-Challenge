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
import com.firebase.geofire.*
import com.google.android.gms.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.util.*
import kotlin.collections.HashMap


class CustomerMapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private lateinit var mLogout: Button
    private lateinit var mRequest: Button
    private lateinit var mProfile: Button
    private lateinit var mHistory: Button

    private lateinit var mWorkerInfo: LinearLayout

    private lateinit var mWorkerProfileImage: ImageView

    private lateinit var mWorkerName: TextView
    private lateinit var mWorkerCar: TextView
    private lateinit var mWorkerNumber: TextView

    private lateinit var mRadioGroup: RadioGroup

    private lateinit var mMap: GoogleMap
    private lateinit var mGoogleApiClient: GoogleApiClient
    private lateinit var mLastLocation: Location
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var pickUpLocation: LatLng
    private  var pickUpMarker: Marker? = null
    private lateinit var mapFragment: SupportMapFragment
    private var destination: String? = ""

    private lateinit var destinationLatLng: LatLng

    private lateinit var requestedService: String
    private var requested: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_map)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = supportFragmentManager
            .findFragmentById(R.id.cmmap) as SupportMapFragment

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationRequestCode)
        }else{mapFragment.getMapAsync(this)}

        destinationLatLng = LatLng(0.0,0.0)

        mLogout = findViewById(R.id.cmlogoutbtn)
        mRequest = findViewById(R.id.requestbtn)
        mProfile = findViewById(R.id.cmprofilebtn)
        mHistory = findViewById(R.id.cmhistorybtn)

        mWorkerInfo = findViewById(R.id.cm_worker_info)
        mWorkerProfileImage = findViewById(R.id.cm_worker_profileImage)
        mWorkerName = findViewById(R.id.cm_worker_name)
        mWorkerNumber = findViewById(R.id.cm_worker_number)
        mWorkerCar = findViewById(R.id.cm_worker_car)

        mRadioGroup = findViewById(R.id.cm_radiogroup)
        mRadioGroup.check(R.id.cm_uberX_rb)

        Places.initialize(applicationContext, "AIzaSyCZtz42zPqpzj8zbRzUblwKCr-1KmxkQtg")
        val placesClient = Places.createClient(this)

        // Initialize the AutocompleteSupportFragment.
        val autocompleteFragment =
            supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                    as AutocompleteSupportFragment

        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME,Place.Field.LAT_LNG))

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {

            override fun onPlaceSelected(place: Place) {

                if(place.name != null){destination= place.name!!}

                if(place.latLng != null){destinationLatLng = place.latLng!!}

                autocompleteFragment.setText(place.address)
                Toast.makeText(applicationContext, "Place: ${place.name}, ${place.id}", Toast.LENGTH_SHORT).show()
            }

            override fun onError(status: Status) {
                Toast.makeText(applicationContext, "An error occurred: $status", Toast.LENGTH_SHORT).show()
            }
        })


        mProfile.setOnClickListener {
            val intent = Intent(this@CustomerMapActivity, CustomerProfileActivity::class.java)
            startActivity(intent)
        }

        mRequest.setOnClickListener{

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

            }else{
                if(requested){ endWork() }
                else { requestWorker() } }

        }

        mHistory.setOnClickListener {
            val intent = Intent(this@CustomerMapActivity, HistoryActivity::class.java)
            intent.putExtra("customerOrWorker","Customers")
            startActivity(intent)
        }

        mLogout.setOnClickListener{
            FirebaseAuth.getInstance().signOut()

            val intent = Intent(this@CustomerMapActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun endWork() {

        requested = false
        workerFound = false
        radius = 1.0

        geoQuery.removeAllListeners()
        workerLocationRef?.removeEventListener(workerLocationRefListener)
        workEndedRef?.removeEventListener(workEndedListenerRef)

        if (workerFoundID != null || workerFoundID != ""){ removeWorkerFoundID() }

        removeCustomerRequest()

        if(pickUpMarker != null){

            removeMarkerFromMap(pickUpMarker)

            try{ removeMarkerFromMap(mWorkerMarker) }catch (ignored: NullPointerException){ }

            mRequest.text = "Request Worker"

        }
    }

    private fun removeCustomerRequest() {

        mWorkerName.text = ""
        mWorkerNumber.text = ""
        mWorkerProfileImage.setImageResource(R.drawable.ic_baseline_person_24)
        mWorkerInfo.visibility = View.GONE
        mWorkerCar.text = ""

        val userId = FirebaseAuth.getInstance().currentUser!!.uid

        val ref: DatabaseReference = FirebaseDatabase.getInstance().getReference("customerRequests")
        val geoFire = GeoFire(ref)
        geoFire.removeLocation(userId)


    }

    private fun removeWorkerFoundID() {
        if (workerFoundID!= null){

            val workerRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("Users")
                .child("Workers").child(workerFoundID!!).child("customerRequest")

            workerRef.removeValue()
            workerFoundID = null

        }
    }

    @SuppressLint("SetTextI18n")
    private fun requestWorker() {
        requested = true

        val selectedID = mRadioGroup.checkedRadioButtonId

        val radioButton = findViewById<RadioButton>(selectedID)

        if (radioButton.text == null){ return }

        requestedService = radioButton.text.toString()

        val userId = FirebaseAuth.getInstance().currentUser!!.uid

        val ref: DatabaseReference = FirebaseDatabase.getInstance().getReference("customerRequests")
        val geoFire = GeoFire(ref)
        geoFire.setLocation(userId, GeoLocation(mLastLocation.latitude,mLastLocation.longitude))

        pickUpLocation = LatLng(mLastLocation.latitude,mLastLocation.longitude)

        pickUpMarker = addMarkerToMap(pickUpLocation,R.mipmap.pin_marker_foreground,"Location Here")

        mRequest.text = "Looking For Worker"

        getClosestWorker()
    }

    private var radius: Double = 1.0
    private var workerFound: Boolean = false
    private var workerFoundID: String? = null
    private lateinit var geoQuery: GeoQuery

    private fun getClosestWorker(){
        val workerLocation: DatabaseReference = FirebaseDatabase.getInstance().reference.child("workersAvailable")

        val geoFire = GeoFire(workerLocation)

        geoQuery = geoFire.queryAtLocation(GeoLocation(pickUpLocation.latitude,pickUpLocation.longitude),radius)

        geoQuery.removeAllListeners()

        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener{

            @SuppressLint("SetTextI18n")
            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                if (!workerFound && requested){

                    val mCustomerRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("Users")
                        .child("Workers").child(key!!)

                    mCustomerRef.addListenerForSingleValueEvent(object: ValueEventListener{
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists() && snapshot.childrenCount > 0){
                                val workerMap: Map<String, Any> = snapshot.value as Map<String, Any>

                                if (workerFound){ return }

                                if (workerMap["service"]!! == requestedService){
                                    workerFound = true
                                    workerFoundID= snapshot.key

                                    addCustomerToWorker()
                                    getWorkerLocation()
                                    getWorkerInfo()
                                    getHasRideEnded()

                                    mRequest.text = "Looking For Worker Location......."
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }

            private fun addCustomerToWorker() {
                val workerRef: DatabaseReference = FirebaseDatabase.getInstance().reference.child("Users")
                    .child("Workers").child(workerFoundID!!).child("customerRequest")

                val customerID = FirebaseAuth.getInstance().currentUser!!.uid

                val map = HashMap<String, Any>()

                map["customerJobId"] = customerID

                if (destination != null) {
                    map["destination"] = destination!!
                    map["destinationLat"] = destinationLatLng.latitude
                    map["destinationLng"] = destinationLatLng.longitude
                }

                workerRef.updateChildren(map)
            }

            override fun onGeoQueryReady() {
                if (!workerFound){
                    radius++
                    getClosestWorker()
                }
            }

            override fun onKeyExited(key: String?) {}
            override fun onKeyMoved(key: String?, location: GeoLocation?) {}
            override fun onGeoQueryError(error: DatabaseError?) {}

        })
    }

    private fun getWorkerInfo(){

        val mWorkerDatabase = FirebaseDatabase.getInstance().reference.child("Users")
            .child("Workers").child(workerFoundID!!)

        mWorkerDatabase.addListenerForSingleValueEvent(object: ValueEventListener{

            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.childrenCount >0){

                    mWorkerInfo.visibility = View.VISIBLE
                    val map: Map<String, Any> = snapshot.value as Map<String, Any>

                    if (map["name"] != null){ mWorkerName.text = map["name"].toString() }

                    if (map["car"] != null){ mWorkerCar.text = map["car"].toString() }

                    if (map["number"] != null){ mWorkerNumber.text = map["number"].toString() }

                    if (map["profileImageUrl"] != null){

                        val mProfileImageUrl= Firebase.storage.reference.child("profile_images")
                            .child(workerFoundID!!)

                        mProfileImageUrl.downloadUrl.addOnSuccessListener {Uri->

                            val imageURL = Uri.toString()

                            Glide.with(application)
                                 .load(imageURL)
                                 .into(mWorkerProfileImage)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private var workEndedRef: DatabaseReference? = null
    private lateinit var workEndedListenerRef: ValueEventListener

    private fun getHasRideEnded() {

        workEndedRef = FirebaseDatabase.getInstance().reference
            .child("Users").child("Workers").child(workerFoundID!!)
            .child("customerRequest").child("customerJobId")

        workEndedListenerRef = workEndedRef!!.addValueEventListener(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()){

                }
                else{ endWork() }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private var mWorkerMarker: Marker? = null
    private var workerLocationRef: DatabaseReference? = null
    private lateinit var workerLocationRefListener: ValueEventListener

    private fun getWorkerLocation() {
        workerLocationRef = workerFoundID?.let {
            FirebaseDatabase.getInstance().reference.child("workersWorking")
                .child(it).child("l")
        }

        workerLocationRefListener = workerLocationRef!!.addValueEventListener(object: ValueEventListener{

            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {

                if (snapshot.exists() && requested){ setWorkerLocation(snapshot) }

            }

            @SuppressLint("SetTextI18n")
            private fun setWorkerLocation(snapshot: DataSnapshot) {
                val map: List<Any> = snapshot.value as List<Any>

                mRequest.text = "Worker Found"

                val locationLat = map[0].toString().toDouble()
                val locationLng = map[1].toString().toDouble()

                val  workerLatLng = LatLng(locationLat,locationLng)

                if (mWorkerMarker != null){ removeMarkerFromMap(mWorkerMarker) }

                if (getDistance(workerLatLng ) < 100){ mRequest.text = "Worker is Here" }
                else { mRequest.text = "Worker Found:${getDistance(workerLatLng)}" }

                mWorkerMarker = addMarkerToMap(workerLatLng,R.mipmap.worker_marker_foreground,"Your Worker")

            }

            private fun getDistance(toLatLng: LatLng): Float {

                val loc1 = Location("")
                loc1.latitude = pickUpLocation.latitude
                loc1.longitude = pickUpLocation.longitude

                val loc2 = Location("")
                loc2.latitude = toLatLng.latitude
                loc2.longitude = toLatLng.longitude

                return loc1.distanceTo(loc2)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun addMarkerToMap(pos:LatLng, resourceID : Int, title: String?): Marker? {

        return  mMap.addMarker(MarkerOptions().position(pos).title(title).icon(
                BitmapDescriptorFactory.fromResource(resourceID)))
    }

    private fun removeMarkerFromMap(marker: Marker?){ marker!!.remove() }

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

    @Synchronized
    private fun buildGoogleApiClient() {
        mGoogleApiClient = GoogleApiClient.Builder(this).
        addConnectionCallbacks(this).
        addOnConnectionFailedListener(this).
        addApi(LocationServices.API).build()
        mGoogleApiClient.connect()
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

    override fun onLocationChanged(location: Location) {
        mLastLocation = location

        val latLng = LatLng(location.latitude,location.longitude)

        with(mMap) {
            moveCamera(CameraUpdateFactory.newLatLng(latLng))
            animateCamera(CameraUpdateFactory.zoomTo(11F))
        }

    }

    override fun onConnectionSuspended(p0: Int) {}
    override fun onConnectionFailed(p0: ConnectionResult) {}

    private val locationRequestCode = 1
    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, grantResults: IntArray) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            locationRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mapFragment.getMapAsync(this)
                }
                else{
                    Toast.makeText(applicationContext,"Please provide permission", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}