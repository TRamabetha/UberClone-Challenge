package com.trajikworks.uberclonechallenge

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import historyRecyclerView.HistoryAdapter
import historyRecyclerView.HistoryObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class HistoryActivity : AppCompatActivity() {

    private lateinit var customerOrWorker: String
    private lateinit var userID: String

    private lateinit var mHistoryRV: RecyclerView
    private lateinit var mHistoryAdapter: HistoryAdapter
    private lateinit var mHistoryLayoutManager: RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        mHistoryRV = findViewById(R.id.h_history_rv)

        mHistoryRV.isNestedScrollingEnabled = false
        mHistoryRV.setHasFixedSize(true)
        mHistoryLayoutManager = LinearLayoutManager(this@HistoryActivity)
        mHistoryRV.layoutManager = mHistoryLayoutManager
        mHistoryAdapter = HistoryAdapter(getDataSetHistory(),this@HistoryActivity)
        mHistoryRV.adapter = mHistoryAdapter

        customerOrWorker = intent.getStringExtra("customerOrWorker").toString()
        userID = FirebaseAuth.getInstance().currentUser!!.uid
        getUserHistoryID()


    }

    private fun getUserHistoryID() {
        val userHistoryDatabaseRef : DatabaseReference = FirebaseDatabase.getInstance().reference.child("Users")
            .child(customerOrWorker).child(userID).child("history")

        userHistoryDatabaseRef.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {

                if (snapshot.exists()){
                    for (history in snapshot.children) {
                        fetchRideInformation(history.key)
                    }

                }

            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchRideInformation(rideKey: String?) {
        val historyDatabaseRef : DatabaseReference = FirebaseDatabase.getInstance().reference.child("history")
                                    .child(rideKey!!)

        historyDatabaseRef.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {

                if (snapshot.exists()){
                    val rideId = snapshot.key
                    var timestamp = 0L
                    for(child in snapshot.children){
                        if(child.key!! == "timestamp"){
                            timestamp = java.lang.Long.valueOf(child.value.toString())
                        }
                    }
                    val obj = HistoryObject(rideId.toString(),getDate(timestamp))
                    resultHistoryList.add(obj)
                    mHistoryAdapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun getDate(timestamp: Long): String {
        val cal = Calendar.getInstance(Locale.getDefault())
        cal.timeInMillis = timestamp * 1000

        var formatter = SimpleDateFormat("dd-MM-yyyy hh:mm")

        return formatter.format(cal.timeInMillis)
    }

    private var resultHistoryList: ArrayList<HistoryObject> = ArrayList()

    private fun getDataSetHistory(): List<HistoryObject> {

        return resultHistoryList
    }

}