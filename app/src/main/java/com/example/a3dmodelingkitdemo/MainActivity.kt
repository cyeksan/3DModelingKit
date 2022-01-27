package com.example.a3dmodelingkitdemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.huawei.hms.materialgeneratesdk.MaterialGenApplication
import com.huawei.hms.materialgeneratesdk.Modeling3dTextureConstants
import com.huawei.hms.materialgeneratesdk.cloud.*
import java.io.File
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    private lateinit var engine: Modeling3dTextureEngine
    private lateinit var setting: Modeling3dTextureSetting
    private lateinit var takePhotoBtn: Button
    private lateinit var uploadBtn: Button
    private lateinit var queryBtn: Button
    private lateinit var downloadBtn: Button
    private lateinit var photoFile: File
    private var storageDirectory: File? = null
    private val permissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA
    )
    private var photoStoragePath: String? = null
    private var savePath: String? = null
    private var modeling3dTextureInitResult: Modeling3dTextureInitResult? = null
    private var taskId: String? = null
    private lateinit var taskUtils: Modeling3dTextureTaskUtils

    companion object {
        private const val TAG = "MainActivity"
        private const val FILE_NAME = "file"
        private const val CAMERA_REQUEST_CODE = 42
        private const val REQUEST_PERMISSION = 1
        private const val FILE_PROVIDER_AUTHORITY = "com.example.a3dmodelingkitdemo.fileprovider"
        fun getMaterialDownFile(): String {
            return "/3dModeling/material/download/"
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        MaterialGenApplication.getInstance().apiKey =
            Constants.API_KEY
        initializeEngine()

        storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        photoStoragePath = storageDirectory.toString() + "/"
        savePath = if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            this.getExternalFilesDir(null).toString() + getMaterialDownFile()
        } else {
            this.filesDir.path + getMaterialDownFile()
        }

        takePhotoBtn = findViewById(R.id.take_photo_btn)
        uploadBtn = findViewById(R.id.upload_btn)
        queryBtn = findViewById(R.id.query_btn)
        downloadBtn = findViewById(R.id.download_btn)

        takePhotoBtn.setOnClickListener {
            checkPermissions()
        }

        uploadBtn.setOnClickListener {
            uploadImage()
        }

        queryBtn.setOnClickListener {
            queryTask()
        }

        downloadBtn.setOnClickListener {
            downloadImage()
        }
    }

    private fun initializeEngine() {

        // Create a material generation engine and pass the current context.
        engine = Modeling3dTextureEngine.getInstance(applicationContext)
        taskUtils = Modeling3dTextureTaskUtils.getInstance(applicationContext)

        // Create a material generation configurator.
        setting = Modeling3dTextureSetting.Factory() // Set the working mode to AI.
            .setTextureMode(Modeling3dTextureConstants.AlgorithmMode.AI)
            .create()
    }

    private fun checkPermissions() {
        // Check if we have required permissions
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // We don't have permissions so prompt the user
            ActivityCompat.requestPermissions(
                this,
                permissions,
                REQUEST_PERMISSION
            )
        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        photoFile = File.createTempFile(FILE_NAME, ".jpg", storageDirectory)
        val fileProvider = FileProvider.getUriForFile(
            this,
            FILE_PROVIDER_AUTHORITY,
            photoFile
        )
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider)
        if (takePictureIntent.resolveActivity(this.packageManager) != null) {
            startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
        } else {
            Toast.makeText(this, "Unable to open camera", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Toast.makeText(
                this,
                "The picture has been saved to path: ${photoFile.path}",
                Toast.LENGTH_LONG
            ).show()
        } else {
            super.onActivityResult(requestCode, resultCode, data)

            Toast.makeText(
                this,
                "Photo could not be taken!",
                Toast.LENGTH_LONG
            ).show()
            photoFile.delete()
        }
    }

    private fun uploadImage() {
        val uploadHandler = Handler(Looper.getMainLooper())
        thread {
            modeling3dTextureInitResult = engine.initTask(setting)
            taskId = modeling3dTextureInitResult?.taskId
            if (taskId.isNullOrEmpty()) {
                Log.e(TAG, "Get taskId error: ${modeling3dTextureInitResult?.retCode}")
                uploadHandler.post {
                    Toast.makeText(
                        this, "Get taskId error: ${modeling3dTextureInitResult?.retCode}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Log.i(TAG, "Get taskId: " + modeling3dTextureInitResult?.retMsg)
                // Set the upload listener.
                engine.setTextureUploadListener(uploadListener)
                engine.asyncUploadFile(taskId, photoStoragePath.toString())
            }

        }

    }

    private val uploadListener = object : Modeling3dTextureUploadListener {
        val uploadHandler = Handler(Looper.getMainLooper())
        override fun onResult(taskId: String, result: Modeling3dTextureUploadResult, ext: Any?) {
            // Obtain the image upload result.
            if (result.isComplete) {
                // Process the upload result.
                uploadHandler.post {
                    // Code will be executed on the main thread
                    Toast.makeText(
                        this@MainActivity,
                        "Upload process successful",
                        Toast.LENGTH_SHORT
                    )
                        .show()

                }
            }
        }

        override fun onError(taskId: String, errorCode: Int, message: String) {
            Log.e("uploading error", errorCode.toString())
            uploadHandler.post {
                // Code will be executed on the main thread
                Toast.makeText(this@MainActivity, "Failure in upload process!", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        override fun onUploadProgress(taskId: String?, p1: Double, ext: Any?) {
            Log.i("uploading", p1.toString())
        }
    }

    private fun queryTask() {
        val queryHandler = Handler(Looper.getMainLooper())

        thread {
            val queryResult = taskUtils.queryTask(taskId)
            val resultCode = queryResult.retCode
            if (resultCode == 0) {
                val ret = queryResult.status
                Log.w(
                    TAG, "Material generation query result status (INITED: 0, " +
                            "UPLOAD_COMPLETED: 1, TEXTURE_START: 2, TEXTURE_COMPLETED: 3, " +
                            "TEXTURE_FAILED: 4): $ret"
                )
                if (ret == 3) {
                    queryHandler.post {
                        downloadBtn.isEnabled = true
                        // Code will be executed on the main thread
                        Toast.makeText(
                            this@MainActivity,
                            "You can download the texture maps now",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } else {
                    queryHandler.post {
                        // Code will be executed on the main thread
                        Toast.makeText(
                            this@MainActivity,
                            "Material generation task is not complete yet!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                queryHandler.post {
                    // Code will be executed on the main thread
                    Toast.makeText(
                        this@MainActivity,
                        "Failure in material generation task. Error code: ${queryResult.retCode}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }
    }

    private fun downloadImage() {
        thread {
            // Set the download listener.
            engine.setTextureDownloadListener(downloadListener)
            // Download the texture maps, passing the task ID and texture map path.
            engine.asyncDownloadTexture(taskId, savePath.toString())
        }
    }

    private var downloadListener: Modeling3dTextureDownloadListener =
        object : Modeling3dTextureDownloadListener {
            val downloadHandler = Handler(Looper.getMainLooper())
            override fun onResult(
                taskId: String,
                result: Modeling3dTextureDownloadResult,
                ext: Any?
            ) {
                // Obtain the download result of generated texture maps.
                if (result.isComplete) {
                    // Process the download result.
                    downloadHandler.post {
                        Toast.makeText(
                            this@MainActivity,
                            "Download success. Task Id: ${result.taskId}",
                            Toast.LENGTH_SHORT
                        ).show()

                    }
                }
            }

            override fun onError(taskId: String, errorCode: Int, message: String) {
                downloadHandler.post {
                    Toast.makeText(
                        this@MainActivity,
                        "Download fail! Error code: $errorCode",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onDownloadProgress(taskId: String, progress: Double, ext: Any?) {
                Log.i("downloading", progress.toString())
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        taskUtils.deleteTask(taskId.toString())
    }
}