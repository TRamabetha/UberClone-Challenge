package com.trajikworks.uberclonechallenge

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream
import java.io.IOException

class WorkerProfileActivity : AppCompatActivity() {

    private lateinit var mNameField: EditText
    private lateinit var mNumberField: EditText
    private lateinit var mCarInfoField: EditText

    private lateinit var mConfirmBtn: Button
    private lateinit var mBackBtn: Button

    private lateinit var mRadioGroup: RadioGroup

    private lateinit var mAuth: FirebaseAuth
    private lateinit var mWorkerDatabase: DatabaseReference

    private lateinit var userID: String
    private lateinit var mName: String
    private lateinit var mNumber: String
    private lateinit var mCar: String
    private lateinit var mService: String

    private lateinit var mProfileImage: ImageView

    private var mProfileImageUri: Uri? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_profile)

        mProfileImage = findViewById(R.id.wp_profileImage_iv)

        mNameField = findViewById(R.id.wp_name_et)
        mNumberField = findViewById(R.id.wp_number_et)
        mCarInfoField = findViewById(R.id.wp_car_et)

        mConfirmBtn = findViewById(R.id.wp_confirm_btn)
        mBackBtn = findViewById(R.id.wp_back_btn)

        mRadioGroup = findViewById(R.id.wp_radiogroup)

        mAuth = FirebaseAuth.getInstance()
        userID = mAuth.currentUser!!.uid
        mWorkerDatabase = FirebaseDatabase.getInstance().reference.child("Users")
            .child("Workers").child(userID)

        getUserInformation()

        mProfileImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 1)
        }

        mConfirmBtn.setOnClickListener { saveUserInformation() }

        mBackBtn.setOnClickListener {
            finish()
            return@setOnClickListener
        }
    }

    private fun getUserInformation() {
        mWorkerDatabase.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && snapshot.childrenCount > 0) {

                    val map: Map<String, Any> = snapshot.value as Map<String, Any>

                    if (map["name"] != null) {
                        mName = map["name"].toString()
                        mNameField.setText(mName)
                    }

                    if (map["number"] != null) {
                        mNumber = map["number"].toString()
                        mNumberField.setText(mNumber)
                    }

                    if (map["car"] != null) {
                        mCar = map["car"].toString()
                        mCarInfoField.setText(mCar)
                    }

                    if (map["service"] != null) {
                        mService = map["service"].toString()
                        when(mService){
                            "UberX" -> { mRadioGroup.check(R.id.wp_uberX_rb)}
                            "Uber Black" -> { mRadioGroup.check(R.id.wp_uberblack_rb)}
                            "UberXL" -> { mRadioGroup.check(R.id.wp_uberXL_rb)}
                        }
                    }

                    if (map["profileImageUrl"] != null) {

                        val mProfileImageUrl =
                            Firebase.storage.reference.child("profile_images").child(userID)
                        mProfileImageUrl.downloadUrl.addOnSuccessListener { Uri ->

                            val imageURL = Uri.toString()
                            Glide.with(application).load(imageURL).into(mProfileImage)

                        }

                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}

        })
    }

    private fun saveUserInformation() {
        mName = mNameField.text.toString()
        mNumber = mNumberField.text.toString()
        mCar = mCarInfoField.text.toString()

        val selectedID = mRadioGroup.checkedRadioButtonId

        val radioButton = findViewById<RadioButton>(selectedID)

        if (radioButton.text == null){
            return
        }

        mService = radioButton.text.toString()

        val userInfo: HashMap<String, Any> = HashMap()

        userInfo["name"] = mName
        userInfo["number"] = mNumber
        userInfo["car"] = mCar
        userInfo["service"] = mService

        mWorkerDatabase.updateChildren(userInfo)

        if (mProfileImageUri != null) {

            val filePath: StorageReference = FirebaseStorage.getInstance().reference
                .child("profile_images").child(userID)
            var bitmap: Bitmap? = null

            try {

                bitmap =
                    MediaStore.Images.Media.getBitmap(application.contentResolver, mProfileImageUri)

            } catch (e: IOException) { e.printStackTrace() }

            val baos = ByteArrayOutputStream()
            bitmap!!.compress(Bitmap.CompressFormat.JPEG, 20, baos)

            val data = baos.toByteArray()
            val uploadTask: UploadTask = filePath.putBytes(data)

            uploadTask.addOnFailureListener(OnFailureListener {
                finish()
                return@OnFailureListener
            })

            uploadTask.addOnSuccessListener(OnSuccessListener { taskSnapshot ->
                val downloadUrl: Task<Uri> = taskSnapshot!!.metadata!!.reference!!.downloadUrl

                val newImage: HashMap<String, Any> = HashMap()

                newImage["profileImageUrl"] = downloadUrl.toString()
                mWorkerDatabase.updateChildren(newImage)

                finish()
                return@OnSuccessListener
            })
        }
        else { finish() }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {

            val imageUri: Uri? = data!!.data
            if (imageUri != null) {
                mProfileImageUri = imageUri
                mProfileImage.setImageURI(mProfileImageUri)
            }

        }
    }

}