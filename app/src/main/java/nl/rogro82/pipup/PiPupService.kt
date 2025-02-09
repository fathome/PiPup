package nl.rogro82.pipup

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.io.File
import java.io.IOException


class PiPupService : Service(), WebServer.Handler {
    private val mHandler: Handler = Handler()
    private var mOverlay: FrameLayout? = null
    private var mPopup: MutableList<PopupView>? = null
    private lateinit var mWebServer: WebServer

    override fun onCreate() {
        super.onCreate()

        initNotificationChannel("service_channel", "Service channel", "Service channel")

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java), 0
        )

        val mBuilder = NotificationCompat.Builder(this, "service_channel")
            .setContentTitle("PiPup")
            .setContentText("Service running")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setAutoCancel(false)
            .setOngoing(true)

        startForeground(ONGOING_NOTIFICATION_ID, mBuilder.build())

        mWebServer = WebServer(SERVER_PORT, this).apply {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }

        Log.d(LOG_TAG, "WebServer started")
    }

    override fun onDestroy() {
        super.onDestroy()

        mWebServer.stop()
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun initNotificationChannel(id: String, name: String, description: String) {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(id, name,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = description
        notificationManager.createNotificationChannel(channel)
    }

    private fun removePopup(removeOverlay: Boolean = false) {

        mHandler.removeCallbacksAndMessages(null)

        mPopup = mPopup?.let {
            for ( p in it) {
                p.destroy()
            }
            null
        }

        mOverlay?.apply {

            removeAllViews()
            if (removeOverlay) {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeViewImmediate(mOverlay)

                mOverlay = null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun createPopup(popup: PopupProps) {
        try {

            Log.d(LOG_TAG, "Create popup: $popup")

            // remove current popup

            removePopup()

            // create or reuse the current overlay

            mOverlay = when (val overlay = mOverlay) {
                is FrameLayout -> overlay
                else -> FrameLayout(this).apply {

                    setPadding(20, 20, 20, 20)

                    val layoutFlags: Int = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        else -> WindowManager.LayoutParams.TYPE_TOAST
                    }

                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        layoutFlags,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                    )

                    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.addView(this, params)
                }
            }.also {

                mPopup = mutableListOf<PopupView>()
                // inflate the popup layout
                for (i in popup.media.indices) {
                    val popupView = PopupView.build(this, popup,i)
                    mPopup!!.add(popupView)

                    it.addView(popupView, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ). apply {

                        // position the popup
                        val pos =  popup.position.ordinal + i % 5

                        gravity = when(  PopupProps.Position.getByValue(pos) ) {
                            PopupProps.Position.TopRight -> Gravity.TOP or Gravity.END
                            PopupProps.Position.TopLeft -> Gravity.TOP or Gravity.START
                            PopupProps.Position.BottomRight -> Gravity.BOTTOM or Gravity.END
                            PopupProps.Position.BottomLeft -> Gravity.BOTTOM or Gravity.START
                            PopupProps.Position.Center -> Gravity.CENTER
                            else -> {
                                Gravity.TOP or Gravity.END
                            }
                        }
                    })
                }

                // schedule removal

                mHandler.postDelayed({
                    removePopup(true)
                }, (popup.duration * 1000).toLong())

                }

            // Play Notification Sound
            if (popup.notificationSound) {
                var mediaPlayer = MediaPlayer.create(this, R.raw.highintensity)
                mediaPlayer.start()
            }

        } catch (ex: Throwable) {
            ex.printStackTrace()
        }
    }

    override fun handleHttpRequest(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
        return session?.let {
            when(session.method) {
                NanoHTTPD.Method.POST -> {

                    when(session.uri) {
                        "/cancel" -> {
                            mHandler.post {
                                removePopup(true)
                            }
                            OK()
                        }
                        "/notify" -> {
                            try {
                                val contentType = session.headers["content-type"] ?: APPLICATION_JSON
                                val popup = when {
                                    contentType.startsWith(APPLICATION_JSON) -> {

                                        // try to handle it as json

                                        val contentLength = session.headers["content-length"]?.toInt() ?: 0
                                        val content = ByteArray(contentLength)

                                        session.inputStream.read(content, 0, contentLength)

                                        Json.readValue(content, PopupProps::class.java)
                                            ?: throw Exception("failed to parse input")

                                    }
                                    contentType.startsWith(MULTIPART_FORM_DATA) -> {

                                        val files = mutableMapOf<String, String>()
                                        session.parseBody(files)

                                        // flatten parameters

                                        val params = session.parameters.mapValues { it.value.firstOrNull() }

                                        val duration = params["duration"]?.toIntOrNull()
                                            ?: PopupProps.DEFAULT_DURATION

                                        val position = PopupProps.Position.values()[params["position"]?.toIntOrNull() ?: 0]

                                        val backgroundColor = params["backgroundColor"]
                                            ?: PopupProps.DEFAULT_BACKGROUND_COLOR

                                        val title = params["title"]

                                        val titleSize = params["titleSize"]?.toFloatOrNull()
                                            ?: PopupProps.DEFAULT_TITLE_SIZE

                                        val titleColor = params["titleColor"]
                                            ?: PopupProps.DEFAULT_TITLE_COLOR

                                        val message = params["message"]

                                        val messageSize = params["messageSize"]?.toFloatOrNull()
                                            ?: PopupProps.DEFAULT_TITLE_SIZE

                                        val messageColor = params["messageColor"]
                                            ?: PopupProps.DEFAULT_TITLE_COLOR

                                        val media = when(val image = files["image"]) {
                                            is String -> {
                                                File(image).absoluteFile.let {
                                                    val bitmap = BitmapFactory.decodeStream(it.inputStream())
                                                    val imageWidth = params["imageWidth"]?.toIntOrNull() ?: PopupProps.DEFAULT_MEDIA_WIDTH

                                                    PopupProps.Media.Bitmap(image = bitmap, width = imageWidth)
                                                }
                                            }
                                            else -> null
                                        }

                                        val medialist =  mutableListOf<PopupProps.Media>()
                                        if (media != null) {
                                            medialist.add(media)
                                        }
                                        PopupProps(
                                            duration = duration,
                                            position = position,
                                            backgroundColor =  backgroundColor,
                                            title = title,
                                            titleSize = titleSize,
                                            titleColor = titleColor,
                                            message = message,
                                            messageSize = messageSize,
                                            messageColor = messageColor,
                                            media = medialist
                                        )
                                    }
                                    else -> throw Exception("invalid content-type")
                                }

                                Log.d(LOG_TAG, "received popup: $popup")

                                mHandler.post {
                                    createPopup(popup)
                                }

                                OK("$popup")


                            } catch (ex: Throwable) {
                                Log.e(LOG_TAG, ex.message.toString())
                                InvalidRequest(ex.message)
                            }
                        }
                        else -> InvalidRequest("unknown uri: ${session.uri}")
                    }
                }
                else -> InvalidRequest("invalid method")
            }
        } ?: InvalidRequest()
    }

    companion object {
        const val LOG_TAG = "PiPupService"
        const val SERVER_PORT = 7979
        const val ONGOING_NOTIFICATION_ID = 123
        const val MULTIPART_FORM_DATA = "multipart/form-data"
        const val APPLICATION_JSON = "application/json"

        fun OK(message: String? = null): NanoHTTPD.Response = newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", message)
        fun InvalidRequest(message: String? = null): NanoHTTPD.Response = newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "invalid request: $message")
    }
}
