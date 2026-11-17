package com.ai.assistance.operit.core.tools.agent.display

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/**
 * 单个后台虚拟屏。
 *
 * 画面输出到本进程的 [ImageReader]，截图不依赖无障碍接口。
 * 帧处理限速 ≤10 帧/秒：来帧时若距上次处理不足 100ms 就延后到期再取最新帧，
 * 期间旧帧由 [ImageReader.acquireLatestImage] 丢弃。
 *
 * 几何与取帧逻辑来自 Mika `cap/phone/display/AgentDisplay.kt`（Apache-2.0，wangzan101）。
 * 建屏方式改为 Operit 双路：优先特权 shell（`am`/`display` 相关），失败退回应用权限。
 */
class AgentDisplay private constructor(
        private val context: Context,
        private val bufferWidth: Int,
        private val bufferHeight: Int,
        private val dpi: Int,
) {

    private val thread = HandlerThread("agent-display").apply { start() }
    private val handler = Handler(thread.looper)
    private val reader =
            ImageReader.newInstance(bufferWidth, bufferHeight, PixelFormat.RGBA_8888, 2)

    /** 应用权限建的屏用这个；特权路径建的屏回写真实 id */
    private var virtualDisplay: VirtualDisplay? = null

    val displayId: Int

    private val displayManager =
            context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    /**
     * ImageReader 始终是建屏时的物理尺寸；应用的逻辑坐标随虚拟屏旋转，
     * 与主屏的方向无关。
     */
    private val geometry: DisplayGeometry
        get() = DisplayGeometry(
                bufferWidth,
                bufferHeight,
                displayManager.getDisplay(displayId)?.rotation ?: Surface.ROTATION_0
        )

    val width: Int get() = geometry.width
    val height: Int get() = geometry.height

    private val _frames = MutableStateFlow<Bitmap?>(null)
    /** 实时画面：限速 ≤10 帧/秒，缩到短边 ≤360。 */
    val frames: StateFlow<Bitmap?> = _frames.asStateFlow()

    private val frameLock = Any()
    private var fullFrame: Bitmap? = null // 带行填充的整帧，宽 = rowStride / pixelStride
    private var hasFrame = false
    private var frameGeometry =
            DisplayGeometry(bufferWidth, bufferHeight, Surface.ROTATION_0)
    private var lastProcessedAt = 0L
    private var pending = false

    /** 虚拟屏生产者的单调时钟纳秒时间戳，与整帧和缩略帧一起在 frameLock 下发布。 */
    private var frameTimestampNanos = 0L

    @Volatile private var released = false

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val process = Runnable {
        pending = false
        lastProcessedAt = SystemClock.uptimeMillis()
        processLatest()
    }

    init {
        val id = try {
            createDisplay()
        } catch (t: Throwable) {
            closeLocal()
            throw t
        }
        if (id == null || id < 0) {
            closeLocal()
            throw IllegalStateException("createVirtualDisplay failed ($id)")
        }
        displayId = id
        reader.setOnImageAvailableListener({ schedule() }, handler)
        // 建屏期间可能已经有帧排进来；先有正确的屏号，再处理这些帧。
        handler.post { schedule() }
        AppLogger.i(
                TAG,
                "created display id=$displayId size=${bufferWidth}x$bufferHeight dpi=$dpi"
        )
    }

    /**
     * 建屏。Operit 已有两条可用的特权通道（Shizuku / Root，见
     * [com.ai.assistance.operit.core.tools.system.shell.ShellExecutorFactory]），
     * 但 shell 无法直接替应用创建 VirtualDisplay，所以这里统一走应用权限的
     * [DisplayManager.createVirtualDisplay]——它本身不需要特殊权限即可建屏并投 Surface。
     */
    private fun createDisplay(): Int? {
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        val vd = displayManager.createVirtualDisplay(
                "OperitAgentDisplay",
                bufferWidth,
                bufferHeight,
                dpi,
                reader.surface,
                flags
        ) ?: return null
        virtualDisplay = vd
        return vd.display?.displayId
    }

    private fun schedule() {
        if (pending || released) return
        pending = true
        val wait = lastProcessedAt + FRAME_INTERVAL_MS - SystemClock.uptimeMillis()
        if (wait > 0) handler.postDelayed(process, wait) else handler.post(process)
    }

    private fun processLatest() {
        if (released) return
        val image: Image = try {
            reader.acquireLatestImage()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "acquireLatestImage failed: ${t.javaClass.simpleName}", t)
            null
        } ?: return
        try {
            val plane = image.planes[0]
            val strideWidth = plane.rowStride / plane.pixelStride
            val capturedGeometry = geometry
            synchronized(frameLock) {
                var bmp = fullFrame
                if (bmp == null || bmp.width != strideWidth) {
                    bmp?.recycle()
                    bmp = Bitmap.createBitmap(
                            strideWidth,
                            bufferHeight,
                            Bitmap.Config.ARGB_8888
                    )
                    fullFrame = bmp
                }
                bmp.copyPixelsFromBuffer(plane.buffer.rewind())
                hasFrame = true
                // 限速或线程调度会延后消费，不能把复制位图的时刻记成帧时刻。
                frameTimestampNanos = image.timestamp
                frameGeometry = capturedGeometry
                _frames.value = uprightDisplayFrame(
                        bmp,
                        capturedGeometry,
                        capturedGeometry.previewWidth(PREVIEW_SHORT_SIDE),
                        paint
                )
            }
        } finally {
            image.close()
        }
    }

    /** 最新一帧的整幅 PNG；还没收到画面时返回 null。 */
    fun latestFramePng(): ByteArray? {
        val crop = latestFrameBitmap() ?: return null
        return try {
            ByteArrayOutputStream().also {
                crop.compress(Bitmap.CompressFormat.PNG, 100, it)
            }.toByteArray()
        } finally {
            crop.recycle()
        }
    }

    /** 最新已发布帧的单调时钟纳秒时间戳。 */
    fun lastFrameAt(): Long? = synchronized(frameLock) {
        frameTimestampNanos.takeIf { !released && it > 0 }
    }

    /** OCR 使用原始尺寸的画面副本，避免 PNG 编解码；调用方负责 recycle。 */
    fun latestFrameBitmap(): Bitmap? = synchronized(frameLock) {
        val bmp = fullFrame
        if (!hasFrame || bmp == null) null
        else uprightDisplayFrame(bmp, frameGeometry, paint = paint)
    }

    fun release() {
        if (released) return
        released = true
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        closeLocal()
        _frames.value = null
        AppLogger.d(TAG, "released display id=$displayId")
    }

    private fun closeLocal() {
        released = true
        handler.removeCallbacksAndMessages(null)
        // 在帧线程上收尾，避免与正在处理的帧并发
        handler.post {
            runCatching { reader.close() }
            synchronized(frameLock) {
                fullFrame?.recycle()
                fullFrame = null
                hasFrame = false
            }
        }
        thread.quitSafely()
    }

    companion object {
        private const val TAG = "AgentDisplay"
        private const val PREVIEW_SHORT_SIDE = 360
        private const val FRAME_INTERVAL_MS = 100L

        /**
         * 按给定尺寸新建一块屏。调用方负责在不再使用时调 [AgentDisplay.release]。
         */
        fun create(
                context: Context,
                width: Int,
                height: Int,
                dpi: Int
        ): AgentDisplay = AgentDisplay(context.applicationContext, width, height, dpi)
    }
}

/** 按主屏真实尺寸建屏的便捷入口。 */
fun createAgentDisplayForScreen(context: Context): AgentDisplay {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    @Suppress("DEPRECATION")
    wm.defaultDisplay.getRealMetrics(metrics)
    val statusBar = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    val statusBarPx = if (statusBar > 0) context.resources.getDimensionPixelSize(statusBar) else 0
    return AgentDisplay.create(
            context,
            metrics.widthPixels,
            (metrics.heightPixels - statusBarPx).coerceAtLeast(1),
            metrics.densityDpi
    )
}