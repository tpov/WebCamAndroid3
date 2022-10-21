package com.dolya.webcam.server

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import fi.iki.elonen.NanoHTTPD
import com.dolya.webcam.util.*
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.SocketException
import java.text.SimpleDateFormat
import java.util.*

class MJpegHTTPD(
  hostname: String?, port: Int,
  private val owner: LifecycleOwner,
  private val src: LiveData<Bitmap>,
  private val jpeg_quality: Int,
  private val handler: Handler?
) : NanoHTTPD(hostname, port) {

  companion object {
    private val CRLF = byteArrayOf(0x0d, 0x0a)
    private const val MJPEG_BOUNDARY = "--frame"
    private const val OUTPUT_BUFFERED_SIZE = 5 * 1024
  }

  override fun serve(session: IHTTPSession?): Response {
    val now = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date())
    session?.let {
      Timber.i("${it.remoteIpAddress} - [$now] \"${it.method} ${it.uri}\"")
    }

    return when (session?.uri) {

      /* アクセスされた時点でのJPEG画像を返す。 */
      "/current" -> {
        val output = PipedOutputStream()
        val input = PipedInputStream(output)
        val bufferedOutput = BufferedOutputStream(output, OUTPUT_BUFFERED_SIZE)

        src.observeElementAt(owner, 1, NonNullObserver { bmpImage ->
          (handler ?: Looper.myLooper()?.let { Handler(it) })?.post {
            val body = bmpImage.toJpegByteArray(jpeg_quality)
            kotlin.runCatching {
              bufferedOutput.use { it.write(body) }
            }.onFailure { ex ->
              when (ex) {
                is IOException -> Timber.i("Pipe closed.")
                is SocketException -> Timber.i("Broken pipe.")
                else -> Timber.e(ex, "Unexpected error occurred.")
              }
            }
          }
        })
        newChunkedResponse(Response.Status.OK, "image/jpeg", input)
      }

      /* MJPEGフレームを返し続ける。 */
      "/mjpeg" -> {
        val output = PipedOutputStream()
        val input = PipedInputStream(output)
        val bufferedOutput = BufferedOutputStream(output, OUTPUT_BUFFERED_SIZE)

        val observerStatus = ObserverStatus(ObserverStatus.State.ACTIVE)
        src.observeByStatus(owner, observerStatus, NonNullObserver { bmpImage ->
          observerStatus.state = ObserverStatus.State.INACTIVE
          (handler ?: Looper.myLooper()?.let { Handler(it) })?.post {
            val jpgByteArray = bmpImage.toJpegByteArray(jpeg_quality)
            kotlin.runCatching {
              bufferedOutput.let {
                it.write(MJPEG_BOUNDARY.toByteArray() + CRLF)
                it.write("Content-Type: image/jpeg".toByteArray() + CRLF)
                it.write("Content-Length: ${jpgByteArray.size}".toByteArray() + CRLF + CRLF)
                it.write(jpgByteArray + CRLF)
                it.flush()
              }
              observerStatus.state = ObserverStatus.State.ACTIVE
            }.onFailure { ex ->
              observerStatus.state = ObserverStatus.State.REMOVED
              kotlin.runCatching { bufferedOutput.close() }
                .onFailure { Timber.i("Fail to close output pipe.") }
              when (ex) {
                is IOException -> Timber.i("Pipe closed.")
                is SocketException -> Timber.i("Broken pipe.")
                else -> Timber.e(ex, "Unexpected error occurred.")
              }
            }
          }
        })
        newChunkedResponse(
          Response.Status.OK, "multipart/x-mixed-replace; boundary=$MJPEG_BOUNDARY", input
        )
      }

      else -> newFixedLengthResponse(
        "<html><body>"
            + "<h1>GET /current</h1><p>GET a current JPEG image.</p>"
            + "<h1>GET /mjpeg</h1><p>GET MJPEG frames.</p>"
            + "</body></html>"
      )

    }
  }
}
