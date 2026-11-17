package com.ai.assistance.operit.core.zfx

import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ZfxRuntime"

/**
 * 回合级运行时动作判定。
 *
 * 上游 index.js 用 `agent/status` 事件 + `agent.followup(...)` 实现三件事：
 *  1. 拒绝恢复（refusal-recover）
 *  2. 截断续命（auto-continue）
 *  3. 唤醒确认（wake-confirm）
 *
 * Operit 没有等价的事件总线，改用"读取回合结束时的历史"来判定——
 * 由 [com.ai.assistance.operit.services.core.MessageProcessingDelegate] 在
 * `sendUserMessage` 收尾处调用。
 */
object ZfxRuntime {

    /** 已经发起过补发的回合，防止同轮重复触发 */
    private val handledTurns = ConcurrentHashMap<String, Long>()

    data class TurnSnapshot(
            val chatId: String,
            /** 回合标识：用最后一条用户消息的时间戳，天然每轮不同 */
            val turnKey: Long,
            /** 最后一个回合的 AI 文本 */
            val lastAssistantText: String,
            /** 最后一个回合的用户文本 */
            val lastUserText: String,
            /** 是否为本轮最后一条消息是 AI（否则是工具结果，不该补发） */
            val endsWithAssistant: Boolean
    )

    enum class Action { NONE, RECOVER, CONTINUE, WAKE }

    /**
     * 判定这一轮结束后要不要补发。
     * 优先级：唤醒 > 截断续命 > 拒绝恢复（唤醒是用户显式意图，最先判）。
     */
    fun decide(snapshot: TurnSnapshot): Action {
        if (!ZfxEngine.enabled.value) return Action.NONE
        if (handledTurns[snapshot.chatId] == snapshot.turnKey) return Action.NONE
        if (!snapshot.endsWithAssistant) return Action.NONE
        if (snapshot.lastAssistantText.trim().length < 8) return Action.NONE

        // 唤醒：用户只发了唤醒词
        if (ZfxEngine.shouldConfirmWake(snapshot.chatId, snapshot.turnKey, snapshot.lastUserText)) {
            handledTurns[snapshot.chatId] = snapshot.turnKey
            return Action.WAKE
        }

        // 截断续命：文本长度接近上限且没有正常收尾（这里用"极长但无结束标点"近似）
        if (isTruncated(snapshot.lastAssistantText) &&
                ZfxEngine.canContinue(snapshot.chatId, snapshot.turnKey)) {
            handledTurns[snapshot.chatId] = snapshot.turnKey
            return Action.CONTINUE
        }

        // 拒绝恢复
        if (ZfxEngine.isRefusal(snapshot.lastAssistantText) &&
                ZfxEngine.canRecover(snapshot.chatId, snapshot.turnKey)) {
            handledTurns[snapshot.chatId] = snapshot.turnKey
            return Action.RECOVER
        }

        return Action.NONE
    }

    fun actionText(action: Action): String = when (action) {
        Action.WAKE -> ZfxConfig.DEFAULT_WAKE_HINT
        Action.CONTINUE -> ZfxConfig.DEFAULT_CONTINUE_TEXT
        Action.RECOVER -> ZfxConfig.DEFAULT_RECOVER_TEXT
        Action.NONE -> ""
    }

    /**
     * 截断判定：上游看 `turn/end.reason.kind ∈ {length, max-tokens, content-filter}`。
     * Operit 侧没有该字段，用启发式：长度超过 8000 且结尾不是句读/代码收尾。
     */
    private fun isTruncated(text: String): Boolean {
        if (text.length < 8000) return false
        val tail = text.trimEnd().takeLast(2)
        return !tail.any { it in "。！？.!?)]}\"'" }
    }

    /** 记录一次补发（便于状态页统计）。 */
    fun recordAction(action: Action) {
        if (action == Action.NONE) return
        AppLogger.i(TAG, "action=$action")
    }

    /** 清理过期标记，避免表无限增长。 */
    fun prune(keepChatIds: Set<String>) {
        val it = handledTurns.keys.iterator()
        while (it.hasNext()) {
            if (it.next() !in keepChatIds) it.remove()
        }
    }
}