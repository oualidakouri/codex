package fr.maisonos.diagnostic

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.media.FaceDetector
import androidx.camera.core.ImageProxy
import fr.maisonos.core.FaceGaze

/** Android's bundled frontal-face detector: offline, no model download, identity or retained frames. */
internal class FaceTracker {
    private var detector: FaceDetector? = null
    private var detectorWidth = 0
    private var detectorHeight = 0
    private val faces = arrayOfNulls<FaceDetector.Face>(3)
    fun detect(image: ImageProxy): FaceGaze {
        val crop = image.cropRect
        // Bound both conversion and detector cost even if CameraX selects a larger sensor stream.
        val scale = maxOf(1, (maxOf(crop.width(),crop.height()) + 319) / 320)
        val width = (crop.width()/scale).coerceAtLeast(2) and -2
        val height = (crop.height()/scale).coerceAtLeast(2) and -2
        val plane = image.planes[0]; val buffer = plane.buffer
        val pixels = IntArray(width*height) { i ->
            val x = crop.left + (i % width)*scale
            val y = crop.top + (i / width)*scale
            val value = buffer.get(buffer.position() + y*plane.rowStride + x*plane.pixelStride).toInt() and 255
            -0x1000000 or (value shl 16) or (value shl 8) or value
        }
        val source = Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565)
        var upright: Bitmap? = null
        try {
            source.setPixels(pixels,0,width,0,0,width,height)
            upright = if (image.imageInfo.rotationDegrees == 0) source else Bitmap.createBitmap(source,0,0,width,height,
                Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) },false)
            if (detectorWidth != upright.width || detectorHeight != upright.height) {
                detector = FaceDetector(upright.width,upright.height,3)
                detectorWidth = upright.width; detectorHeight = upright.height
            }
            faces.fill(null)
            detector!!.findFaces(upright,faces)
            val face = faces.filterNotNull().filter { it.confidence() >= FaceDetector.Face.CONFIDENCE_THRESHOLD }.maxByOrNull { it.eyesDistance() } ?: return FaceGaze()
            val center = PointF();face.getMidPoint(center)
            return FaceGaze.fromCamera(center.x/upright.width,center.y/upright.height)
        } finally { if (upright !== source) upright?.recycle(); source.recycle() }
    }
}
