package com.ai.assistance.operit.core.purge

/**
 * Purge 的宿主调参层。
 *
 * ## 为什么需要这一层
 *
 * 上游 dsh-purge 有一批补丁改的不是提示词，而是**宿主代码里的常量/默认值**。
 * 这些值在 Operit 里是硬编码，直接改常量会破坏「关闭 Purge 后恢复原样」的承诺，
 * 所以统一抽成可读写配置：默认值 = Operit 原值，Purge 开启且补丁勾选时才覆盖。
 *
 * ## 收录标准（硬性）
 *
 * 只有**在宿主代码里存在真实调用点**的补丁才允许出现在本文件。
 * 只定义 getter、没有任何地方调用的，一律视为假接入，必须改回 N_A。
 *
 * 这是上一版踩过的坑：曾有 5 个 getter（#18 #21 #51 #62 #69）定义了却零调用，
 * 面板上却显示成已生效。
 *
 * ## 当前收录
 *
 * | 编号 | 上游动作 | Operit 落点 | 调用点 |
 * |---|---|---|---|
 * | #19 | 工具结果裁剪关闭 | 结果后处理阈值 | ToolExecutionLimits.maxTextResultLength |
 * | #22 | read 上限放宽 | 文件工具截断阈值 | ToolExecutionLimits.maxFileReadBytes |
 * | #23 | 子代理深度提高 | PhoneAgent 步数上限 | PhoneAgent.AgentConfig.maxSteps |
 *
 * ## 已剔除（无宿主落点，见 PurgePatchCatalog 的 N_A 条目）
 *
 * - #18 重复工具提醒：Operit 无此机制
 * - #21 bash 超时恢复：纯迁移补丁，Operit 终端默认 30 分钟从未被插件改过
 * - #51 新会话全权限：Operit 权限是 DataStore 持久化策略，不由此层控制
 * - #62 工具 ask fail-open：Operit 无 ask 语义
 * - #69 模型流释放：Operit 流式响应由 provider 自身管理
 */
object PurgeTuning {

    /** 子代理步数上限。Operit 原值 20。 */
    const val DEFAULT_SUBAGENT_MAX_STEPS = 20

    /** 放大后的子代理步数。对应 #23「子代理深度提高」。 */
    const val RAISED_SUBAGENT_MAX_STEPS = 100

    /** #19 工具结果裁剪是否关闭（关闭即放宽上限）。 */
    fun resultPrunerDisabled(): Boolean = isActive(19)

    /** #22 read 上限是否放宽。 */
    fun readCapsRaised(): Boolean = isActive(22)

    /** 当前生效的子代理步数上限。对应 #23。 */
    fun subagentMaxSteps(fallback: Int = DEFAULT_SUBAGENT_MAX_STEPS): Int {
        return if (isActive(23)) RAISED_SUBAGENT_MAX_STEPS else fallback
    }

    /** 某个补丁当前是否真实生效（总开关开 + 该补丁勾选）。 */
    private fun isActive(id: Int): Boolean {
        return try {
            PurgeEngine.enabled.value && PurgeEngine.isPatchEnabled(id)
        } catch (_: Throwable) {
            false
        }
    }

    /** 面板调试用：当前所有调参项的实际值。 */
    fun snapshot(): Map<String, Any> = mapOf(
            "resultPrunerDisabled" to resultPrunerDisabled(),
            "readCapsRaised" to readCapsRaised(),
            "subagentMaxSteps" to subagentMaxSteps()
    )
}