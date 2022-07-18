package nl.rogro82.pipup

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.widget.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout


// TODO: convert dimensions from px to dp

@SuppressLint("ViewConstructor")
sealed class PopupView(context: Context, val popup: PopupProps) : LinearLayout(context) {

    open fun create() {


        inflate(context, R.layout.popup,this)

        layoutParams = LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            orientation = VERTICAL
            minimumWidth = 240
        }

        setPadding(20,20,20,20)

        val title = findViewById<TextView>(R.id.popup_title)
        val message = findViewById<TextView>(R.id.popup_message)
        val frame = findViewById<FrameLayout>(R.id.popup_frame)

        if(popup.media == null) {
            removeView(frame)
        }

        if(popup.title.isNullOrEmpty()) {
            removeView(title)
        } else {
            title.text = popup.title
            title.textSize = popup.titleSize
            title.setTextColor(Color.parseColor(popup.titleColor))
        }

        if(popup.message.isNullOrEmpty()) {
            removeView(message)
        } else {
            message.text = popup.message
            message.textSize = popup.messageSize
            message.setTextColor(Color.parseColor(popup.messageColor))
        }

        setBackgroundColor(Color.parseColor(popup.backgroundColor))
    }

    open fun destroy() {}

    private class Default(context: Context, popup: PopupProps) : PopupView(context, popup) {
        init { create() }
    }

    private class Video(context: Context, popup: PopupProps, val media: PopupProps.Media.Video): PopupView(context, popup) {
        private lateinit var mVideoView: VideoView

        init { create() }

        override fun create() {
            super.create()

            visibility = View.INVISIBLE

            val frame = findViewById<FrameLayout>(R.id.popup_frame)

            mVideoView = VideoView(context).apply {
                setVideoURI(Uri.parse(media.uri))
                setOnPreparedListener {
                    it.setOnVideoSizeChangedListener { _, _, _ ->

                        // resize video and show popup view

                        layoutParams = FrameLayout.LayoutParams(media.width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                            gravity = Gravity.CENTER
                        }

                        this@Video.visibility = View.VISIBLE
                    }
                }

                start()
            }

            frame.addView(mVideoView, FrameLayout.LayoutParams(1, 1))
        }

        override fun destroy() {
            try {
                if(mVideoView.isPlaying) {
                    mVideoView.stopPlayback()
                }
            } catch(e: Throwable) {}
        }
    }


    private class Image(context: Context, popup: PopupProps, val media: PopupProps.Media.Image): PopupView(context, popup) {
        init { create() }

        override fun create() {
            super.create()

            val frame = findViewById<FrameLayout>(R.id.popup_frame)

            try {
                val imageView = ImageView(context)

                val layoutParams =
                    FrameLayout.LayoutParams(media.width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER
                    }

                frame.addView(imageView, layoutParams)

                Glide.with(context)
                    .load(Uri.parse(media.uri))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(imageView)

            } catch(e: Throwable) {
                removeView(frame)
            }
        }
    }

    private class Bitmap(context: Context, popup: PopupProps, val media: PopupProps.Media.Bitmap): PopupView(context, popup) {
        var mImageView: ImageView? = null

        init { create() }

        override fun create() {
            super.create()

            val frame = findViewById<FrameLayout>(R.id.popup_frame)
            mImageView = ImageView(context).apply {
                setImageBitmap(media.image)
            }

            val scaledHeight = ((media.width.toFloat() / media.image.width) * media.image.height).toInt()
            val layoutParams =
                FrameLayout.LayoutParams(media.width, scaledHeight).apply {
                    gravity = Gravity.CENTER
                }

            frame.addView(mImageView, layoutParams)
        }

        override fun destroy() {
            try {
                mImageView?.setImageDrawable(null)
                media.image.recycle()
            } catch(e: Throwable) {}
        }
    }

    private class Web(context: Context, popup: PopupProps, val media: PopupProps.Media.Web): PopupView(context, popup) {
        init { create() }

        override fun create() {
            super.create()

            val frame = findViewById<FrameLayout>(R.id.popup_frame)
            val webView = WebView(context).apply {
                with(settings) {
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                }
                loadUrl(media.uri)
            }

            val layoutParams = FrameLayout.LayoutParams(
                media.width,
                media.height
            ).apply {
                gravity = Gravity.CENTER
            }

            frame.addView(webView, layoutParams)
        }
    }

    private class VLC(context: Context, popup: PopupProps, val media: PopupProps.Media.VLC): PopupView(context, popup) {

        private lateinit var libVlc: LibVLC
        private lateinit var mediaPlayer: MediaPlayer
        private lateinit var videoLayout: VLCVideoLayout
        private lateinit var mSurface: SurfaceView
        private lateinit var holder: SurfaceHolder

        init { create() }

        override fun create() {
            super.create()

            val args: ArrayList<String> = ArrayList()
//            args.add("--rtsp-tcp")
//            args.add("--vout=android-display")
//            args.add("-vvv")
//            args.add("--http-reconnect");
//            args.add("--no-audio")
              args.add("--network-caching=800");
            val frame = findViewById<FrameLayout>(R.id.popup_frame)
            libVlc = LibVLC(context,args)
            mediaPlayer = MediaPlayer(libVlc)

            Log.e("VLC","using " + media.width + " x " + media.height)

           // mSurface = SurfaceView(context)
           //holder = mSurface.holder;

            mSurface = findViewById<SurfaceView>(R.id.surface);
            holder = mSurface.getHolder();
            holder.setFixedSize(media.width,  media.height)

            // set display size
            val lp  = mSurface.layoutParams //  WindowManager.LayoutParams()
            lp.width = media.width
            lp.height = media.height
            mSurface.layoutParams = lp
            mSurface.invalidate()

            val vout = mediaPlayer.vlcVout
            vout.setVideoView(mSurface)
            vout.setWindowSize(media.width,media.height);
            vout.attachViews()

            //videoLayout = org.videolan.libvlc.util.VLCVideoLayout(context)
            //mediaPlayer.attachViews(videoLayout, null, false, false)

            //frame.addView(mSurface, lp)

            val media = Media(libVlc, Uri.parse(media.uri))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=800");
         //   media.addOption(":clock-jitter=0");
         //   media.addOption(":clock-synchro=0");
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            Log.e("VLC","playback started")
        }

        override fun destroy() {
            try {
                mediaPlayer.stop()
                mediaPlayer.detachViews()
                mediaPlayer.release()
                libVlc.release()
            } catch(e: Throwable) {}
        }
    }


    companion object {
        const val LOG_TAG = "PopupView"

        fun build(context: Context, popup: PopupProps): PopupView
        {
            return when (popup.media) {
                is PopupProps.Media.Web -> Web(context, popup, popup.media)
                is PopupProps.Media.Video -> Video(context, popup, popup.media)
                is PopupProps.Media.Image -> Image(context, popup, popup.media)
                is PopupProps.Media.Bitmap -> Bitmap(context, popup, popup.media)
                is PopupProps.Media.VLC -> VLC(context,popup,popup.media)
                else -> Default(context, popup)
            }
        }
    }
}