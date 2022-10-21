package com.dolya.webcam.Services

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.dolya.webcam.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class Services : Service() {

  private var finishDialog = false
  private var finish = false

  private val coroutineScope = CoroutineScope(Dispatchers.Main)

  private val notificationManager by lazy {
    getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  }

  private val notificationBuilder by lazy {
    createNotificationBuilder()
  }

  @Nullable
  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  override fun onCreate() {
    super.onCreate()

    startForeground(NOTIFICATION_ID, notificationBuilder.build())
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = notificationBuilder.build()

    notificationManager.notify(NOTIFICATION_ID, notification)
    val manager =
      applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val view = layoutInflater.inflate(R.layout.activity_main, null)

    coroutineScope.launch {
      while (!finish) {
        when {
          manager.isDeviceSecure -> {
            stopSelf()
          }
          else -> {

            finishDialog = false
            finish = false
            notificationManager.notify(NOTIFICATION_ID, notification)
            createDialog(windowManager, view)

            createNotificationChannel()
            delay(DELAY_DIALOG)
          }
        }

        delay(DELAY)
        if (!finishDialog) {
          finishDialog = true
          windowManager.removeView(view)
        }
      }
    }
    return START_STICKY
  }

  @SuppressLint("InvalidWakeLockTag", "ResourceAsColor")
  private fun createDialog(windowManager: WindowManager, view: View) {

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    )

    params.gravity = Gravity.CENTER or Gravity.CENTER
    params.x = 0
    params.y = 0
    windowManager.addView(view, params)

  }



  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationChannel = NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_DEFAULT
      )
      notificationManager.createNotificationChannel(notificationChannel)
    }
  }

  private fun createNotificationBuilder() = NotificationCompat.Builder(this, CHANNEL_ID)
    .setContentTitle(CHANNEL_ID)
    .setContentText(CHANNEL_NAME)
    .setSmallIcon(R.drawable.ic_launcher_foreground)

  companion object {

    private const val CHANNEL_ID = "channel_id"
    private const val CHANNEL_NAME = "create_password"
    private const val NOTIFICATION_ID = 1
    private const val DELAY: Long = 90000
    private const val DELAY_DIALOG: Long = 90000
  }
}