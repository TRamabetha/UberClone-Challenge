package com.trajikworks.uberclonechallenge

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class WorkerLoginActivity : AppCompatActivity() {

    private lateinit var mEmail : EditText
    private lateinit var mPassword : EditText
    private lateinit var mLogin : Button
    private lateinit var mRegistration : Button

    private lateinit var mAuth : FirebaseAuth
    private lateinit var firebaseAuthListener : AuthStateListener
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_login)

        mAuth = FirebaseAuth.getInstance()

        firebaseAuthListener = object : AuthStateListener {
            override fun onAuthStateChanged(p0: FirebaseAuth) {
                val user : FirebaseUser? = FirebaseAuth.getInstance().currentUser

                if (user != null) {
                    val intent = Intent(this@WorkerLoginActivity, MapsActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        }

        mEmail = findViewById(R.id.email)
        mPassword = findViewById(R.id.password)

        mLogin = findViewById(R.id.login)
        mRegistration = findViewById(R.id.registration)

        mRegistration.setOnClickListener(object : View.OnClickListener {

            override fun onClick(v: View?) {
                registr()
            }
        })

        mLogin.setOnClickListener(object : View.OnClickListener {

            override fun onClick(v: View?) {
               login()
            }
        })

    }

    private fun registr(){

        val email = mEmail.text.toString()
        val password = mPassword.text.toString()
        database = FirebaseDatabase.getInstance().getReference("Users")

        mAuth.createUserWithEmailAndPassword(
            email,
            password
        ).addOnCompleteListener(
            this@WorkerLoginActivity
        ) { task ->
            if(!task.isSuccessful){
                Toast.makeText(this@WorkerLoginActivity,"sign up error",Toast.LENGTH_SHORT).show()
            }else{
                val user_id = mAuth.currentUser!!.uid
                database.child("Workers").child(user_id).child("email").setValue(email)
            }

        }
    }

    private fun login(){
        val email = mEmail.text.toString()
        val password = mPassword.text.toString()

        mAuth.signInWithEmailAndPassword(
            email,
            password
        ).addOnCompleteListener(
            this@WorkerLoginActivity
        ) { task ->
            if(!task.isSuccessful){
                Toast.makeText(this@WorkerLoginActivity,"sign in error",Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mAuth.addAuthStateListener(firebaseAuthListener)
    }

    override fun onStop() {
        super.onStop()
        mAuth.removeAuthStateListener(firebaseAuthListener)
    }
}