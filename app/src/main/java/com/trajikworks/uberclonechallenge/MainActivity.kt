package com.trajikworks.uberclonechallenge

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button

class MainActivity : AppCompatActivity() {
    private lateinit var mWorker : Button
    private lateinit var mCustomer : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mWorker = findViewById(R.id.worker)
        mCustomer = findViewById(R.id.customer)

//        mWorker.setOnClickListener{
//           val intent = Intent(this,WorkerLoginActivity::class.java)
//            startActivity(intent)
//            finish()
//        }
        mWorker.setOnClickListener(object : View.OnClickListener {

            override fun onClick(v: View?) {
                val intent = Intent(this@MainActivity,WorkerLoginActivity::class.java)
                startActivity(intent)
                finish()
            }

        })

        mCustomer.setOnClickListener(object : View.OnClickListener {

            override fun onClick(v: View?) {
                val intent = Intent(this@MainActivity,CustomerLoginActivity::class.java)
                startActivity(intent)
                finish()
            }

        })
    }
}