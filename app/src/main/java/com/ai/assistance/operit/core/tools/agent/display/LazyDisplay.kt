package com.ai.assistance.operit.core.tools.agent.display

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 用到时才建的虚拟屏。
 *
 * 来自 Mika `cap/phone/display/LazyDisplay.kt`（Apache-2.0, wangzan101）。
 *
 * 行为约定：
 * - 任务第一次真正操作手机（打开 App / 点击 / 输入 / 截图）时才建屏；
 * - 纯对话任务全程不建屏，不占用任何显示资源；
 * - 建成功后一直复用同一块屏；
 * - 建失败抛出异常，下次 [get] 会重试（不缓存失败态）。
 */
class LazyDisplay<T : Any>(
        val width: Int,
        val height: Int,
        private val create: suspend () -> T
) {
    private val lock = Mutex()

    @Volatile private var made: T? = null

    /** 已经建好的屏；还没建时为 null。不会触发建屏。 */
    val current: T? get() = made

    /** 是否已建。 */
    val isCreated: Boolean get() = made != null

    /** 取屏，第一次调用时才建。 */
    suspend fun get(): T =
            made ?: lock.withLock {
                made ?: create().also { made = it }
            }

    /** 释放。由调用方负责先关掉底层屏。 */
    fun forget() {
        made = null
    }
}
