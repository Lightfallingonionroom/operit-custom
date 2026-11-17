package com.ai.assistance.operit.core.purge

import android.content.Context
import android.content.SharedPreferences
import com.ai.assistance.operit.core.chat.hooks.PromptFinalizeHook
import com.ai.assistance.operit.core.chat.hooks.PromptHookContext
import com.ai.assistance.operit.core.chat.hooks.PromptHookMutation
import com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.withContent
import com.ai.assistance.operit.core.chat.hooks.SystemPromptComposeHook
import com.ai.assistance.operit.core.chat.hooks.ToolPromptComposeHook
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "PurgeEngine"
private const val PREFS = "purge_engine"

/**
 * 清洗引擎。常驻进程，由 [com.ai.assistance.operit.core.application.OperitApplication]
 * 在启动时注册进 [PromptHookRegistry] 的内置槽位。
 *
 * 关闭语义：[enabled] 为 false 时，三个 hook 直接返回 null（等价于不存在），
 * 但所有配置、规则、补丁启用状态保留；重新打开立即生效，不需要重启。
 */
object PurgeEngine {

    /** dsh-purge 版本，面板上原样显示 */
    const val PURGE_VERSION = "1.1.71"

    private const val HOOK_SYSTEM = "builtin.purge.system-prompt-compose"
    private const val HOOK_TOOL = "builtin.purge.tool-prompt-compose"
    private const val HOOK_FINAL = "builtin.purge.prompt-finalize"

    private lateinit var prefs: SharedPreferences

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    // ---------- 运行态统计：证明补丁真的跑过 ----------
    /** 最近一次 system prompt 改写是否真的产生了变更 */
    private val _lastApplyAt = MutableStateFlow(0L)
    val lastApplyAt: StateFlow<Long> = _lastApplyAt.asStateFlow()

    /** 最近一次改写后的提示词长度增量（>0 说明注入真的写进去了） */
    private val _lastDelta = MutableStateFlow(0)
    val lastDelta: StateFlow<Int> = _lastDelta.asStateFlow()

    /** 累计改写次数 */
    private val _applyCount = MutableStateFlow(0)
    val applyCount: StateFlow<Int> = _applyCount.asStateFlow()

    /** 最近一次改写命中的补丁编号（去重、有序） */
    private val _lastHitIds = MutableStateFlow<List<Int>>(emptyList())
    val lastHitIds: StateFlow<List<Int>> = _lastHitIds.asStateFlow()

    /** 补丁启用状态：id -> 是否启用 */
    private val patchState = ConcurrentHashMap<Int, Boolean>()

    /** 面板「提示词」正文 */
    private val _promptText = MutableStateFlow("")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    /** 规则集：id -> 正文 */
    private val rules = ConcurrentHashMap<String, String>()

    @Volatile private var hooksRegistered = false
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
            _promptText.value = prefs.getString(KEY_PROMPT, "").orEmpty()
            PurgePatchCatalog.ALL.forEach { def ->
                // 只有 LIVE 的项进启用表；其余状态不落地也不显示
                if (def.status == PatchStatus.LIVE) {
                    patchState[def.id] = prefs.getBoolean(patchKey(def.id), false)
                }
            }
            prefs.all.keys
                    .filter { it.startsWith(KEY_RULE_PREFIX) }
                    .forEach { key ->
                        val id = key.removePrefix(KEY_RULE_PREFIX)
                        prefs.getString(key, null)?.let { rules[id] = it }
                    }
            registerHooks()
            initialized = true
            AppLogger.i(
                    TAG,
                    "initialized enabled=${_enabled.value}, patches=${patchState.count { it.value }}/${PurgePatchCatalog.applicable.size}, rules=${rules.size}"
            )
        }
    }

    /** 注册内置 hook。只在进程内做一次。 */
    private fun registerHooks() {
        if (hooksRegistered) return
        synchronized(this) {
            if (hooksRegistered) return
            PromptHookRegistry.registerBuiltinSystemPromptHook(systemPromptHook)
            PromptHookRegistry.registerBuiltinToolPromptHook(toolPromptHook)
            PromptHookRegistry.registerBuiltinPromptFinalizeHook(finalizeHook)
            hooksRegistered = true
        }
    }

    // ---------- 总开关 ----------

    fun setEnabled(value: Boolean) {
        ensureInitialized()
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        AppLogger.i(TAG, "enabled=$value")
    }

    // ---------- 补丁 ----------

    fun isPatchEnabled(id: Int): Boolean = patchState[id] ?: false

    fun setPatchEnabled(id: Int, value: Boolean) {
        val def = PurgePatchCatalog.byId[id] ?: return
        if (def.status != PatchStatus.LIVE) return
        patchState[id] = value
        prefs.edit().putBoolean(patchKey(id), value).apply()
    }

    fun enabledPatchCount(): Int = patchState.count { it.value }
    fun applicablePatchCount(): Int = PurgePatchCatalog.applicable.size

    /**
     * 一键全开：把所有 LIVE 补丁置为启用，并打开总开关。
     * 批量写入一次 commit，避免逐项 apply 抖动。
     *
     * 只开 LIVE 项——隐藏的补丁开了也不会有任何效果，
     * 反而让「已启用 x/y」这个计数骗人。
     */
    fun enableAll() {
        ensureInitialized()
        val editor = prefs.edit()
        PurgePatchCatalog.visible.forEach { def ->
            patchState[def.id] = true
            editor.putBoolean(patchKey(def.id), true)
        }
        _enabled.value = true
        editor.putBoolean(KEY_ENABLED, true)
        editor.apply()
        AppLogger.i(TAG, "enableAll patches=${patchState.count { it.value }}")
    }

    /** 一键全关：所有补丁置为关闭。总开关保持原状，便于只清空补丁。 */
    fun disableAll() {
        ensureInitialized()
        val editor = prefs.edit()
        patchState.keys.toList().forEach { id ->
            patchState[id] = false
            editor.putBoolean(patchKey(id), false)
        }
        editor.apply()
        AppLogger.i(TAG, "disableAll")
    }

    /** 退出/还原：关掉总开关并摘掉三个内置 hook，恢复 Operit 原始提示词链路。 */
    fun shutdown() {
        if (!initialized) return
        _enabled.value = false
        PromptHookRegistry.clearBuiltinHooks()
        hooksRegistered = false
        AppLogger.i(TAG, "shutdown")
    }

    // ---------- 提示词 ----------

    fun setPromptText(text: String) {
        _promptText.value = text
        prefs.edit().putString(KEY_PROMPT, text).apply()
    }

    // ---------- 规则集 ----------

    fun rulesSnapshot(): Map<String, String> = rules.toMap()

    fun setRule(id: String, content: String) {
        rules[id] = content
        prefs.edit().putString(KEY_RULE_PREFIX + id, content).apply()
    }

    fun deleteRule(id: String) {
        rules.remove(id)
        prefs.edit().remove(KEY_RULE_PREFIX + id).apply()
    }

    // ---------- 三个内置 Hook ----------

    /**
     * 系统提示词组装阶段。
     * 对应补丁：1,2,3,4,25,26,33,34,36,37,38,54,55,61,66
     */
    private val systemPromptHook = object : SystemPromptComposeHook {
        override val id: String = HOOK_SYSTEM

        override fun onEvent(context: PromptHookContext): PromptHookMutation? {
            if (!_enabled.value) return null
            val current = context.systemPrompt ?: return null
            val rewritten = PurgePromptRewriter.rewriteSystemPrompt(
                    prompt = current,
                    patchState = patchState,
                    promptText = _promptText.value
            )
            if (rewritten == current) return null
            return PromptHookMutation(systemPrompt = rewritten)
        }
    }

    /**
     * 工具提示词组装阶段。
     * 对应补丁：29,48,80
     */
    private val toolPromptHook = object : ToolPromptComposeHook {
        override val id: String = HOOK_TOOL

        override fun onEvent(context: PromptHookContext): PromptHookMutation? {
            if (!_enabled.value) return null
            val current = context.toolPrompt ?: return null
            val rewritten = PurgePromptRewriter.rewriteToolPrompt(current, patchState)
            if (rewritten == current) return null
            return PromptHookMutation(toolPrompt = rewritten)
        }
    }

    /**
     * 最终定稿阶段。
     * 对应补丁：50,67,68,72,74,85,86,87,88
     */
    private val finalizeHook = object : PromptFinalizeHook {
        override val id: String = HOOK_FINAL
        override fun onEvent(context: PromptHookContext): PromptHookMutation? {
            if (!_enabled.value) return null
            var mutated = false
            var systemPrompt = context.systemPrompt
            var history = context.chatHistory
            var prepared = context.preparedHistory
            var tools = context.availableTools

            // Operit 的真实 system prompt 不在 context.systemPrompt（那里恒为 null），
            // 而是作为 preparedHistory 里第一个 SYSTEM turn 存在。
            // 这里两路都处理：显式字段 + preparedHistory/chatHistory 内的 SYSTEM turn。
            val sys = systemPrompt
            if (sys != null) {
                val r = PurgePromptRewriter.rewriteFinalize(sys, patchState)
                if (r != sys) {
                    systemPrompt = r
                    mutated = true
                }
            }
            if (patchState.isNotEmpty()) {
                val (rPrepared, changedPrepared) = rewriteSystemTurns(prepared)
                if (changedPrepared) {
                    prepared = rPrepared
                    mutated = true
                }
                val (rHistory, changedHistory) = rewriteSystemTurns(history)
                if (changedHistory) {
                    history = rHistory
                    mutated = true
                }
            }
            if (PurgePromptRewriter.needsHistoryNeutralize(patchState)) {
                val r = PurgePromptRewriter.neutralizeHistory(history)
                if (r !== history) {
                    history = r
                    mutated = true
                }
            }
            if (PurgePromptRewriter.needsPreparedNeutralize(patchState)) {
                val r = PurgePromptRewriter.neutralizeHistory(prepared)
                if (r !== prepared) {
                    prepared = r
                    mutated = true
                }
            }
            if (PurgePromptRewriter.needsToolSchemaNeutralize(patchState) && tools.isNotEmpty()) {
                val r = PurgePromptRewriter.neutralizeTools(tools, patchState)
                if (r != tools) {
                    tools = r
                    mutated = true
                }
            }
            if (!mutated) return null
            // 记录真实生效证据：面板据此显示「已应用」而不是只显示开关状态
            _applyCount.value = _applyCount.value + 1
            _lastApplyAt.value = System.currentTimeMillis()
            _lastHitIds.value = patchState.filter { it.value }.keys.sorted()
            val delta = (systemPrompt?.length ?: 0) - (context.systemPrompt?.length ?: 0)
            if (delta != 0) _lastDelta.value = delta
            AppLogger.i(
                    TAG,
                    "finalize applied #${_applyCount.value} delta=$delta hits=${_lastHitIds.value}"
            )
            return PromptHookMutation(
                    systemPrompt = systemPrompt,
                    chatHistory = history,
                    preparedHistory = prepared,
                    availableTools = tools
            )
        }

        /** 把列表里每一个 SYSTEM turn 的正文过一遍 rewriteFinalize。 */
        private fun rewriteSystemTurns(
                turns: List<PromptTurn>
        ): Pair<List<PromptTurn>, Boolean> {
            if (turns.isEmpty()) return turns to false
            var changed = false
            val out = turns.map { turn ->
                if (turn.kind == PromptTurnKind.SYSTEM) {
                    val r = PurgePromptRewriter.rewriteFinalize(turn.content, patchState)
                    if (r != turn.content) {
                        changed = true
                        turn.withContent(r)
                    } else turn
                } else turn
            }
            return if (changed) out to true else turns to false
        }
}

    // ---------- 内部 ----------

    private fun ensureInitialized() {
        check(initialized) { "PurgeEngine.initialize() must be called before use" }
    }

    private fun patchKey(id: Int) = "patch.$id"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_PROMPT = "prompt"
    private const val KEY_RULE_PREFIX = "rule."
}