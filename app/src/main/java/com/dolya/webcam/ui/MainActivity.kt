package com.dolya.webcam.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbDevice
import android.media.ImageReader
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.format.Formatter
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.dolya.webcam.R
import com.dolya.webcam.camera.CameraCaptureSessionData
import com.dolya.webcam.camera.CameraCaptureSessionData.CameraCaptureSessionStateEvents
import com.dolya.webcam.camera.CameraComponent
import com.dolya.webcam.camera.CameraDeviceData.DeviceStateEvents
import com.dolya.webcam.server.MJpegHTTPD
import com.dolya.webcam.server.Yuv420ToBitmapConverter
import com.dolya.webcam.util.NonNullObserver
import com.dolya.webcam.util.addSourceNonNullObserve
import com.dolya.webcam.util.observeElementAt
import com.google.android.material.snackbar.Snackbar
import com.jiangdg.usbcamera.UVCCameraHelper
import com.serenegiant.usb.widget.CameraViewInterface
import kotlinx.android.synthetic.main.activity_main.*
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.RuntimePermissions
import timber.log.Timber


@RuntimePermissions
class MainActivity : AppCompatActivity() {
  private lateinit var server: MJpegHTTPD
  private val cameraImage = MutableLiveData<Bitmap>()

  private lateinit var cameraComponent: CameraComponent
  private val captureManager = MediatorLiveData<Unit>()
  private lateinit var imageReader: ImageReader
  private var imageSize: Size = Size(1440, 1080)
  private lateinit var converter: Yuv420ToBitmapConverter
  private lateinit var tvIp: TextView

  private var backgroundThread: HandlerThread? = null
  private var backgroundHandler: Handler? = null
  private var imgCnvThread: HandlerThread? = null
  private var imgCnvHandler: Handler? = null
  private var mjpegHttpdThread: HandlerThread? = null
  private var mjpegHttpdHandler: Handler? = null

  private lateinit var mCameraHelper: UVCCameraHelper
  private lateinit var mUVCCameraView: CameraViewInterface
  private lateinit var mDialog: AlertDialog

  private var isRequest = false
  private var isPreview = false

  @RequiresApi(Build.VERSION_CODES.O)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    log("onCreate")
    setContentView(R.layout.activity_main)
    checkWifi()
    initView()
    startBackgroundThread()
    initUsbCam()
    cameraComponent = CameraComponent(
      cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager,
      cameraId = "0",
      handler = backgroundHandler
    )
    setupCaptureManagerWithPermissionCheck()
    setupSurfaceTexture()
    setupImageReader(imageSize.width, imageSize.height)
    converter = Yuv420ToBitmapConverter(imgCnvHandler, this)

    lifecycle.addObserver(cameraComponent)
    server =
      MJpegHTTPD(
        getIdAddress(),
        PORT,
        this,
        cameraImage,
        20,
        mjpegHttpdHandler
      ).also { it.start() }
  }

  private fun initUsbCam() {

    var mCallback = object : CameraViewInterface.Callback {

      override fun onSurfaceCreated(view: CameraViewInterface?, surface: Surface?) {
        if (!isPreview && mCameraHelper.isCameraOpened) {
          mCameraHelper.startPreview(mUVCCameraView);
          isPreview = true;
        }
      }

      override fun onSurfaceChanged(
        view: CameraViewInterface?,
        surface: Surface?,
        width: Int,
        height: Int
      ) {

      }

      override fun onSurfaceDestroy(view: CameraViewInterface?, surface: Surface?) {
        if (isPreview && mCameraHelper.isCameraOpened) {
          mCameraHelper.stopPreview();
          isPreview = false;
        }
      }
    }

    var listener = object : UVCCameraHelper.OnMyDevConnectListener {

      override fun onAttachDev(device: UsbDevice?) {
        if (!isRequest) {
          isRequest = true;
          if (mCameraHelper != null) {
            mCameraHelper.requestPermission(0);
          }
        }
      }

      override fun onDettachDev(device: UsbDevice?) {
        if (isRequest) {
          isRequest = false;
          mCameraHelper.closeCamera();
        }
      }

      override fun onConnectDev(device: UsbDevice?, isConnected: Boolean) {

      }

      override fun onDisConnectDev(device: UsbDevice?) {

      }
    };

    mUVCCameraView = texture_view as CameraViewInterface
    mUVCCameraView.setCallback(mCallback)
    mCameraHelper = UVCCameraHelper.getInstance()
// set default preview size
    // set default preview size
    mCameraHelper.setDefaultPreviewSize(1280, 720)
// set default frame format，defalut is UVCCameraHelper.Frame_FORMAT_MPEG
// if using mpeg can not record mp4,please try yuv
// mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
// set default frame format，defalut is UVCCameraHelper.Frame_FORMAT_MPEG
// if using mpeg can not record mp4,please try yuv
// mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
    mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener)

  }

  private fun checkWifi() {
    val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    wifi.isWifiEnabled = false // activate/deactivate wifi
  }

  @SuppressLint("SetTextI18n")
  private fun initView() {
    tvIp = findViewById(R.id.tv_ip)
    tvIp.setTextColor(getColor(R.color.colorWhite))
    tvIp.text = "Ip: ${getIdAddress()}\nport: $PORT"
  }

  private fun getIdAddress(): String? =
    Formatter.formatIpAddress((applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).connectionInfo.ipAddress)


  private fun log(massage: String) {
    Timber.tag("MainActivitydawd").d(massage)
  }

  override fun onDestroy() {
    super.onDestroy()
    log("onDestroy")
  }

  @RequiresApi(Build.VERSION_CODES.N)
  override fun onUserLeaveHint() {
    log("onUserLeaveHint")
    return enterPictureInPictureMode()
  }

  @NeedsPermission(Manifest.permission.CAMERA)
  fun setupCaptureManager() {}/*{
    log("setupCaptureManager")
    captureManager.addSourceNonNullObserve(cameraComponent.cameraDeviceLiveData) { cameraDeviceData ->
      var captureSession: CameraCaptureSession? = null
      var captureSessionLiveData: LiveData<CameraCaptureSessionData>? = null

      if (cameraDeviceData.deviceStateEvents == DeviceStateEvents.ON_OPENED) {
        val targetSurfaces = listOf(Surface(texture_view.surfaceTexture), imageReader.surface)
        val previewCaptureRequest = cameraDeviceData.createPreviewCaptureRequest(targetSurfaces)
          ?: return@addSourceNonNullObserve
        captureSessionLiveData =
          cameraDeviceData.createCaptureSession(targetSurfaces, backgroundHandler)
        captureManager.addSourceNonNullObserve(captureSessionLiveData) {
          if (it.cameraCaptureSessionStateEvents == CameraCaptureSessionStateEvents.ON_READY) {
            captureSession = it.cameraCaptureSession
            it.setRepeatingRequest(previewCaptureRequest, backgroundHandler)
          }
        }
      } else if (cameraDeviceData.deviceStateEvents == DeviceStateEvents.ON_CLOSED) {
        captureSession?.close()
        captureSessionLiveData?.also { captureManager.removeSource(it) }
      }
    }
  }*/

  @RequiresApi(Build.VERSION_CODES.O)
  override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
    log("onPictureInPictureModeChanged")
    super.onPictureInPictureModeChanged(isInPictureInPictureMode)
    if (isInPictureInPictureMode) {
      stopBackgroundThread()
      server.stop()
      recreate()

      val pictureInPictureParamsBuilder = PictureInPictureParams.Builder()
      val aspectRatio = Rational(imageSize.width, imageSize.height)
      pictureInPictureParamsBuilder.setAspectRatio(aspectRatio)
      enterPictureInPictureMode(pictureInPictureParamsBuilder.build())
      // Restore the full-screen UI.
    }
  }

  private fun setupSurfaceTexture() {
    log("setupSurfaceTexture")
    texture_view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
      override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
      override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
      override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Timber.d("onSurfaceTextureAvailable: $width x $height")
        imageSize = Size(width, height)
        captureManager.observe(this@MainActivity, Observer {})
      }

      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        log("onSurfaceTextureDestroyed")
        captureManager.removeObservers(this@MainActivity)
        return true
      }
    }
  }

  private var isProcessingFrame = false
  private fun setupImageReader(width: Int, height: Int) {
    log("setupImageReader")
    imageReader = ImageReader
      .newInstance(width, height, ImageFormat.YUV_420_888, 2)
      .apply {
        setOnImageAvailableListener({ reader ->
          val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
          if (cameraImage.hasActiveObservers() && !isProcessingFrame) {
            isProcessingFrame = true
            converter.enqueue(image)
              .observeElementAt(this@MainActivity, 0, NonNullObserver {
                cameraImage.postValue(it)
                image.close()
                isProcessingFrame = false
              })
          } else {
            image.close()
          }
        }, backgroundHandler)
      }
  }

  private fun startBackgroundThread() {
    log("startBackgroundThread")
    backgroundThread = HandlerThread("ImageListener").also { it.start() }
    backgroundHandler = backgroundThread?.looper?.let { Handler(it) }

    imgCnvThread = HandlerThread("imageConverter").also { it.start() }
    imgCnvHandler = imgCnvThread?.looper?.let { Handler(it) }

    mjpegHttpdThread = HandlerThread("mjpegHttpd").also { it.start() }
    mjpegHttpdHandler = mjpegHttpdThread?.looper?.let { Handler(it) }
  }

  private fun stopBackgroundThread() {
    backgroundThread?.quitSafely()
    try {
      backgroundThread?.join()
      backgroundThread = null
      backgroundHandler = null
    } catch (e: InterruptedException) {
      Timber.e(e, "Exception!")
    }

    imgCnvThread?.quitSafely()
    try {
      imgCnvThread?.join()
      imgCnvThread = null
      imgCnvHandler = null
    } catch (e: InterruptedException) {
      Timber.e(e, "Exception!")
    }

    mjpegHttpdThread?.quitSafely()
    try {
      mjpegHttpdThread?.join()
      mjpegHttpdThread = null
      mjpegHttpdHandler = null
    } catch (e: InterruptedException) {
      Timber.e(e, "Exception!")
    }
  }

  @OnPermissionDenied(Manifest.permission.CAMERA)
  fun onCameraDenied() {
    log("onCameraDenied")
    Snackbar.make(content, "камера недоступна。", Snackbar.LENGTH_LONG).show()
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    log("onRequestPermissionsResult")

    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    onRequestPermissionsResult(requestCode, grantResults)
  }

  companion object {
    const val PORT = 8080
  }
}
