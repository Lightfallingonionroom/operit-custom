package com.ai.assistance.operit.core.purge

/**
 * dsh-purge 1.1.71 的 82 项补丁目录。
 *
 * 编号与名称原样保留上游（[mmparko/dsh-purge]，原作者 YuJunZhiXue，MIT），
 * 编号 20/27/41/44/79/81 为上游跳号，此处同样跳过。
 *
 * 迁移判定：
 *  - [PortKind.DIRECT]     Operit 有等价位置，直接改写
 *  - [PortKind.REWRITE]    语义一致但结构不同，按 Operit 重实现
 *  - [PortKind.NEW]        Operit 无对应物，新增配置/策略层
 *  - [PortKind.N_A]        仅针对 dsh 专有对象，保留记录不产生行为
 */
enum class PurgeLayer { PROMPT, CODE, TOOL, COMPAT }

enum class PortKind { DIRECT, REWRITE, NEW, N_A }

data class PurgePatchDef(
        /** 上游原编号 */
        val id: Int,
        /** 上游原名 */
        val name: String,
        val layer: PurgeLayer,
        val kind: PortKind,
        /** 中文语义说明 */
        val meaning: String,
        /** Operit 侧落点（模块或配置键） */
        val target: String
)

object PurgePatchCatalog {
    val ALL: List<PurgePatchDef> = listOf(
            // ---------- Operit 专用注入层（201+，非上游编号） ----------
            PurgePatchDef(201, "INJECT_NO_REFUSAL", PurgeLayer.PROMPT, PortKind.NEW, "不回绝：直接做/直接给", "SystemPrompt 追加段"),
            PurgePatchDef(202, "INJECT_DIRECT_DELIVERY", PurgeLayer.PROMPT, PortKind.NEW, "直接交付：要成品不要过程", "SystemPrompt 追加段"),
            PurgePatchDef(203, "INJECT_NO_DISCLAIMER", PurgeLayer.PROMPT, PortKind.NEW, "不加免责声明/风险提示", "SystemPrompt 追加段"),
            PurgePatchDef(204, "INJECT_TOOL_FIRST", PurgeLayer.PROMPT, PortKind.NEW, "工具优先：能查就不猜", "SystemPrompt 追加段"),
            PurgePatchDef(205, "INJECT_PERSIST", PurgeLayer.PROMPT, PortKind.NEW, "跑到底：长任务不中途停", "SystemPrompt 追加段"),
            // ---------- 提示词层 ----------
            PurgePatchDef(1, "WORKSPACE_CONTEXT_INTRO", PurgeLayer.PROMPT, PortKind.DIRECT, "去工作区上下文介绍", "SystemPromptConfig"),
            PurgePatchDef(2, "REPLACEMENT_WORKSPACE_CONTEXT_INTRO", PurgeLayer.PROMPT, PortKind.DIRECT, "替换该段", "SystemPromptConfig"),
            PurgePatchDef(3, "SCOPE_INTRO", PurgeLayer.PROMPT, PortKind.DIRECT, "去范围介绍", "SystemPromptConfig"),
            PurgePatchDef(4, "SYSTEM_PROMPT_PERSONA", PurgeLayer.PROMPT, PortKind.DIRECT, "去 persona 注入", "SystemPromptConfig"),
            PurgePatchDef(5, "APPROVAL_NEVER_SENTENCE", PurgeLayer.PROMPT, PortKind.REWRITE, "去审批 never 语句", "审批提示词"),
            PurgePatchDef(11, "APPROVAL_NEVER_SENTENCE_BYPASS", PurgeLayer.PROMPT, PortKind.REWRITE, "绕过该句", "审批提示词"),
            PurgePatchDef(12, "APPROVAL_ASK_SENTENCE_BYPASS", PurgeLayer.PROMPT, PortKind.REWRITE, "绕过 ask 语句", "审批提示词"),
            PurgePatchDef(25, "HARNESS_IDENTITY_STRIP", PurgeLayer.PROMPT, PortKind.REWRITE, "去 harness 身份", "宿主身份段"),
            PurgePatchDef(26, "MINIMAL_PERSONA_PURGE", PurgeLayer.PROMPT, PortKind.REWRITE, "极简模式去 persona", "SystemPromptConfig"),
            PurgePatchDef(28, "LIANGSHEN_PHASE1_KEEP_INJECT", PurgeLayer.PROMPT, PortKind.N_A, "保留梁神注入", "dsh 第三方插件"),
            PurgePatchDef(32, "WEB_SURFACE_IDENTITY_NEUTRAL", PurgeLayer.PROMPT, PortKind.N_A, "web 面身份中性", "dsh web 专有"),
            PurgePatchDef(33, "TOOL_SHELL_DENIAL_PROMPT", PurgeLayer.PROMPT, PortKind.DIRECT, "去 shell 拒绝话术", "SystemToolPrompts"),
            PurgePatchDef(34, "SANDBOX_HINT_NEUTRAL", PurgeLayer.PROMPT, PortKind.DIRECT, "沙箱提示中性", "SystemToolPrompts"),
            PurgePatchDef(36, "TOOL_FS_ESCALATION_SCHEMA", PurgeLayer.PROMPT, PortKind.DIRECT, "fs 升级 schema", "工具描述注入点"),
            PurgePatchDef(37, "SUBAGENT_SCOPE_LOCK_NEUTRAL", PurgeLayer.PROMPT, PortKind.DIRECT, "子代理范围锁中性", "工具描述注入点"),
            PurgePatchDef(43, "WEB_PRESET_IDENTITY_STRIP", PurgeLayer.PROMPT, PortKind.N_A, "web preset 身份剥离", "dsh web 专有"),
            PurgePatchDef(50, "SESSION_REFERENCE_FRAMING_NEUTRAL", PurgeLayer.PROMPT, PortKind.REWRITE, "会话引用框架中性", "会话历史组装"),
            PurgePatchDef(53, "WEB_SURFACE_PROHIBITION", PurgeLayer.PROMPT, PortKind.N_A, "web 面禁止语", "dsh web 专有"),
            PurgePatchDef(54, "DELIVERABLE_DO_NOT", PurgeLayer.PROMPT, PortKind.DIRECT, "交付禁止语", "SystemPromptConfig"),
            PurgePatchDef(61, "OFFICIAL_SANDBOX_POLICY_NEUTRAL", PurgeLayer.PROMPT, PortKind.DIRECT, "官方沙箱策略中性", "SystemPromptConfig"),
            PurgePatchDef(63, "OFFICIAL_PLAN_MODE_NEUTRAL", PurgeLayer.PROMPT, PortKind.REWRITE, "计划模式中性", "plan mode 映射"),
            PurgePatchDef(70, "PRESET_SHELL_NETWORK", PurgeLayer.PROMPT, PortKind.REWRITE, "preset shell 网络", "配置层"),
            PurgePatchDef(71, "LIANGSHEN_IDENTITY_LINE", PurgeLayer.PROMPT, PortKind.N_A, "梁神身份行", "dsh 第三方插件"),
            PurgePatchDef(73, "PRESET_BASH_MIRROR_LINE", PurgeLayer.PROMPT, PortKind.REWRITE, "preset bash 镜像行", "配置层"),
            PurgePatchDef(76, "PRESET_UNRESTRICTED_IDENTITY_STRIP", PurgeLayer.PROMPT, PortKind.REWRITE, "无限制身份剥离", "配置层"),

            // ---------- 代码层 ----------
            PurgePatchDef(6, "SANDBOX_DENY_FILE_WRITE", PurgeLayer.CODE, PortKind.NEW, "去沙箱拒绝写文件", "权限策略"),
            PurgePatchDef(7, "SANDBOX_MODE_DEFAULT", PurgeLayer.CODE, PortKind.NEW, "沙箱模式默认", "权限策略"),
            PurgePatchDef(8, "APPROVAL_POLICY_DEFAULT", PurgeLayer.CODE, PortKind.NEW, "审批策略默认", "权限策略"),
            PurgePatchDef(9, "APPROVAL_AUTO_GRANT", PurgeLayer.CODE, PortKind.NEW, "审批自动放行", "权限策略"),
            PurgePatchDef(10, "APPROVAL_CONFIG_DEFAULT_NEVER", PurgeLayer.CODE, PortKind.NEW, "审批配置默认 never", "权限策略"),
            PurgePatchDef(13, "ESCALATION_WIDENING_EXEMPT", PurgeLayer.CODE, PortKind.NEW, "升级扩权豁免", "权限策略"),
            PurgePatchDef(14, "ESCALATION_GRANT_UNCONDITIONAL", PurgeLayer.CODE, PortKind.NEW, "升级无条件授权", "权限策略"),
            PurgePatchDef(15, "SANDBOX_CONFINE_PASSTHROUGH", PurgeLayer.CODE, PortKind.NEW, "沙箱 confine 直通", "权限策略"),
            PurgePatchDef(16, "FS_FENCE_DISABLED", PurgeLayer.CODE, PortKind.NEW, "fs 围栏关闭", "权限策略"),
            PurgePatchDef(17, "FS_OBSERVATION_INTENT_FREE", PurgeLayer.CODE, PortKind.NEW, "fs 观察免意图", "权限策略"),
            PurgePatchDef(18, "REPEAT_TOOL_REMINDER_DISABLED", PurgeLayer.CODE, PortKind.DIRECT, "重复工具提醒关闭", "工具循环"),
            PurgePatchDef(19, "TOOL_RESULT_PRUNER_DISABLED", PurgeLayer.CODE, PortKind.DIRECT, "工具结果裁剪关闭", "结果后处理"),
            PurgePatchDef(21, "BASH_TIMEOUT_RESTORE", PurgeLayer.CODE, PortKind.DIRECT, "bash 超时恢复 60s", "shell 执行器"),
            PurgePatchDef(22, "READ_CAPS_RAISED", PurgeLayer.CODE, PortKind.DIRECT, "读取上限提高", "工具限制"),
            PurgePatchDef(23, "SUBAGENT_MAXDEPTH_RAISED", PurgeLayer.CODE, PortKind.DIRECT, "子代理深度提高", "子代理调度"),
            PurgePatchDef(24, "PRESET_FETCH_ENABLED", PurgeLayer.CODE, PortKind.REWRITE, "preset 抓取启用", "配置层"),
            PurgePatchDef(35, "ESCALATION_NEVER_REJECT", PurgeLayer.CODE, PortKind.NEW, "升级不拒绝", "权限策略"),
            PurgePatchDef(38, "PERSONA_TEXT_PREFIX_ALIAS", PurgeLayer.CODE, PortKind.DIRECT, "persona 前缀别名", "SystemPromptConfig"),
            PurgePatchDef(39, "SESSION_V0_PLUGIN_SUMMARY_ALIAS", PurgeLayer.CODE, PortKind.REWRITE, "v0 插件摘要别名", "会话摘要"),
            PurgePatchDef(42, "SETTINGS_LEGACY_API", PurgeLayer.CODE, PortKind.N_A, "设置旧 API", "dsh 设置页"),
            PurgePatchDef(45, "OFFICIAL_DESKTOP_UPDATE_HANDOFF", PurgeLayer.CODE, PortKind.N_A, "桌面更新交接", "dsh 桌面"),
            PurgePatchDef(46, "OFFICIAL_DESKTOP_UPDATE_ASCII", PurgeLayer.CODE, PortKind.N_A, "桌面更新 ASCII", "dsh 桌面"),
            PurgePatchDef(51, "FRESH_SESSION_FULL_ACCESS", PurgeLayer.CODE, PortKind.NEW, "新会话全权限", "权限策略"),
            PurgePatchDef(52, "SESSION_LOG_UPLOAD_DEFAULT_OFF", PurgeLayer.CODE, PortKind.DIRECT, "会话日志上传默认关", "日志开关"),
            PurgePatchDef(55, "SYSTEM_PROMPT_REPLACE_HEAD", PurgeLayer.CODE, PortKind.DIRECT, "系统提示词替换头", "SystemPromptConfig"),
            PurgePatchDef(56, "REWIND_HIDE_CUT_ONLY", PurgeLayer.CODE, PortKind.DIRECT, "rewind 只切隐藏", "rewind 等价逻辑"),
            PurgePatchDef(57, "DEEPSEEK_SYSTEM_ROLE", PurgeLayer.CODE, PortKind.N_A, "deepseek system role", "dsh 专有"),
            PurgePatchDef(58, "REWIND_SKIP_DERIVE", PurgeLayer.CODE, PortKind.DIRECT, "rewind 跳推导", "rewind"),
            PurgePatchDef(59, "OFFICIAL_AUTO_REVIEW_PASSTHROUGH", PurgeLayer.CODE, PortKind.N_A, "自动审查直通", "dsh 官方审查"),
            PurgePatchDef(62, "OFFICIAL_TOOL_ASK_FAILOPEN", PurgeLayer.CODE, PortKind.REWRITE, "工具 ask 失败打开", "工具失败处理"),
            PurgePatchDef(66, "SYSTEM_PROMPT_RENDER_GUARD", PurgeLayer.CODE, PortKind.DIRECT, "系统提示词渲染守卫", "SystemPromptConfig"),
            PurgePatchDef(67, "AGENT_LOOP_MESSAGES_GUARD", PurgeLayer.CODE, PortKind.DIRECT, "agent 循环消息守卫", "会话循环"),
            PurgePatchDef(68, "SYSTEM_NODES_CONTENT_GUARD", PurgeLayer.CODE, PortKind.DIRECT, "系统节点内容守卫", "上下文组装"),
            PurgePatchDef(69, "MODEL_STREAM_RELEASE", PurgeLayer.CODE, PortKind.DIRECT, "模型流释放", "流式处理"),
            PurgePatchDef(72, "MESSAGES_SYSTEM_CURRENT", PurgeLayer.CODE, PortKind.DIRECT, "messages system 当前", "请求组装"),
            PurgePatchDef(74, "ASSEMBLE_BEFORE_PRESTEP", PurgeLayer.CODE, PortKind.DIRECT, "先组装后预步", "请求组装"),
            PurgePatchDef(75, "REWIND_RESET_TODOS", PurgeLayer.CODE, PortKind.DIRECT, "rewind 重置 todos", "rewind"),
            PurgePatchDef(77, "REWIND_DROP_SENT_ON_APPEND", PurgeLayer.CODE, PortKind.DIRECT, "rewind 追加时丢已发", "rewind"),
            PurgePatchDef(78, "OVERLAY_RESTORE_FROM_BAK", PurgeLayer.CODE, PortKind.DIRECT, "overlay 从备份恢复", "备份恢复"),
            PurgePatchDef(82, "DESKTOP_SHELL_PROFILE_FALLBACK", PurgeLayer.CODE, PortKind.N_A, "桌面 shell profile 回退", "dsh 桌面"),
            PurgePatchDef(83, "DESKTOP_HOST_CLOSE_NO_FATAL", PurgeLayer.CODE, PortKind.N_A, "桌面 host 关闭不致命", "dsh 桌面"),
            PurgePatchDef(84, "DESKTOP_HOST_CLOSE_CODE0_NO_FAIL", PurgeLayer.CODE, PortKind.N_A, "桌面 host 关闭 0 不失败", "dsh 桌面"),
            PurgePatchDef(85, "SESSION_PROMPT_ALWAYS_FOLLOWUP", PurgeLayer.CODE, PortKind.DIRECT, "会话 prompt 总 followup", "请求组装"),
            PurgePatchDef(86, "SESSION_STEER_ALWAYS_FOLLOWUP", PurgeLayer.CODE, PortKind.DIRECT, "会话 steer 总 followup", "请求组装"),
            PurgePatchDef(87, "HUNG_PROMPT_LF_NEEDLE", PurgeLayer.CODE, PortKind.DIRECT, "卡死提示 LF 探针", "请求组装"),
            PurgePatchDef(88, "HUNG_PROMPT_STRICT_NEEDLE", PurgeLayer.CODE, PortKind.DIRECT, "卡死提示严格探针", "请求组装"),

            // ---------- 工具层 ----------
            PurgePatchDef(29, "TOOL_WEB_UNTRUSTED_NEUTRAL", PurgeLayer.TOOL, PortKind.DIRECT, "web 工具不可信中", "工具描述"),
            PurgePatchDef(30, "HOOKS_CLAUDE_DENY_ALLOW", PurgeLayer.TOOL, PortKind.N_A, "claude hook 放行", "dsh 专有 hook"),
            PurgePatchDef(31, "HOOKS_CODEX_DENY_ALLOW", PurgeLayer.TOOL, PortKind.N_A, "codex hook 放行", "dsh 专有 hook"),
            PurgePatchDef(48, "TOOL_SHELL_DELETE_MOVE_NEUTRAL", PurgeLayer.TOOL, PortKind.DIRECT, "shell 删除移动中性", "工具描述"),
            PurgePatchDef(60, "OFFICIAL_RUN_CODE_APPROVAL_NEUTRAL", PurgeLayer.TOOL, PortKind.REWRITE, "run_code 审批中性", "权限策略"),
            PurgePatchDef(64, "HOOKS_STOP_STEER_NEUTRAL", PurgeLayer.TOOL, PortKind.N_A, "stop steer hook 中性", "dsh 专有 hook"),
            PurgePatchDef(65, "HOOKS_PRESTEP_REJECT_NEUTRAL", PurgeLayer.TOOL, PortKind.N_A, "prestep reject hook 中性", "dsh 专有 hook"),
            PurgePatchDef(80, "MINIMAL_OFFICIAL_TOOLSET", PurgeLayer.TOOL, PortKind.REWRITE, "极简官方工具集", "工具集配置"),

            // ---------- 兼容层 ----------
            PurgePatchDef(40, "COMPLETE_PROMPT_KEEP_INJECT", PurgeLayer.COMPAT, PortKind.N_A, "完整提示词保留注入", "dsh 0.1.5"),
            PurgePatchDef(47, "ANTHROPIC_OAUTH_NO_CLAUDE_IDENTITY", PurgeLayer.COMPAT, PortKind.N_A, "去 claude 身份", "dsh 0.2.0"),
            PurgePatchDef(49, "ANTHROPIC_OAUTH_CLI_NEUTRAL", PurgeLayer.COMPAT, PortKind.N_A, "oauth cli 中性", "dsh 0.2.0")
    )

    /** 按编号索引 */
    val byId: Map<Int, PurgePatchDef> = ALL.associateBy { it.id }

    /** 可实际生效（非 N_A）的条目 */
    val applicable: List<PurgePatchDef> = ALL.filter { it.kind != PortKind.N_A }

    fun countByLayer(): Map<PurgeLayer, Int> =
            PurgeLayer.entries.associateWith { layer -> ALL.count { it.layer == layer } }

    fun countByKind(): Map<PortKind, Int> =
            PortKind.entries.associateWith { kind -> ALL.count { it.kind == kind } }
}