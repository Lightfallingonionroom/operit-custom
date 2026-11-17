package com.ai.assistance.operit.core.tools.agent.display

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * 系统把逻辑画面顺时针旋转到固定的物理缓冲；反向转回来，
 * 让预览、PNG 和 OCR 都用应用的逻辑坐标。
 *
 * 来源：Mika `cap/phone/display/DisplayFrame.kt`（Apache-2.0，wangzan101）。
 *
 * 直接画到目标尺寸，不为每张预览另建一张全尺寸中间图；
 * 源矩形不含 ImageReader 的行填充。
 */
internal fun uprightDisplayFrame(
        buffer: Bitmap,
        geometry: DisplayGeometry,
        maxWidth: Int = geometry.width,
        paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG),
): Bitmap {
    val (width, height) = geometry.previewSize(maxWidth)
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(result).apply {
        scale(width.toFloat() / geometry.width, height.toFloat() / geometry.height)
        when (geometry.rotation) {
            1 -> {
                translate(0f, geometry.bufferWidth.toFloat()); rotate(-90f)
            }
            2 -> {
                translate(
                        geometry.bufferWidth.toFloat(),
                        geometry.bufferHeight.toFloat()
                ); rotate(180f)
            }
            3 -> {
                translate(geometry.bufferHeight.toFloat(), 0f); rotate(90f)
            }
        }
        val content = Rect(0, 0, geometry.bufferWidth, geometry.bufferHeight)
        drawBitmap(buffer, content, content, paint)
    }
    return result
}

/**
 * 物理缓冲固定，逻辑屏在四分之一圈时交换宽高。
 * 来源：Mika `cap/phone/display/DisplayFrameGeometry.kt`（Apache-2.0，wangzan101）。
 *
 * rotation 取值：ROTATION_0/90/180/270 分别是 0/1/2/3。
 */
data class DisplayGeometry(
        val bufferWidth: Int,
        val bufferHeight: Int,
        val rotation: Int
) {
    private val sideways: Boolean get() = rotation == 1 || rotation == 3

    val width: Int get() = if (sideways) bufferHeight else bufferWidth
    val height: Int get() = if (sideways) bufferWidth else bufferHeight

    /** 按短边限制预览：横竖屏保持相近的像素量，避免横屏只剩一条低分辨率画面。 */
    fun previewWidth(shortSide: Int): Int =
            if (width > height) maxOf(1, (width.toLong() * shortSide / height).toInt())
            else shortSide

    fun previewSize(maxWidth: Int): Pair<Int, Int> {
        val w = minOf(maxWidth, width)
        return w to maxOf(1, (height.toLong() * w / width).toInt())
    }
}