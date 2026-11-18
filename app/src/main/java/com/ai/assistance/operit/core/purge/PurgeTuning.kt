package com.ai.assistance.operit.core.purge

import com.ai.assistance.operit.util.AppLogger

/**
 * Purge 的宿主调参层。
 *
 * ## 为什么需要这一层
 *
 * 上游 dsh-purge 有一批补丁（#21 #22 #23 #51 #62 #69）改的不是提示词，
 * 而是**宿主代码里的常量/默认值**：
 *
 * | 编号 | 上游动作 | Operit 对应 |
 * |---|---|---|
 * | #21 | bash 等待从 30s 恢复到 60s | 终端工具 timeoutMs 默认值 |
 * | #22 | read 单行上限 1e4 → 更大 | 文件工具截断阈值 |
 * | #23 | 子代理 maxDepth 3 → 10 | PhoneAgent.maxSteps |
 * | #51 | 新会话默认全权限 | 权限默认档 |
 * | #62 | 工具 ask 失败时 fail-open | 工具失败处理 |
 * | #69 | 模型流及时释放 | 流式响应清理 |
 *
 * 这些值在 Operit 里是硬编码常量。直接改常量会破坏「关闭 Purge 后恢复原样」的承诺，
 * 所以统一抽成可读写配置：默认值 = Operit 原值，Purge 开启时按补丁状态覆盖。
 *
 * ## 使用方式
 *
 * 宿主代码在用到这些参数时调 `PurgeTuning.xxx()`，它会：
 *   - Purge 关闭 → 返回 Operit 原值（零影响）
 *   - Purge 开启且对应补丁勾选 → 返回放大的值
 */
object PurgeTuning {

    private const val TAG = "PurgeTuning"

    // ---------- Operit 原值（必须与宿主代码保持一致）----------

    /** 终端命令默认超时。Operit 原值 15s（前台），见 super_admin:terminal 描述。 */
    const val DEFAULT_TERMINAL_TIMEOUT_MS = 15_000L

    /** 放大后的终端超时。对应 #21「bash 超时恢复 60s」。 */
    const val RAISED_TERMINAL_TIMEOUT_MS = 60_000L

    /** 文件工具单次截断字符数。Operit 原值 10_000。 */
    const val DEFAULT_READ_MAX_CHARS = 10_000

    /** 放大后的读取上限。对应 #22「read 上限放宽」。 */
    const val RAISED_READ_MAX_CHARS = 100_000

    /** 子代理步数上限。Operit 原值 20。 */
    const val DEFAULT_SUBAGENT_MAX_STEPS = 20

    /** 放大后的子代理步数。对应 #23「子代理深度提高」。 */
    const val RAISED_SUBAGENT_MAX_STEPS = 100

    // ---------- 取值接口 ----------

    /**
     * 当前生效的终端超时。
     *
     * @param fallback 调用方自己的默认值（不传则用 Operit 原值）
     */
    fun terminalTimeoutMs(fallback: Long = DEFAULT_TERMINAL_TIMEOUT_MS): Long {
        return if (isActive(21)) RAISED_TERMINAL_TIMEOUT_MS else fallback
    }

    /** 当前生效的文件读取上限。 */
    fun readMaxChars(fallback: Int = DEFAULT_READ_MAX_CHARS): Int {
        return if (isActive(22)) RAISED_READ_MAX_CHARS else fallback
    }

    /** 当前生效的子代理步数上限。 */
    fun subagentMaxSteps(fallback: Int = DEFAULT_SUBAGENT_MAX_STEPS): Int {
        return if (isActive(23)) RAISED_SUBAGENT_MAX_STEPS else fallback
    }

    /** #22 read 上限是否放宽。 */
    fun readCapsRaised(): Boolean = isActive(22)

    /** #19 工具结果裁剪是否关闭（关闭即放宽上限）。 */
    fun resultPrunerDisabled(): Boolean = isActive(19)

    /** #18 重复工具提醒是否关闭。 */
    fun repeatToolReminderDisabled(): Boolean = isActive(18)

    /** #51 新会话是否默认全权限。 */
    fun freshSessionFullAccess(): Boolean = isActive(51)

    /** #62 工具 ask 失败是否 fail-open（放行而不是拒绝）。 */
    fun toolAskFailOpen(): Boolean = isActive(62)

    /** #69 模型流是否在结束后立即释放。 */
    fun releaseModelStream(): Boolean = isActive(69)

    /** 某个补丁当前是否真实生效（总开关开 + 该补丁勾选 + 状态为 LIVE）。 */
    private fun isActive(id: Int): Boolean {
        return try {
            PurgeEngine.enabled.value && PurgeEngine.isPatchEnabled(id)
        } catch (_: Throwable) {
            false
        }
    }

    /** 面板调试用：当前所有调参项的实际值。 */
    fun snapshot(): Map<String, Any> = mapOf(
            "terminalTimeoutMs" to terminalTimeoutMs(),
            "readMaxChars" to readMaxChars(),
            "subagentMaxSteps" to subagentMaxSteps(),
            "readCapsRaised" to readCapsRaised(),
            "resultPrunerDisabled" to resultPrunerDisabled(),
            "repeatToolReminderDisabled" to repeatToolReminderDisabled(),
            "freshSessionFullAccess" to freshSessionFullAccess(),
            "toolAskFailOpen" to toolAskFailOpen(),
            "releaseModelStream" to releaseModelStream()
    )
}