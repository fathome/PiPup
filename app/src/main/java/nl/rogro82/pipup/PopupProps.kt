package nl.rogro82.pipup

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class PopupProps(
    val duration: Int = DEFAULT_DURATION,
    val position: Position = DEFAULT_POSITION,
    val backgroundColor: String = DEFAULT_BACKGROUND_COLOR,
    val title: String? = null,
    val titleSize: Float = 14f,
    val titleColor: String = DEFAULT_TITLE_COLOR,
    val message: String? = null,
    val messageSize: Float = DEFAULT_MESSAGE_SIZE,
    val messageColor: String = DEFAULT_MESSAGE_COLOR,
    val media: List<Media>,
    val notificationSound: Boolean = false
) {
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.WRAPPER_OBJECT)
    @JsonSubTypes(
        JsonSubTypes.Type(Media.Video::class, name = "video"),
        JsonSubTypes.Type(Media.Image::class, name = "image"),
        JsonSubTypes.Type(Media.Web::class, name = "web"),
        JsonSubTypes.Type(Media.VLC::class, name = "vlc"),
        JsonSubTypes.Type(Media.EXO::class, name = "exo")

    )
    sealed class Media {
        data class Video(val uri: String, val width: Int = DEFAULT_MEDIA_WIDTH): Media()
        data class Image(val uri: String, val width: Int = DEFAULT_MEDIA_WIDTH): Media()
        data class Web(val uri: String, val width: Int = 640, val height: Int = 480): Media()
        data class Bitmap(val image: android.graphics.Bitmap, val width: Int = DEFAULT_MEDIA_WIDTH): Media()
        data class VLC(val uri: String, val width: Int = 640, val height: Int = 480): Media()
        data class EXO(val uri: String, val width: Int = 640, val height: Int = 480): Media()
    }

    enum class Position(index: Int) {
        TopRight(0),
        TopLeft(1),
        BottomRight(2),
        BottomLeft(3),
        Center(4);

        companion object {
            fun getByValue(value: Int) = Position.values().firstOrNull() { it.ordinal == value }
        }
    }




    companion object {
        const val DEFAULT_DURATION: Int = 30
        const val DEFAULT_BACKGROUND_COLOR = "#CC000000"
        const val DEFAULT_TITLE_SIZE = 16f
        const val DEFAULT_TITLE_COLOR = "#ffffff"
        const val DEFAULT_MESSAGE_SIZE = 12f
        const val DEFAULT_MESSAGE_COLOR = "#ffffff"
        const val DEFAULT_MEDIA_WIDTH = 480

        val DEFAULT_POSITION: Position = Position.TopRight
    }
}