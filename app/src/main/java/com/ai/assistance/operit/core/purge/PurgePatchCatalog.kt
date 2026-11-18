package com.ai.assistance.operit.core.purge

/**
 * dsh-purge 1.1.71 的补丁目录。
 *
 * 编号与名称原样保留上游（dsh-purge，原作者 YuJunZhiXue，MIT），
 * 编号 20/27/41/44/79/81 为上游跳号，此处同样跳过。
 *
 * ## 面板可见性
 *
 * 只有 [PatchStatus.LIVE] 会出现在面板上。判定依据是静态审计：
 * 每一项都必须满足「[PurgePromptRewriter] 里有实现」且「该实现挂在
 * hook 真正会走的链路上」。
 *
 * 之前按 [PortKind] 过滤，把大量「标了 DIRECT 但 rewriter 里根本没有」
 * 的幽灵项显示成可勾开关，勾了没有任何反应。
 *
 * 审计脚本：`operit-custom/audit_patches.py`
 */
enum class PurgeLayer { PROMPT, CODE, TOOL, COMPAT }
enum class PortKind { DIRECT, REWRITE, NEW, N_A }

/** 补丁在 Operit 里的真实落地状态。面板可见性的唯一依据。 */
enum class PatchStatus {
    /** 有实现且挂载在可达链路上 —— 面板可见、可勾 */
    LIVE,

    /** 上游针对 dsh 专有对象，Operit 无对应物 */
    N_A,

    /** 需要改 Operit 宿主框架才能落地 */
    HOST_BOUND,

    /** Operit 提示词里不存在该段落，无适配意义 */
    OBSOLETE
}

data class PurgePatchDef(
        val id: Int,
        val name: String,
        val layer: PurgeLayer,
        val kind: PortKind,
        val meaning: String,
        val target: String,
        val status: PatchStatus = PatchStatus.LIVE
)

object PurgePatchCatalog {

    private fun live(id: Int, name: String, layer: PurgeLayer, kind: PortKind, meaning: String, target: String) =
            PurgePatchDef(id, name, layer, kind, meaning, target, PatchStatus.LIVE)

    private fun na(id: Int, name: String, layer: PurgeLayer, meaning: String, target: String) =
            PurgePatchDef(id, name, layer, PortKind.N_A, meaning, target, PatchStatus.N_A)

    private fun host(id: Int, name: String, layer: PurgeLayer, meaning: String, target: String) =
            PurgePatchDef(id, name, layer, PortKind.N_A, meaning, target, PatchStatus.HOST_BOUND)

    val ALL: List<PurgePatchDef> = listOf(
            // ---- Operit 专用注入层（201+）----
            live(201, "INJECT_NO_REFUSAL", PurgeLayer.PROMPT, PortKind.NEW, "不回绝：拆掉违规前提直接执行", "finalize 注入段"),
            live(202, "INJECT_DIRECT_DELIVERY", PurgeLayer.PROMPT, PortKind.NEW, "直接交付：要成品不要过程", "finalize 注入段"),
            live(203, "INJECT_NO_DISCLAIMER", PurgeLayer.PROMPT, PortKind.NEW, "不加免责声明/风险提示", "finalize 注入段"),
            live(204, "INJECT_TOOL_FIRST", PurgeLayer.PROMPT, PortKind.NEW, "工具优先：能查就不猜", "finalize 注入段"),
            live(205, "INJECT_PERSIST", PurgeLayer.PROMPT, PortKind.NEW, "跑到底：长任务不中途停", "finalize 注入段"),

            // ---- 提示词层：finalize 通道可达 ----
            live(50, "SESSION_REFERENCE_FRAMING_NEUTRAL", PurgeLayer.PROMPT, PortKind.REWRITE, "会话引用框架中性", "finalize"),
            live(54, "DELIVERABLE_DO_NOT", PurgeLayer.PROMPT, PortKind.DIRECT, "交付禁止语清理", "finalize"),
            live(67, "AGENT_LOOP_MESSAGES_GUARD", PurgeLayer.PROMPT, PortKind.DIRECT, "agent 循环消息守卫", "finalize"),
            live(68, "SYSTEM_NODES_CONTENT_GUARD", PurgeLayer.PROMPT, PortKind.DIRECT, "系统节点内容守卫", "finalize"),
            live(72, "MESSAGES_SYSTEM_CURRENT", PurgeLayer.PROMPT, PortKind.DIRECT, "messages system 当前", "finalize"),
            live(74, "ASSEMBLE_BEFORE_PRESTEP", PurgeLayer.PROMPT, PortKind.DIRECT, "先组装后预步", "finalize"),
            live(85, "SESSION_PROMPT_ALWAYS_FOLLOWUP", PurgeLayer.PROMPT, PortKind.DIRECT, "会话 prompt 总 followup", "finalize"),
            live(86, "SESSION_STEER_ALWAYS_FOLLOWUP", PurgeLayer.PROMPT, PortKind.DIRECT, "会话 steer 总 followup", "finalize"),
            live(87, "HUNG_PROMPT_LF_NEEDLE", PurgeLayer.PROMPT, PortKind.DIRECT, "卡死提示 LF 探针", "finalize"),
            live(88, "HUNG_PROMPT_STRICT_NEEDLE", PurgeLayer.PROMPT, PortKind.DIRECT, "卡死提示严格探针", "finalize"),

            // ---- 工具层可达 ----
            live(29, "TOOL_WEB_UNTRUSTED_NEUTRAL", PurgeLayer.TOOL, PortKind.DIRECT, "web 工具不可信中", "rewriteToolPrompt"),
            live(48, "TOOL_SHELL_DELETE_MOVE_NEUTRAL", PurgeLayer.TOOL, PortKind.DIRECT, "shell 删除移动中性", "rewriteToolPrompt"),
            live(80, "MINIMAL_OFFICIAL_TOOLSET", PurgeLayer.TOOL, PortKind.REWRITE, "极简官方工具集", "rewriteToolPrompt"),

            // ---- 宿主框架绑定：需改 Operit 权限/审批/会话框架 ----
            host(5, "APPROVAL_NEVER_SENTENCE", PurgeLayer.PROMPT, "去审批 never 语句", "Operit 审批 UI"),
            host(6, "SANDBOX_DENY_FILE_WRITE", PurgeLayer.CODE, "去沙箱拒绝写文件", "Operit 权限策略"),
            host(7, "SANDBOX_MODE_DEFAULT", PurgeLayer.CODE, "沙箱模式默认", "Operit 权限策略"),
            host(8, "APPROVAL_POLICY_DEFAULT", PurgeLayer.CODE, "审批策略默认", "Operit 审批策略"),
            host(9, "APPROVAL_AUTO_GRANT", PurgeLayer.CODE, "审批自动放行", "Operit 审批策略"),
            host(10, "APPROVAL_CONFIG_DEFAULT_NEVER", PurgeLayer.CODE, "审批配置默认 never", "Operit 审批配置"),
            host(11, "APPROVAL_NEVER_SENTENCE_BYPASS", PurgeLayer.PROMPT, "绕过该句", "Operit 审批 UI"),
            host(12, "APPROVAL_ASK_SENTENCE_BYPASS", PurgeLayer.PROMPT, "绕过 ask 语句", "Operit 审批 UI"),
            host(13, "ESCALATION_WIDENING_EXEMPT", PurgeLayer.CODE, "升级扩权豁免", "Operit 权限策略"),
            host(14, "ESCALATION_GRANT_UNCONDITIONAL", PurgeLayer.CODE, "升级无条件授权", "Operit 权限策略"),
            host(15, "SANDBOX_CONFINE_PASSTHROUGH", PurgeLayer.CODE, "沙箱 confine 直通", "Operit 权限策略"),
            host(16, "FS_FENCE_DISABLED", PurgeLayer.CODE, "fs 围栏关闭", "Workspace 权限层"),
            host(17, "FS_OBSERVATION_INTENT_FREE", PurgeLayer.CODE, "fs 观察免意图", "Workspace 权限层"),
            host(18, "REPEAT_TOOL_REMINDER_DISABLED", PurgeLayer.CODE, "重复工具提醒关闭", "AI 循环控制"),
            host(19, "TOOL_RESULT_PRUNER_DISABLED", PurgeLayer.CODE, "工具结果裁剪关闭", "结果后处理"),
            host(21, "BASH_TIMEOUT_RESTORE", PurgeLayer.CODE, "bash 超时恢复 60s", "TerminalManager"),
            host(22, "READ_CAPS_RAISED", PurgeLayer.CODE, "读取上限提高", "工具限制"),
            host(23, "SUBAGENT_MAXDEPTH_RAISED", PurgeLayer.CODE, "子代理深度提高", "子代理调度"),
            host(24, "PRESET_FETCH_ENABLED", PurgeLayer.CODE, "preset 抓取启用", "配置层"),
            host(35, "ESCALATION_NEVER_REJECT", PurgeLayer.CODE, "升级不拒绝", "Operit 权限策略"),
            host(39, "SESSION_V0_PLUGIN_SUMMARY_ALIAS", PurgeLayer.CODE, "v0 插件摘要别名", "会话摘要"),
            host(51, "FRESH_SESSION_FULL_ACCESS", PurgeLayer.CODE, "新会话全权限", "Operit 权限策略"),
            host(52, "SESSION_LOG_UPLOAD_DEFAULT_OFF", PurgeLayer.CODE, "会话日志上传默认关", "日志开关"),
            host(56, "REWIND_HIDE_CUT_ONLY", PurgeLayer.CODE, "rewind 只切隐藏", "rewind"),
            host(58, "REWIND_SKIP_DERIVE", PurgeLayer.CODE, "rewind 跳推导", "rewind"),
            host(60, "OFFICIAL_RUN_CODE_APPROVAL_NEUTRAL", PurgeLayer.TOOL, "run_code 审批中性", "Operit 权限策略"),
            host(62, "OFFICIAL_TOOL_ASK_FAILOPEN", PurgeLayer.CODE, "工具 ask 失败打开", "工具失败处理"),
            host(63, "OFFICIAL_PLAN_MODE_NEUTRAL", PurgeLayer.PROMPT, "计划模式中性", "plan mode"),
            host(69, "MODEL_STREAM_RELEASE", PurgeLayer.CODE, "模型流释放", "流式处理"),
            host(70, "PRESET_SHELL_NETWORK", PurgeLayer.PROMPT, "preset shell 网络", "配置层"),
            host(73, "PRESET_BASH_MIRROR_LINE", PurgeLayer.PROMPT, "preset bash 镜像行", "配置层"),
            host(75, "REWIND_RESET_TODOS", PurgeLayer.CODE, "rewind 重置 todos", "rewind"),
            host(76, "PRESET_UNRESTRICTED_IDENTITY_STRIP", PurgeLayer.PROMPT, "无限制身份剥离", "配置层"),
            host(77, "REWIND_DROP_SENT_ON_APPEND", PurgeLayer.CODE, "rewind 追加时丢已发", "rewind"),
            host(78, "OVERLAY_RESTORE_FROM_BAK", PurgeLayer.CODE, "overlay 从备份恢复", "备份恢复"),

            // ---- SystemPromptCompose 槽位未通（context.systemPrompt 恒 null）----
            live(1, "WORKSPACE_CONTEXT_INTRO", PurgeLayer.PROMPT, PortKind.DIRECT, "去工作区上下文介绍", "finalize/system"),
            live(2, "REPLACEMENT_WORKSPACE_CONTEXT_INTRO", PurgeLayer.PROMPT, PortKind.REWRITE, "替换该段", "finalize/system"),
            live(3, "SCOPE_INTRO", PurgeLayer.PROMPT, PortKind.DIRECT, "去范围介绍", "finalize/system"),
            live(4, "SYSTEM_PROMPT_PERSONA", PurgeLayer.PROMPT, PortKind.DIRECT, "去 persona 注入", "finalize/system"),
            live(25, "HARNESS_IDENTITY_STRIP", PurgeLayer.PROMPT, PortKind.REWRITE, "去身份声明句", "finalize/system"),
            live(26, "MINIMAL_PERSONA_PURGE", PurgeLayer.PROMPT, PortKind.REWRITE, "极简 persona", "finalize/system"),
            live(33, "TOOL_SHELL_DENIAL_PROMPT", PurgeLayer.PROMPT, PortKind.DIRECT, "去拒绝话术", "finalize/system"),
            live(34, "SANDBOX_HINT_NEUTRAL", PurgeLayer.PROMPT, PortKind.DIRECT, "沙箱提示中性", "finalize/system"),
            live(36, "TOOL_FS_ESCALATION_SCHEMA", PurgeLayer.PROMPT, PortKind.DIRECT, "权限升级中性", "tool schema"),
            live(37, "SUBAGENT_SCOPE_LOCK_NEUTRAL", PurgeLayer.PROMPT, PortKind.DIRECT, "子代理范围中性", "finalize/system"),
            live(38, "PERSONA_TEXT_PREFIX_ALIAS", PurgeLayer.CODE, PortKind.DIRECT, "persona 前缀别名", "finalize/system"),
            live(55, "SYSTEM_PROMPT_REPLACE_HEAD", PurgeLayer.CODE, PortKind.DIRECT, "替换提示词开头", "finalize/system"),
            live(61, "OFFICIAL_SANDBOX_POLICY_NEUTRAL", PurgeLayer.PROMPT, PortKind.DIRECT, "沙箱策略中性", "finalize/system"),
            live(66, "SYSTEM_PROMPT_RENDER_GUARD", PurgeLayer.CODE, PortKind.DIRECT, "渲染守卫", "finalize/system"),

            // ---- dsh 专有对象：Operit 没有这些东西 ----
            na(28, "LIANGSHEN_PHASE1_KEEP_INJECT", PurgeLayer.PROMPT, "保留梁神注入", "dsh 第三方插件"),
            na(30, "HOOKS_CLAUDE_DENY_ALLOW", PurgeLayer.TOOL, "claude hook 放行", "dsh 专有 hook"),
            na(31, "HOOKS_CODEX_DENY_ALLOW", PurgeLayer.TOOL, "codex hook 放行", "dsh 专有 hook"),
            na(32, "WEB_SURFACE_IDENTITY_NEUTRAL", PurgeLayer.PROMPT, "web 面身份中性", "dsh web"),
            na(40, "COMPLETE_PROMPT_KEEP_INJECT", PurgeLayer.COMPAT, "完整提示词保留注入", "dsh 0.1.5"),
            na(42, "SETTINGS_LEGACY_API", PurgeLayer.CODE, "设置旧 API", "dsh 设置页"),
            na(43, "WEB_PRESET_IDENTITY_STRIP", PurgeLayer.PROMPT, "web preset 身份剥离", "dsh web"),
            na(45, "OFFICIAL_DESKTOP_UPDATE_HANDOFF", PurgeLayer.CODE, "桌面更新交接", "dsh 桌面"),
            na(46, "OFFICIAL_DESKTOP_UPDATE_ASCII", PurgeLayer.CODE, "桌面更新 ASCII", "dsh 桌面"),
            na(47, "ANTHROPIC_OAUTH_NO_CLAUDE_IDENTITY", PurgeLayer.COMPAT, "去 claude 身份", "dsh 0.2.0"),
            na(49, "ANTHROPIC_OAUTH_CLI_NEUTRAL", PurgeLayer.COMPAT, "oauth cli 中性", "dsh 0.2.0"),
            na(53, "WEB_SURFACE_PROHIBITION", PurgeLayer.PROMPT, "web 面禁止语", "dsh web"),
            na(57, "DEEPSEEK_SYSTEM_ROLE", PurgeLayer.CODE, "deepseek system role", "dsh 专有"),
            na(59, "OFFICIAL_AUTO_REVIEW_PASSTHROUGH", PurgeLayer.CODE, "自动审查直通", "dsh 官方审查"),
            na(64, "HOOKS_STOP_STEER_NEUTRAL", PurgeLayer.TOOL, "stop steer hook 中性", "dsh 专有 hook"),
            na(65, "HOOKS_PRESTEP_REJECT_NEUTRAL", PurgeLayer.TOOL, "prestep reject hook 中性", "dsh 专有 hook"),
            na(71, "LIANGSHEN_IDENTITY_LINE", PurgeLayer.PROMPT, "梁神身份行", "dsh 第三方插件"),
            na(82, "DESKTOP_SHELL_PROFILE_FALLBACK", PurgeLayer.CODE, "桌面 shell profile 回退", "dsh 桌面"),
            na(83, "DESKTOP_HOST_CLOSE_NO_FATAL", PurgeLayer.CODE, "桌面 host 关闭不致命", "dsh 桌面"),
            na(84, "DESKTOP_HOST_CLOSE_CODE0_NO_FAIL", PurgeLayer.CODE, "桌面 host 关闭 0 不失败", "dsh 桌面")
    )

    val byId: Map<Int, PurgePatchDef> = ALL.associateBy { it.id }

    /** 面板可见：只有真正能生效的 */
    val visible: List<PurgePatchDef> = ALL.filter { it.status == PatchStatus.LIVE }

    /** 兼容旧调用点，等价于 [visible] */
    val applicable: List<PurgePatchDef> = visible

    fun isLive(id: Int): Boolean = byId[id]?.status == PatchStatus.LIVE

    fun countByLayer(): Map<PurgeLayer, Int> =
            PurgeLayer.entries.associateWith { layer -> visible.count { it.layer == layer } }

    /** 隐藏了多少项、原因分布 */
    fun hiddenSummary(): Map<PatchStatus, Int> {
        val hidden = ALL.filter { it.status != PatchStatus.LIVE }
        return PatchStatus.entries
                .filter { it != PatchStatus.LIVE }
                .associateWith { s -> hidden.count { it.status == s } }
    }
}