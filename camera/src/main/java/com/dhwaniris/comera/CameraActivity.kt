package com.dhwaniris.comera

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.constraint.ConstraintLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDelegate
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.dhwaniris.comera.widgets.CameraSwitchView
import com.dhwaniris.comera.widgets.FlashSwitchView
import io.fotoapparat.Fotoapparat
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.selector.autoFlash
import io.fotoapparat.selector.back
import io.fotoapparat.selector.front
import io.fotoapparat.selector.off
import io.fotoapparat.selector.on
import io.fotoapparat.view.CameraView
import kotlin.math.max


class CameraActivity : AppCompatActivity() {

  init {
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
  }

  private val cameraView by lazy { findViewById<CameraView>(R.id.cameraView) }
  private val capture by lazy { findViewById<View>(R.id.capture) }
  private val cameraSwitchView by lazy { findViewById<CameraSwitchView>(R.id.cameraSwitchView) }
  private val flashSwitchView by lazy { findViewById<FlashSwitchView>(R.id.flashSwitchView) }

  private val ivPreview by lazy { findViewById<ImageView>(R.id.iv_preview) }
  private val bRetry by lazy { findViewById<ImageView>(R.id.b_retry) }
  private val bAccept by lazy { findViewById<ImageView>(R.id.b_accept) }
  private val bReject by lazy { findViewById<ImageView>(R.id.b_reject) }

  private val flPreview by lazy { findViewById<FrameLayout>(R.id.fl_preview) }
  private val clCamera by lazy { findViewById<ConstraintLayout>(R.id.cl_camera) }

  private lateinit var fotoapparat: Fotoapparat
  private val permissionsDelegate = PermissionsDelegate(this)
  private lateinit var imageUri: Uri
  private var isBackCamera = true
  private val flashManager = FlashManager()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_camera)

    actionBar?.hide()
    supportActionBar?.hide()

    imageUri = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)

    if (permissionsDelegate.hasCameraPermission()) {
      cameraView.visibility = View.VISIBLE
    } else {
      permissionsDelegate.requestCameraPermission()
    }

    fotoapparat = Fotoapparat(
        context = this,
        view = cameraView         // view which will draw the camera preview
    )

    capture.setOnClickListener {
      val photoResult = fotoapparat
          .autoFocus()
          .takePicture()

      val outputStream = contentResolver.openOutputStream(imageUri).buffered()
      photoResult.toPendingResult().transform { input ->
        outputStream.use {
          it.write(input.encodedImage)
          it.flush()
        }
      }

//      photoResult
//          .toPendingResult()
//          .whenAvailable {
//            it?.encodedImage?.let {
//              val bitmap = decodeSampledBitmapFromByteArray(it, ivPreview.width, ivPreview.height)
//              ivPreview.setImageBitmap(bitmap)
//              ivPreview.setRotation(.rotationDegrees)
//              clCamera.visibility = View.GONE
//              flPreview.visibility = View.VISIBLE
//            }
//          }

      photoResult
          .toBitmap {
            val scale = max(ivPreview.height, ivPreview.width).toFloat() / max(it.height,
                it.width).toFloat()
            Resolution(height = (it.height * scale).toInt(), width = (it.width * scale).toInt())
          }
          .transform {
            it.bitmap.rotate(-it.rotationDegrees.toFloat())
          }
          .whenAvailable { photo: Bitmap? ->
            photo?.let {
              ivPreview.setImageBitmap(it)
              clCamera.visibility = View.GONE
              flPreview.visibility = View.VISIBLE
            }
          }

      bAccept.setOnClickListener {
        val result = Intent()
        result.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        setResult(RESULT_OK, result)
        finish()
      }

      bReject.setOnClickListener {
        contentResolver.delete(imageUri, null, null)
        val result = Intent()
        setResult(Activity.RESULT_CANCELED, result)
        finish()
      }

      bRetry.setOnClickListener {
        clCamera.visibility = View.VISIBLE
        flPreview.visibility = View.GONE
      }
    }

    cameraSwitchView.setOnClickListener {

      if (isBackCamera) {
        fotoapparat.switchTo(front(), CameraConfiguration.default())
        cameraSwitchView.displayFrontCamera()
      } else {
        fotoapparat.switchTo(back(), CameraConfiguration.default())
        cameraSwitchView.displayBackCamera()
      }

      isBackCamera = !isBackCamera
    }

    flashSwitchView.setOnClickListener {
      val current = flashManager.switch()
      when (current) {
        is CameraFlashAuto -> {
          fotoapparat.updateConfiguration(
              CameraConfiguration.default().copy(flashMode = autoFlash()))
          flashSwitchView.displayFlashAuto()
        }
        is CameraFlashOn -> {
          fotoapparat.updateConfiguration(CameraConfiguration.default().copy(flashMode = on()))
          flashSwitchView.displayFlashOn()
        }
        is CameraFlashOff -> {
          fotoapparat.updateConfiguration(CameraConfiguration.default().copy(flashMode = off()))
          flashSwitchView.displayFlashOff()
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    if (permissionsDelegate.hasCameraPermission()) {
      fotoapparat.start()
    }
  }

  override fun onStop() {
    super.onStop()
    if (permissionsDelegate.hasCameraPermission()) {
      fotoapparat.stop()
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
      grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (permissionsDelegate.resultGranted(requestCode, permissions, grantResults)) {
      fotoapparat.start()
      cameraView.visibility = View.VISIBLE
    }
  }
}

sealed class CameraFlashState

object CameraFlashAuto : CameraFlashState()
object CameraFlashOff : CameraFlashState()
object CameraFlashOn : CameraFlashState()

class FlashManager {
  private val modes = listOf(CameraFlashAuto, CameraFlashOn, CameraFlashOff)
  private var current = 0

  fun switch(): CameraFlashState {
    current = (++current) % modes.size
    return modes[current]
  }

  fun current(): CameraFlashState {
    return modes[current]
  }
}

fun Bitmap.rotate(angle: Float): Bitmap {
  val matrix = Matrix()
  matrix.postRotate(angle)
  val result = Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
  this.recycle()
  return result
}