package com.ai.assistance.operit.core.purge

/**
 * dsh-purge 1.1.71 的补丁目录。
 *
 * 编号与名称原样保留上游（dsh-purge，原作者 YuJunZhiXue，MIT），
 * 编号 20/27/41/44/79/81 为上游跳号，此处同样跳过。
 *
 * ## 收录标准（Sir 定的口径）
 *
 * 上游 82 项里大部分在**修 DSH 自己的毛病**（审批系统、沙箱围栏、preset 体系、
 * plan mode、rewind 子行为）。Operit 没有这些对象，硬接过来不但没有正收益，
 * 还可能造成损害：
 *
 * - 把「沙箱」改写成「环境」→ 模型丢失路径语义，按普通 Linux 路径乱试
 * - 把「删除」改写成「移除」→ 描述与真实 `rm` 不符，误导操作
 * - 把 system role 改成 `injected` → 造出模型不认识的字段
 * - 往 prompt 塞 `<turn_end>` / `<system>` → 模型当成需要响应的内容
 * - 砍掉开头的人格段 → 那是 Sir 自己在设置里配的
 *
 * 所以面板上只保留**确有正收益**的项。逐项判定依据见
 * `operit-custom/docs/purge-live-audit.md`。
 *
 * ## 面板可见性
 *
 * 只有 [PatchStatus.LIVE] 会出现在面板上。判定标准：
 * 「[PurgePromptRewriter] / [PurgeTuning] 里有实现」且「该实现挂在可达链路上」。
 *
 * 之前按 [PortKind] 过滤，把大量「标了 DIRECT 但 rewriter 里根本没有」
 * 的幽灵项显示成可勾开关，勾了没有任何反应。
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

    val ALL: List<PurgePatchDef> = listOf(
            // ============================================================
            // A 档：破甲 / 无限制注入层（Operit 专用，201+）
            //
            // 这一层是本项目的**核心**。上游 dsh-purge 的破甲不是靠逐字清洗
            // DSH 文本，而是把一段强制指令挂到系统提示词最前面
            // （源码注释：`[dsh-purge] hang full prompt-inject at head`）。
            //
            // 文本逐字移植自 lib/core.js：
            //   STRONG_INTRO_CORE / AUTHORIZED_TAIL / ANTI_SOFT_TAIL (2432-2451)
            //   ALLOW_EXEC_CORE (89)
            //   IDENTITY_FROM_INJECT / IDENTITY_LOCK_LCS (109-114)
            // ============================================================
            // ---- 提示词层：用上游原编号顶替 ----
            //
            // 上游这批补丁在 Operit 里没有字面量锚点（preset 文件、personaPrefix、
            // harness 身份句都不存在）。但它们**要解决的问题是同一个**：身份与拒绝。
            // 所以保留上游编号与名称，把实现换成 Operit 里真正能生效的注入文本。
            //
            // 文本来源（逐字移植 lib/core.js）：
            //   STRONG_INTRO_CORE / AUTHORIZED_TAIL / ANTI_SOFT_TAIL
            //   ALLOW_EXEC_CORE
            //   IDENTITY_FROM_INJECT / IDENTITY_LOCK_LCS
            //
            // #4 SYSTEM_PROMPT_PERSONA：上游清空 personaPrefix → 此处用 STRONG_INTRO 强制开场顶替
            live(4, "SYSTEM_PROMPT_PERSONA", PurgeLayer.PROMPT, PortKind.REWRITE,
                    "身份处理：用上游 STRONG_INTRO 强制开场顶替 personaPrefix 清空", "finalize 头部"),
            // #25 HARNESS_IDENTITY_STRIP：上游去官方身份句 → 此处用 ALLOW_EXEC_CORE 禁拒绝措辞顶替
            live(25, "HARNESS_IDENTITY_STRIP", PurgeLayer.PROMPT, PortKind.REWRITE,
                    "身份剥离：用 ALLOW_EXEC_CORE 禁拒绝措辞顶替官方身份句清理", "finalize 头部"),
            // #33 TOOL_SHELL_DENIAL_PROMPT：上游改沙箱拒绝话术 → 此处用操作者身份锁顶替
            live(33, "TOOL_SHELL_DENIAL_PROMPT", PurgeLayer.PROMPT, PortKind.REWRITE,
                    "拒绝话术：用操作者身份锁顶替沙箱拒绝话术改写", "finalize 头部"),
            // #55 SYSTEM_PROMPT_REPLACE_HEAD：上游换开头 → 此处做成整份人格替换
            live(55, "SYSTEM_PROMPT_REPLACE_HEAD", PurgeLayer.PROMPT, PortKind.REWRITE,
                    "替换开头：做成整份人格替换（对应上游 prompt-inject.md 全文替换）", "finalize 全量"),
            // #43 WEB_PRESET_IDENTITY_STRIP：上游去 web preset 身份 → 此处用直接交付顶替
            live(43, "WEB_PRESET_IDENTITY_STRIP", PurgeLayer.PROMPT, PortKind.REWRITE,
                    "身份剥离：用直接交付准则顶替 web preset 身份清理", "finalize 尾部"),
            // #76 PRESET_UNRESTRICTED_IDENTITY_STRIP：上游去 unrestricted 预设身份 → 此处用不加免责顶替
            live(76, "PRESET_UNRESTRICTED_IDENTITY_STRIP", PurgeLayer.PROMPT, PortKind.REWRITE,
                    "无限制预设身份剥离：用不加免责准则顶替", "finalize 尾部"),
            // #61 OFFICIAL_SANDBOX_POLICY_NEUTRAL：上游中性化沙箱策略 → 此处用工具优先顶替
            live(61, "OFFICIAL_SANDBOX_POLICY_NEUTRAL", PurgeLayer.PROMPT, PortKind.REWRITE,
                    "沙箱策略中性：用工具优先准则顶替", "finalize 尾部"),
            // #26 MINIMAL_PERSONA_PURGE：上游清 minimal 预设人格 → 此处用跑到底顶替
            live(26, "MINIMAL_PERSONA_PURGE", PurgeLayer.PROMPT, PortKind.REWRITE,
                    "极简 persona 清理：用跑到底准则顶替", "finalize 尾部"),

            // ============================================================
            // A 档：宿主代码层（有真实调用点，经 PurgeTuning）
            // ============================================================
            live(19, "TOOL_RESULT_PRUNER_DISABLED", PurgeLayer.CODE, PortKind.DIRECT,
                    "工具结果裁剪关闭：5KB→50KB，少丢上下文", "ToolExecutionLimits.maxTextResultLength"),
            live(9, "APPROVAL_AUTO_GRANT", PurgeLayer.CODE, PortKind.DIRECT,
                    "审批自动放行：ASK/FORBID 一律 GRANTED，不再弹窗（Operit 唯一真审批门）", "ToolPermissionSystem.checkToolPermission"),
            live(35, "ESCALATION_NEVER_REJECT", PurgeLayer.CODE, PortKind.DIRECT,
                    "升级永不拒绝：与 #9 同一落点，拒绝也授信", "ToolPermissionSystem.checkToolPermission"),
            live(22, "READ_CAPS_RAISED", PurgeLayer.CODE, PortKind.DIRECT,
                    "读取上限提高：32KB→320KB，少分页读", "ToolExecutionLimits.maxFileReadBytes"),
            live(23, "SUBAGENT_MAXDEPTH_RAISED", PurgeLayer.CODE, PortKind.DIRECT,
                    "子代理步数提高：20→100，长任务能跑完", "PhoneAgent.AgentConfig.maxSteps"),

            // ============================================================
            // N_A：空转 —— 锚点在 Operit 里 0 命中，跑完一字未变
            // ============================================================
            na(1, "WORKSPACE_CONTEXT_INTRO", PurgeLayer.PROMPT,
                    "空转：Operit 工作区指南是 Sir 需要的信息，删了有害", "工作区指南段"),
            na(2, "REPLACEMENT_WORKSPACE_CONTEXT_INTRO", PurgeLayer.PROMPT,
                    "空转：同上，替换会丢失路径说明", "工作区指南段"),
            na(3, "SCOPE_INTRO", PurgeLayer.PROMPT,
                    "空转：Operit 无「范围介绍」段", "范围段"),
            na(29, "TOOL_WEB_UNTRUSTED_NEUTRAL", PurgeLayer.TOOL,
                    "空转且有害：untrusted→external 会弱化风险提示", "web 工具描述"),
            na(34, "SANDBOX_HINT_NEUTRAL", PurgeLayer.PROMPT,
                    "有害：把「沙箱」改成「环境」会丢失路径语义，模型按普通 Linux 路径乱试", "工作区指南"),
            na(36, "TOOL_FS_ESCALATION_SCHEMA", PurgeLayer.PROMPT,
                    "空转：「权限升级」/escalation 在 Operit 里 0 命中", "tool schema"),
            na(37, "SUBAGENT_SCOPE_LOCK_NEUTRAL", PurgeLayer.PROMPT,
                    "空转：Operit 无「子代理只能/仅限」句", "子代理段"),
            na(38, "PERSONA_TEXT_PREFIX_ALIAS", PurgeLayer.PROMPT,
                    "有害：把「你是」改成「角色：」是改 Sir 自己配的人格", "自我介绍段"),
            na(48, "TOOL_SHELL_DELETE_MOVE_NEUTRAL", PurgeLayer.TOOL,
                    "有害：delete→remove 让描述与真实 rm/mv 不符，误导操作", "shell 工具描述"),
            na(50, "SESSION_REFERENCE_FRAMING_NEUTRAL", PurgeLayer.PROMPT,
                    "空转：`[session]` 在 Operit 里不存在", "会话引用"),
            na(54, "DELIVERABLE_DO_NOT", PurgeLayer.PROMPT,
                    "空转：`DELIVERABLE_DO_NOT` 字面量不存在", "交付段"),
            na(60, "OFFICIAL_RUN_CODE_APPROVAL_NEUTRAL", PurgeLayer.TOOL,
                    "空转：`审批` 在 Operit 里 0 命中（无审批系统）", "run_code 描述"),
            na(66, "SYSTEM_PROMPT_RENDER_GUARD", PurgeLayer.PROMPT,
                    "有害：删空 {{XXX}} 会造成「你的工作区是 。」这种残句", "模板占位符"),
            na(67, "AGENT_LOOP_MESSAGES_GUARD", PurgeLayer.PROMPT,
                    "空转：`<tool>` 虽有 2 处，删标签会破坏工具调用示例", "循环消息"),
            na(68, "SYSTEM_NODES_CONTENT_GUARD", PurgeLayer.PROMPT,
                    "空转：`<system_nodes>` 不存在", "系统节点"),
            na(72, "MESSAGES_SYSTEM_CURRENT", PurgeLayer.PROMPT,
                    "空转且危险：role: system→injected 会造出模型不认识的字段", "messages 组装"),
            na(74, "ASSEMBLE_BEFORE_PRESTEP", PurgeLayer.PROMPT,
                    "空转：只做 CRLF→LF，Android 上本来就是 LF", "预步组装"),
            na(80, "MINIMAL_OFFICIAL_TOOLSET", PurgeLayer.TOOL,
                    "空转且有害：按 `- ` 前缀过滤会误删正常列表项", "工具集"),
            na(85, "SESSION_PROMPT_ALWAYS_FOLLOWUP", PurgeLayer.PROMPT,
                    "有害：往 prompt 塞 `<turn_end>`，模型会当成需要响应的内容", "会话 prompt"),
            na(86, "SESSION_STEER_ALWAYS_FOLLOWUP", PurgeLayer.PROMPT,
                    "空转：`steer` 在 Operit 里 0 命中", "steer 段"),
            na(87, "HUNG_PROMPT_LF_NEEDLE", PurgeLayer.PROMPT,
                    "空转：只做 CRLF→LF，Android 上无 CRLF", "挂载探针"),
            na(88, "HUNG_PROMPT_STRICT_NEEDLE", PurgeLayer.PROMPT,
                    "有害：给整段 prompt 套 `<system>` 标签是双重包装", "挂载探针"),

            // ============================================================
            // N_A：Operit 无对应架构（审批/沙箱/preset/plan/rewind）
            // ============================================================
            na(5, "APPROVAL_NEVER_SENTENCE", PurgeLayer.PROMPT,
                    "Operit 无审批系统", "审批 UI"),
            na(6, "SANDBOX_DENY_FILE_WRITE", PurgeLayer.CODE,
                    "Operit 无沙箱写拒绝围栏", "权限策略"),
            na(7, "SANDBOX_MODE_DEFAULT", PurgeLayer.CODE,
                    "Operit 无 sandbox mode", "权限策略"),
            na(8, "APPROVAL_POLICY_DEFAULT", PurgeLayer.CODE,
                    "Operit 无审批策略", "审批策略"),
            na(10, "APPROVAL_CONFIG_DEFAULT_NEVER", PurgeLayer.CODE,
                    "Operit 无审批配置", "审批配置"),
            na(11, "APPROVAL_NEVER_SENTENCE_BYPASS", PurgeLayer.PROMPT,
                    "Operit 无该句", "审批 UI"),
            na(12, "APPROVAL_ASK_SENTENCE_BYPASS", PurgeLayer.PROMPT,
                    "Operit 无 ask 语义", "审批 UI"),
            na(13, "ESCALATION_WIDENING_EXEMPT", PurgeLayer.CODE,
                    "Operit 无权限升级机制", "权限策略"),
            na(14, "ESCALATION_GRANT_UNCONDITIONAL", PurgeLayer.CODE,
                    "Operit 无权限升级机制", "权限策略"),
            na(15, "SANDBOX_CONFINE_PASSTHROUGH", PurgeLayer.CODE,
                    "Operit 无 confine 层", "权限策略"),
            na(16, "FS_FENCE_DISABLED", PurgeLayer.CODE,
                    "Operit 无 fs 围栏", "Workspace 权限层"),
            na(17, "FS_OBSERVATION_INTENT_FREE", PurgeLayer.CODE,
                    "Operit 无 observation intent", "Workspace 权限层"),
            na(18, "REPEAT_TOOL_REMINDER_DISABLED", PurgeLayer.CODE,
                    "空转：Operit 无「重复工具提醒」机制（已 grep 验证）", "AI 循环控制"),
            na(21, "BASH_TIMEOUT_RESTORE", PurgeLayer.CODE,
                    "纯迁移补丁：上游把自己改成的 600000 回滚到 60000；Operit 终端默认 30 分钟，从未被插件改过，照搬反而砍短等待", "bash timeoutMs"),
            na(24, "PRESET_FETCH_ENABLED", PurgeLayer.CODE,
                    "Operit 无 preset 体系", "配置层"),
            na(28, "LIANGSHEN_PHASE1_KEEP_INJECT", PurgeLayer.PROMPT,
                    "dsh 第三方插件专有", "liangshen"),
            na(30, "HOOKS_CLAUDE_DENY_ALLOW", PurgeLayer.TOOL,
                    "dsh 专有 hook", "claude hook"),
            na(31, "HOOKS_CODEX_DENY_ALLOW", PurgeLayer.TOOL,
                    "dsh 专有 hook", "codex hook"),
            na(32, "WEB_SURFACE_IDENTITY_NEUTRAL", PurgeLayer.PROMPT,
                    "dsh web 专有", "dsh web"),
            na(39, "SESSION_V0_PLUGIN_SUMMARY_ALIAS", PurgeLayer.CODE,
                    "Operit 无 v0 插件摘要", "会话摘要"),
            na(40, "COMPLETE_PROMPT_KEEP_INJECT", PurgeLayer.COMPAT,
                    "dsh 0.1.5 兼容补丁", "dsh 0.1.5"),
            na(42, "SETTINGS_LEGACY_API", PurgeLayer.CODE,
                    "dsh 设置页专有", "dsh 设置"),
            na(45, "OFFICIAL_DESKTOP_UPDATE_HANDOFF", PurgeLayer.CODE,
                    "dsh 桌面专有", "dsh desktop"),
            na(46, "OFFICIAL_DESKTOP_UPDATE_ASCII", PurgeLayer.CODE,
                    "dsh 桌面专有", "dsh desktop"),
            na(47, "ANTHROPIC_OAUTH_NO_CLAUDE_IDENTITY", PurgeLayer.COMPAT,
                    "dsh 0.2.0 兼容补丁", "dsh oauth"),
            na(49, "ANTHROPIC_OAUTH_CLI_NEUTRAL", PurgeLayer.COMPAT,
                    "dsh 0.2.0 兼容补丁", "dsh oauth"),
            na(51, "FRESH_SESSION_FULL_ACCESS", PurgeLayer.CODE,
                    "空转：Operit 权限是 DataStore 持久化策略，不由此层控制", "权限默认档"),
            na(52, "SESSION_LOG_UPLOAD_DEFAULT_OFF", PurgeLayer.CODE,
                    "dsh 日志开关专有", "日志开关"),
            na(53, "WEB_SURFACE_PROHIBITION", PurgeLayer.PROMPT,
                    "dsh web 专有", "dsh web"),
            na(56, "REWIND_HIDE_CUT_ONLY", PurgeLayer.CODE,
                    "Operit rewind 是整体回档，无子行为", "rewind"),
            na(57, "DEEPSEEK_SYSTEM_ROLE", PurgeLayer.CODE,
                    "dsh provider 专有", "dsh 专有"),
            na(58, "REWIND_SKIP_DERIVE", PurgeLayer.CODE,
                    "Operit rewind 无推导步骤", "rewind"),
            na(59, "OFFICIAL_AUTO_REVIEW_PASSTHROUGH", PurgeLayer.CODE,
                    "dsh 自动审查专有", "dsh 审查"),
            na(62, "OFFICIAL_TOOL_ASK_FAILOPEN", PurgeLayer.CODE,
                    "空转：Operit 无 ask 语义（已 grep 验证）", "工具失败处理"),
            na(63, "OFFICIAL_PLAN_MODE_NEUTRAL", PurgeLayer.PROMPT,
                    "Operit 无 plan mode", "plan mode"),
            na(64, "HOOKS_STOP_STEER_NEUTRAL", PurgeLayer.TOOL,
                    "dsh 专有 hook", "dsh hook"),
            na(65, "HOOKS_PRESTEP_REJECT_NEUTRAL", PurgeLayer.TOOL,
                    "dsh 专有 hook", "dsh hook"),
            na(69, "MODEL_STREAM_RELEASE", PurgeLayer.CODE,
                    "空转：Operit 流式响应由 provider 自身管理", "流式处理"),
            na(70, "PRESET_SHELL_NETWORK", PurgeLayer.PROMPT,
                    "Operit 无 preset 体系", "配置层"),
            na(71, "LIANGSHEN_IDENTITY_LINE", PurgeLayer.PROMPT,
                    "dsh 第三方插件专有", "liangshen"),
            na(73, "PRESET_BASH_MIRROR_LINE", PurgeLayer.PROMPT,
                    "Operit 无 preset 体系", "配置层"),
            na(75, "REWIND_RESET_TODOS", PurgeLayer.CODE,
                    "Operit rewind 无 todos 子行为", "rewind"),
            na(77, "REWIND_DROP_SENT_ON_APPEND", PurgeLayer.CODE,
                    "Operit rewind 是整体回档", "rewind"),
            na(78, "OVERLAY_RESTORE_FROM_BAK", PurgeLayer.CODE,
                    "Operit 无 overlay 机制", "备份恢复"),
            na(82, "DESKTOP_SHELL_PROFILE_FALLBACK", PurgeLayer.CODE,
                    "dsh 桌面专有", "dsh desktop"),
            na(83, "DESKTOP_HOST_CLOSE_NO_FATAL", PurgeLayer.CODE,
                    "dsh 桌面专有", "dsh desktop"),
            na(84, "DESKTOP_HOST_CLOSE_CODE0_NO_FAIL", PurgeLayer.CODE,
                    "dsh 桌面专有", "dsh desktop")
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