package jp.yama07.webcam.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import jp.yama07.webcam.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class Services : Service() {

  private var backgroundThread: HandlerThread? = null
  private var backgroundHandler: Handler? = null
  private var imgCnvThread: HandlerThread? = null
  private var imgCnvHandler: Handler? = null
  private var mjpegHttpdThread: HandlerThread? = null
  private var mjpegHttpdHandler: Handler? = null

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

    coroutineScope.launch {
      while (true) {

        startStream()
        notificationManager.notify(NOTIFICATION_ID, notification)
        createNotificationChannel()
      }
    }
    return START_STICKY
  }

  private fun startStream() {
    backgroundThread = HandlerThread("ImageListener").also { it.start() }
    backgroundHandler = backgroundThread?.looper?.let { Handler(it) }

    imgCnvThread = HandlerThread("imageConverter").also { it.start() }
    imgCnvHandler = imgCnvThread?.looper?.let { Handler(it) }

    mjpegHttpdThread = HandlerThread("mjpegHttpd").also { it.start() }
    mjpegHttpdHandler = mjpegHttpdThread?.looper?.let { Handler(it) }
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