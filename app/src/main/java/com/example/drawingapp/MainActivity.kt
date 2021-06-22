package com.example.drawingapp

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.brush_size_dialog.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import top.defaults.colorpicker.ColorPickerPopup
import top.defaults.colorpicker.ColorPickerPopup.ColorPickerObserver
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {

    var eraserActive = false
    var currentColor = 0
    var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                try {
                    if (data!!.data != null) {
                        backgroundIV.visibility = View.VISIBLE
                        backgroundIV.setImageURI(data.data)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Error in parsing the image or it is corrupted.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    private lateinit var mProgressDialog: Dialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingViewXml.setBrushSize(5.toFloat())
        brushButton.setOnClickListener {
            showBrushSizeDialog()
        }
        currentColor = Color.BLACK
        colorPickerButton.setOnClickListener {
            showColorPicker(colorView)
        }
        eraserButton.setOnClickListener {
            onEraserClick(eraserButton)
        }
        galleryButton.setOnClickListener {
            if (isReadStoragePermissionGranted()) {
                val pickImageIntent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                resultLauncher.launch(pickImageIntent)
            } else {
                requestStoragePermission()
            }
        }
        undoButton.setOnClickListener {
            drawingViewXml.onClickUndo()
        }
        saveButton.setOnClickListener {
            if (isReadStoragePermissionGranted()){
                mProgressDialog = Dialog(this)
                mProgressDialog.setContentView(R.layout.dialog_custom_progress)
                mProgressDialog.show()
                CoroutineScope(IO).launch {
                    saveBitmap(getBitmapFromView(fullFrameLayout))
                }
            }else{
                requestStoragePermission()
            }
        }
        shareButton.setOnClickListener {
            if (isReadStoragePermissionGranted()){
                mProgressDialog = Dialog(this)
                mProgressDialog.setContentView(R.layout.dialog_custom_progress)
                mProgressDialog.show()
                CoroutineScope(IO).launch {
                    shareBitmap(getBitmapFromView(fullFrameLayout))
                }
            }else{
                requestStoragePermission()
            }
        }
    }

    private fun showBrushSizeDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.brush_size_dialog)
        brushDialog.setTitle("Brush Size")
        val xtraSmallBtn = brushDialog.xtraSmallBrushButton
        xtraSmallBtn.setOnClickListener {
            drawingViewXml.setBrushSize(5.toFloat())
            brushDialog.dismiss()
        }
        val smallBtn = brushDialog.smallBrushButton
        smallBtn.setOnClickListener {
            drawingViewXml.setBrushSize(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn = brushDialog.mediumBrushButton
        mediumBtn.setOnClickListener {
            drawingViewXml.setBrushSize(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn = brushDialog.largeBrushButton
        largeBtn.setOnClickListener {
            drawingViewXml.setBrushSize(30.toFloat())
            brushDialog.dismiss()
        }
        val xtraLargeBtn = brushDialog.xtraLargeBrushButton
        xtraLargeBtn.setOnClickListener {
            drawingViewXml.setBrushSize(40.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()

    }

    private fun showColorPicker(v: View) {
        ColorPickerPopup.Builder(this)
            .initialColor(Color.BLACK) // Set initial color
            .enableBrightness(true) // Enable brightness slider or not
            .enableAlpha(true) // Enable alpha slider or not
            .okTitle("Choose")
            .cancelTitle("Cancel")
            .showIndicator(true)
            .showValue(false)
            .build()
            .show(v, object : ColorPickerObserver() {
                override fun onColorPicked(color: Int) {
                    v.setBackgroundColor(color)
                    drawingViewXml.setColor(color)
                    currentColor = color
                }

                fun onColor(color: Int, fromUser: Boolean) {}
            })
    }

    private fun onEraserClick(view: MaterialCardView) {
        if (eraserActive) {
            eraserActive = false
            view.strokeWidth = 0
            drawingViewXml.setColor(currentColor)
        } else {
            eraserActive = true
            view.strokeWidth = 5
            drawingViewXml.setColor(Color.WHITE)
        }
    }

    private fun requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ).toString()
            )
        ) {
            Snackbar.make(
                drawingViewXml,
                "Need permission to draw on an image !",
                Snackbar.LENGTH_SHORT
            ).show()
        }
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), STORAGE_PERMISSION_CODE
        )
    }

    private fun isReadStoragePermissionGranted(): Boolean {
        val result =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)

        return returnedBitmap
    }

    private suspend fun saveBitmap(mBitmap: Bitmap){
        var result = ""
        if (mBitmap != null) {
            try {
                val bytes = ByteArrayOutputStream()
                mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                val file_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath +
                        "/DrawingApp"
                val dir = File(file_path)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "DrawingApp_" + System.currentTimeMillis()/1000 + ".png")
                val fos = FileOutputStream(file)
                fos.write(bytes.toByteArray())
                fos.close()
                result =  file.absolutePath
            } catch (e: Exception) {
                result = ""
                e.printStackTrace()
            }
        }

        runOnUiThread{
            mProgressDialog.dismiss()
            if (result.isEmpty()){
                Toast.makeText(this@MainActivity, "Something went wrong while saving the file.",Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(this@MainActivity, "File saved successfully at :- $result",Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun shareBitmap(mBitmap: Bitmap){
        var result = ""
        if (mBitmap != null) {
            try {
                val bytes = ByteArrayOutputStream()
                mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
                val file_path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath +
                        "/DrawingApp"
                val dir = File(file_path)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "DrawingApp_" + System.currentTimeMillis()/1000 + ".png")
                val fos = FileOutputStream(file)
                fos.write(bytes.toByteArray())
                fos.close()
                result =  file.absolutePath
            } catch (e: Exception) {
                result = ""
                e.printStackTrace()
            }
        }

        runOnUiThread{
            mProgressDialog.dismiss()
            if (result.isEmpty()){
                Toast.makeText(this@MainActivity, "Something went wrong while saving the file.",Toast.LENGTH_SHORT).show()
            }else{
                MediaScannerConnection.scanFile(this@MainActivity, arrayOf(result),null){
                    path, uri -> val shareIntent = Intent()
                    shareIntent.action = Intent.ACTION_SEND
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                    shareIntent.type = "image/png"

                    startActivity(
                        Intent.createChooser(
                            shareIntent, "Share"
                        )
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this@MainActivity, "Permission granted !", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Oops you denied the permission, You can grant permission from settings..",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
    }

}