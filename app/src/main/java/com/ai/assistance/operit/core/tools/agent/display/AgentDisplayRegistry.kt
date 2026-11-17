package com.ai.assistance.operit.core.tools.agent.display

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** 一块屏的状态，供 UI 与任务队列读取。 */
data class AgentScreenState(
        val displayId: Int,
        val width: Int,
        val height: Int,
        val ownerTaskId: String?,
        val busyPackage: String? = null,
)

/**
 * 后台屏注册表：多任务各占一块屏，互不干扰。
 *
 * Mika 用 `agent/session` 维度分屏；Operit 用"任务 id"维度，因为 Operit 目前
 * 没有任务概念，先用 chatId + 可选的子代理 id 作键。
 */
object AgentDisplayRegistry {

    private const val TAG = "AgentDisplayRegistry"

    private val screens = ConcurrentHashMap<String, AgentDisplay>()
    private val owners = ConcurrentHashMap<String, String>()

    private val _all = MutableStateFlow<List<AgentScreenState>>(emptyList())
    val all: StateFlow<List<AgentScreenState>> = _all.asStateFlow()

    /** 屏幕数上限，避免异常泄漏把显存吃光 */
    private const val MAX_SCREENS = 8

    @Synchronized
    fun create(key: String, context: Context, ownerTaskId: String? = null): AgentDisplay? {
        screens[key]?.let { return it }
        if (screens.size >= MAX_SCREENS) {
            AppLogger.w(TAG, "refuse to create screen, limit=$MAX_SCREENS")
            return null
        }
        val display = try {
            createAgentDisplayForScreen(context)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "create screen failed for key=$key", t)
            return null
        }
        screens[key] = display
        owners[key] = ownerTaskId ?: ""
        publish()
        return display
    }

    fun get(key: String): AgentDisplay? = screens[key]

    fun findByDisplayId(displayId: Int): AgentDisplay? =
            screens.values.firstOrNull { it.displayId == displayId }

    @Synchronized
    fun markBusy(key: String, packageName: String?) {
        owners[key] = packageName ?: ""
        publish()
    }

    @Synchronized
    fun release(key: String) {
        val removed = screens.remove(key) ?: return
        owners.remove(key)
        runCatching { removed.release() }
        publish()
    }

    @Synchronized
    fun releaseAll() {
        val allScreens = screens.values.toList()
        screens.clear()
        owners.clear()
        allScreens.forEach { runCatching { it.release() } }
        publish()
    }

    fun snapshot(): List<AgentScreenState> =
            screens.entries.map { (key, d) ->
                AgentScreenState(
                        displayId = d.displayId,
                        width = d.width,
                        height = d.height,
                        ownerTaskId = owners[key]?.takeIf { it.isNotBlank() }
                )
            }

    private fun publish() {
        _all.value = snapshot()
    }
}