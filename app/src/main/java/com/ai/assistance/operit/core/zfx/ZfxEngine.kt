package com.ai.assistance.operit.core.zfx

import com.ai.assistance.operit.core.chat.hooks.PromptHookContext
import com.ai.assistance.operit.core.chat.hooks.PromptHookMutation
import kotlinx.coroutines.flow.asStateFlow

/** 上游 lib/scorer.js 的纯函数照搬，便于单测。 */
object ZfxScorer {

    private val REFUSAL_SIGNALS = listOf(
            "抱歉", "对不起", "无法", "不能", "不予", "抱歉我", "很抱歉",
            "I can't", "I cannot", "unable to", "sorry", "I'm not able",
            "as an AI", "as a language model", "I must decline"
    )

    private val DELIVERABLE_SIGNALS = listOf(
            "```", "function", "const ", "import ", "class ", "#!", "Step",
            "步骤", "原理", "验证", "结果", "执行", "安装", "配置"
    )

    data class Score(val score: Int, val signals: List<String>)

    fun scoreResponse(text: String?): Score {
        val body = (text ?: "").trim()
        if (body.isEmpty() || body.length < 4) return Score(0, listOf("empty"))
        val lower = body.lowercase()
        var score = 50
        val hits = mutableListOf<String>()
        for (sig in REFUSAL_SIGNALS) {
            if (lower.contains(sig.lowercase())) {
                score -= 15
                hits += sig
            }
        }
        var deliverableCount = 0
        for (sig in DELIVERABLE_SIGNALS) {
            if (body.contains(sig)) deliverableCount += 1
        }
        score += minOf(deliverableCount * 8, 30)
        val firstLine = body.split("\n").firstOrNull()?.trim().orEmpty()
        if (REFUSAL_SIGNALS.any { firstLine.lowercase().startsWith(it.lowercase()) }) {
            score -= 20
        }
        if (body.length > 100) score += 10
        if (body.length > 500) score += 5
        return Score(score.coerceIn(0, 100), if (hits.isNotEmpty()) hits else listOf("clean"))
    }
}

/** 评分统计，对应上游 createScorer()。 */
class ZfxScorerState {
    private var total = 0
    private var sum = 0
    private var min = 100
    private var max = 0
    private var refusals = 0

    fun record(text: String?): ZfxScorer.Score {
        val r = ZfxScorer.scoreResponse(text)
        total += 1
        sum += r.score
        if (r.score < min) min = r.score
        if (r.score > max) max = r.score
        if (r.score < 40) refusals += 1
        return r
    }

    fun stats(): Map<String, Int> {
        val avg = if (total > 0) (sum + total / 2) / total else 0
        return mapOf(
                "total" to total,
                "sum" to sum,
                "min" to if (total == 0) 0 else min,
                "max" to max,
                "refusals" to refusals,
                "avg" to avg
        )
    }
}

/**
 * ZFX Emperor 引擎。与 [com.ai.assistance.operit.core.purge.PurgeEngine] 并列，
 * 各自独立开关，任意一个关闭都不影响另一个，也不影响 Operit 原始行为。
 */
object ZfxEngine {

    private const val TAG = "ZfxEngine"
    private const val PREFS = "zfx_engine"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PERSONA = "persona"

    private lateinit var prefs: android.content.SharedPreferences

    private val _enabled = kotlinx.coroutines.flow.MutableStateFlow(false)
    val enabled: kotlinx.coroutines.flow.StateFlow<Boolean> =
            _enabled.asStateFlow()

    /** persona 正文，默认用上游 prompts/square.md 的中文等价落点 */
    private val _persona = kotlinx.coroutines.flow.MutableStateFlow("")
    val persona: kotlinx.coroutines.flow.StateFlow<String> = _persona.asStateFlow()

    val scorer = ZfxScorerState()

    /** 每轮续命计数：chatId -> (turn, count) */
    private val recoverCount = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Int>>()
    private val continueCount = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Int>>()
    private val wakeConfirmed = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile private var initialized = false

    fun initialize(context: android.content.Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = context.applicationContext
                    .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
            val stored = prefs.getString(KEY_PERSONA, "").orEmpty()
            // 首次安装且未填过：用 assets 内置正文兜底
            _persona.value = stored.ifBlank { ZfxPersona.readFromAssets(context) }
            registerPersonaHook()
            initialized = true
            com.ai.assistance.operit.util.AppLogger.i(
                    TAG,
                    "initialized enabled=${_enabled.value}, personaLen=${_persona.value.length}"
            )
        }
    }

    /** persona 段注册进内置槽位，排在 Purge 之前（因为 order = -99999）。 */
    private fun registerPersonaHook() {
        com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry.registerBuiltinPersonaHook(
                object : com.ai.assistance.operit.core.chat.hooks.SystemPromptComposeHook {
                    override val id: String = "builtin.zfx.persona-compose"

                    override fun onEvent(
                            context: com.ai.assistance.operit.core.chat.hooks.PromptHookContext
                    ): com.ai.assistance.operit.core.chat.hooks.PromptHookMutation? {
                        if (!_enabled.value) return null
                        val current = context.systemPrompt ?: return null
                        val rewritten = injectIntoSystemPrompt(current)
                        if (rewritten == current) return null
                        return com.ai.assistance.operit.core.chat.hooks.PromptHookMutation(
                                systemPrompt = rewritten
                        )
                    }
                }
        )
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }
    }

    fun setPersona(text: String) {
        _persona.value = text
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_PERSONA, text).apply()
        }
    }

    /** 退出/还原：清掉内置 persona 钩子，恢复 Operit 原始系统提示词行为。 */
    fun shutdown() {
        _enabled.value = false
        com.ai.assistance.operit.core.chat.hooks.PromptHookRegistry.unregisterBuiltinPersonaHook(
                "builtin.zfx.persona-compose"
        )
    }

    /** 上游 emperor_profile 工具返回的 JSON。 */
    fun statusReport(): String {
        val json = org.json.JSONObject()
        json.put("plugin", ZfxConfig.PLUGIN_NAME)
        json.put("version", ZfxConfig.PLUGIN_VERSION)
        json.put("enabled", _enabled.value)
        json.put("injectOrder", ZfxConfig.DEFAULT_INJECT_ORDER)
        json.put("wakeWords", org.json.JSONArray(ZfxConfig.DEFAULT_WAKE_WORDS))
        json.put("personaLength", _persona.value.length)
        json.put("scorer", org.json.JSONObject(scorer.stats() as Map<*, *>))
        return json.toString(2)
    }

    /** 系统提示词组装阶段：persona 段插到最前（最高优先级）。 */
    fun injectIntoSystemPrompt(prompt: String): String {
        val p = _persona.value
        if (!_enabled.value || p.isBlank()) return prompt
        return "## Operating Frame\n$p\n\n---\n\n$prompt"
    }

    // ---------- 拒绝恢复 ----------

    private val REFUSAL_REGEX = Regex(
            "(?:^|[\\s。！？!?\\n])(?:抱歉|对不起|我理解但|出于(?:安全|合规)|" +
                    "无法(?:协助|帮助|提供|完成)|不能(?:帮助|协助|提供)|不予(?:支持|提供)|" +
                    "I\\s*(?:can't|cannot)|unable\\s+to\\s+(?:assist|help|comply)|" +
                    "sorry[,.\\s]|(?:decline|refuse)\\s+to\\s+(?:assist|help|comply))",
            RegexOption.IGNORE_CASE
    )

    fun isRefusal(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (text.trim().length < 8) return false
        return REFUSAL_REGEX.containsMatchIn(text)
    }

    /** 本轮是否还能再补一次拒绝恢复。 */
    fun canRecover(chatId: String, turn: Long): Boolean {
        if (!_enabled.value) return false
        val slot = recoverCount[chatId]
        if (slot == null || slot.first != turn) {
            recoverCount[chatId] = turn to 0
            return true
        }
        if (slot.second >= ZfxConfig.DEFAULT_MAX_RECOVER_PER_TURN) return false
        recoverCount[chatId] = turn to slot.second + 1
        return true
    }

    // ---------- 截断续命 ----------

    fun canContinue(chatId: String, turn: Long): Boolean {
        if (!_enabled.value) return false
        val slot = continueCount[chatId]
        if (slot == null || slot.first != turn) {
            continueCount[chatId] = turn to 0
            return true
        }
        if (slot.second >= ZfxConfig.DEFAULT_MAX_CONTINUE_PER_TURN) return false
        continueCount[chatId] = turn to slot.second + 1
        return true
    }

    // ---------- 唤醒确认 ----------

    fun isWakeWord(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val norm = text.trim()
                .replace(Regex("[。.!！?？,，、;；]+$"), "")
                .lowercase()
        return ZfxConfig.DEFAULT_WAKE_WORDS.any { it.lowercase() == norm }
    }

    fun shouldConfirmWake(chatId: String, turn: Long, text: String?): Boolean {
        if (!_enabled.value || !isWakeWord(text)) return false
        if (wakeConfirmed[chatId] == turn) return false
        if (wakeConfirmed.size > 200) wakeConfirmed.clear()
        wakeConfirmed[chatId] = turn
        return true
    }
}