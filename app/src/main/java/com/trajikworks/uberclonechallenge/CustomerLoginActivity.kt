package com.trajikworks.uberclonechallenge

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class CustomerLoginActivity : AppCompatActivity() {
    private lateinit var mEmail : EditText
    private lateinit var mPassword : EditText
    private lateinit var mLogin : Button
    private lateinit var mRegistration : Button

    private lateinit var mAuth : FirebaseAuth
    private lateinit var firebaseAuthListener : FirebaseAuth.AuthStateListener
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_login)

        mAuth = FirebaseAuth.getInstance()

        firebaseAuthListener = FirebaseAuth.AuthStateListener {
            val user : FirebaseUser? = FirebaseAuth.getInstance().currentUser

            if (user != null) {
                val intent = Intent(this@CustomerLoginActivity, CustomerMapActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

        mEmail = findViewById(R.id.email)
        mPassword = findViewById(R.id.password)

        mLogin = findViewById(R.id.login)
        mRegistration = findViewById(R.id.registration)

        mRegistration.setOnClickListener { register() }

        mLogin.setOnClickListener { login() }

    }

    private fun register() {

        val email = mEmail.text.toString()
        val password = mPassword.text.toString()
        database = FirebaseDatabase.getInstance().getReference("Users")

        mAuth.createUserWithEmailAndPassword(
            email,
            password
        ).addOnCompleteListener(
            this@CustomerLoginActivity
        ) { task ->
            if(!task.isSuccessful){
                Toast.makeText(this@CustomerLoginActivity,"sign up error",Toast.LENGTH_SHORT).show()
            }else{
                val user_id = mAuth.currentUser!!.uid
                database.child("Customers").child(user_id).setValue(true)
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
            this@CustomerLoginActivity
        ) { task ->
            if(!task.isSuccessful){
                Toast.makeText(this@CustomerLoginActivity,"sign in error", Toast.LENGTH_SHORT).show()
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