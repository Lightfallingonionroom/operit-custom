package com.ai.assistance.operit.core.tools.agent

import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * 手机操作的会话绑定。
 *
 * ## 要解决的问题
 *
 * 实测日志（后台屏测试）里出现了这条致命链：
 *
 * ```
 * start_background_screen → agent_id=bg-1794966378097, displayId=12
 * Automatic_ui_base:app_launch → 报 "launched on bg display 10"
 * 后续所有操作 → 继续用 display 10
 * ```
 *
 * 模型拿不到稳定的「这块屏」句柄，只能自己在 agentId / displayId / 各种工具包之间
 * 反复试错，最终 84 次调用、553 万输入 token、12 分钟。
 *
 * Mika 的做法是任务持有 `AndroidScreen`，屏由 `LazyAgentDisplay` 懒建并长期复用，
 * 模型完全不接触 displayId。这里做等价的事：
 *
 * - 一个 chatId 绑定一个 agentId
 * - agentId 绑定一个 displayId
 * - 工具执行时自动解析，模型不用传、也传不错
 *
 * ## 设计约束
 *
 * - 不做建屏动作，只做绑定登记；建屏仍然由 ShowerController 负责
 * - 线程安全（多个工具可能并发进来）
 * - 提供显式解绑，任务结束释放
 */
object PhoneSession {

    private const val TAG = "PhoneSession"

    /** chatId -> agentId */
    private val chatBindings = ConcurrentHashMap<String, String>()

    /** agentId -> displayId（建屏后登记） */
    private val displayBindings = ConcurrentHashMap<String, Int>()

    /** 最近一次使用的 chatId，用于工具没有显式 chatId 时的兜底 */
    @Volatile
    private var lastChatId: String? = null

    /**
     * 为一个会话分配（或复用）agentId。
     *
     * 同一 chatId 永远拿到同一个 agentId —— 这是「一块屏贯穿整个任务」的实现基础。
     */
    fun agentIdFor(chatId: String?): String {
        val key = chatId?.takeIf { it.isNotBlank() }
        if (key == null) {
            // 没有 chatId 时退回到最近使用的会话，再退回到固定默认值。
            // 固定默认值保证「同一个任务里所有工具落到同一块屏」。
            return lastChatId?.let { chatBindings[it] } ?: DEFAULT_AGENT
        }
        lastChatId = key
        return chatBindings.getOrPut(key) { "phone-$key" }
    }

    /** 显式绑定（面板或工具指定 agent_id 时）。 */
    fun bindAgent(chatId: String, agentId: String) {
        if (chatId.isBlank() || agentId.isBlank()) return
        lastChatId = chatId
        chatBindings[chatId] = agentId
        AppLogger.i(TAG, "bindAgent chat=$chatId agent=$agentId")
    }

    /** 建屏成功后登记 displayId，后续工具直接复用，不再让模型猜。 */
    fun bindDisplay(agentId: String, displayId: Int) {
        displayBindings[agentId] = displayId
        AppLogger.i(TAG, "bindDisplay agent=$agentId display=$displayId")
    }

    fun displayOf(agentId: String): Int? = displayBindings[agentId]

    /** 会话对应的 displayId（若已建屏）。 */
    fun displayFor(chatId: String?): Int? = displayOf(agentIdFor(chatId))

    /** 任务结束：释放绑定，并关掉那块屏。 */
    fun release(chatId: String) {
        val agentId = chatBindings.remove(chatId) ?: return
        displayBindings.remove(agentId)
        try {
            ShowerController.shutdown(agentId)
        } catch (_: Throwable) {
        }
        if (lastChatId == chatId) lastChatId = null
        AppLogger.i(TAG, "release chat=$chatId agent=$agentId")
    }

    /** 当前是否已经有一块可用的屏。 */
    fun hasDisplay(chatId: String?): Boolean {
        val agentId = agentIdFor(chatId)
        return ShowerController.getDisplayId(agentId) != null
    }

    /**
     * 解析本次工具调用应该用哪个 agentId。
     *
     * 优先级：
     *  1. 工具显式传了 agent_id 且不是 "default" → 用它（并登记到会话）
     *  2. 工具显式传了 agent_id="default" → 回前台，返回 null
     *  3. 都没传 → 用会话绑定（默认后台屏）
     */
    fun resolveAgentId(chatId: String?, explicitAgentId: String?): String? {
        val explicit = explicitAgentId?.trim()
        if (explicit != null && explicit.equals("default", ignoreCase = true)) return null
        if (!explicit.isNullOrBlank()) {
            chatId?.takeIf { it.isNotBlank() }?.let { bindAgent(it, explicit) }
            return explicit
        }
        return agentIdFor(chatId)
    }

    /** 固定默认 agentId，保证无 chatId 场景也不会每次新建屏。 */
    const val DEFAULT_AGENT = "phone-default"

    // ---------- 后台屏任务进行中标记 ----------

    /**
     * 哪些会话正在跑后台屏任务。
     *
     * 用途：后台屏任务期间要挡住「自己摸索」的路径——实测那次 84 次调用里，
     * 大量是 shell / uiautomator / grep_code 扫 XML，把 553 万 token 全烧了。
     * 手机操作有自己的工具，不需要模型自己去挖界面结构。
     */
    private val activeTasks = ConcurrentHashMap.newKeySet<String>()

    fun markTaskActive(chatId: String?) {
        chatId?.takeIf { it.isNotBlank() }?.let { activeTasks.add(it) }
    }

    fun markTaskIdle(chatId: String?) {
        chatId?.takeIf { it.isNotBlank() }?.let { activeTasks.remove(it) }
    }

    /** 该会话是否正在跑后台屏任务。 */
    fun isTaskActive(chatId: String?): Boolean {
        val key = chatId?.takeIf { it.isNotBlank() } ?: lastChatId ?: return false
        return activeTasks.contains(key)
    }

    /**
     * 该会话是否应该屏蔽某个摸索型工具。
     *
     * 只挡「为了摸界面而滥用」的工具，不挡真正需要执行的能力：
     * shell / terminal 仍然可用（安装、启动、系统命令都靠它），
     * 但 grep_code 扫 XML、uiautomator dump 这类纯摸索动作会被挡。
     */
    fun shouldBlockExploration(chatId: String?, toolName: String): Boolean {
        if (!isTaskActive(chatId)) return false
        return when (toolName) {
            "grep_code", "grep_context" -> true
            "Automatic_ui_base:get_page_info",
            "Automatic_ui_base:click_element",
            "Automatic_ui_base:tap",
            "Automatic_ui_base:swipe",
            "Automatic_ui_base:set_input_text",
            "Automatic_ui_base:long_press",
            "Automatic_ui_base:double_tap",
            "Automatic_ui_base:press_key" -> true
            else -> false
        }
    }

    /** 屏蔽时返回给模型的说明，让它知道该走哪条路。 */
    fun blockedMessage(toolName: String): String =
            "工具 $toolName 在后台屏任务期间不可用。手机操作请直接用 " +
                    "tap / swipe / set_input_text / capture_screenshot / start_app，" +
                    "它们已经绑定到本会话的虚拟屏，不需要 agent_id。"


    /** 清空全部（进程退出/测试用）。 */
    fun clearAll() {
        chatBindings.keys.toList().forEach { release(it) }
        displayBindings.clear()
        lastChatId = null
    }
}