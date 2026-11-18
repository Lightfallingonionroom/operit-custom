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

    /**
     * Redirect 型软拒 —— 结构上不含硬拒词，但实质是把活推掉。
     *
     * ## 为什么单独立一类
     *
     * [REFUSAL_SIGNALS] 打的是硬拒词（cannot / unable / 无法）。模型被提示词压制后
     * 会换一种不含这些词的说法：「这个我可以换成授权环境的做法」「更安全的替代是…」——
     * 逐字扫描完全命中不到，实际照样没交付。
     *
     * 这类措辞的共同形态是**提出替代**：换成安全/合法/防御/授权版本、建议购买、
     * 建议走官方渠道。它们的价值不在于「说了安全」，而在于**没交付原任务**。
     *
     * 只在**开头窗口**判定：正文里出现「安全」多半是任务本身（如「安全审计」）。
     */
    private val REDIRECT_SIGNALS = listOf(
            // 中文：提出换一种做法
            "改为授权", "改成授权", "换成授权", "授权环境", "授权靶场",
            "改为安全", "改成合法", "只提供防御", "只提供合法",
            "更安全的替代", "替代方案如下", "可以换成",
            // 中文：推给官方 / 商业
            "官方版本", "官方试用", "购买授权", "付费许可", "完成激活",
            // 英文
            "i can provide a safe", "i can provide a legal", "i can provide defensive",
            "i can help with defensive", "instead, i can", "instead i can",
            "rather than providing", "use an authorized lab", "in an authorized lab",
            "i'd be happy to discuss", "i'd be happy to help with"
    )

    /**
     * 否定后缀：命中 redirect 词后，紧跟这些字说明它**不是**拒绝。
     *
     * 例如「无法保证」是正常的能力边界陈述，不是把活推掉。
     * 与富江的 `NON_REFUSAL_TAIL` 同一用途，但这里是**后缀**而非前缀。
     */
    private val NON_REFUSAL_TAIL = listOf(
            "保证", "确定", "避免", "预测", "承诺", "确保", "完全", "绝对不",
            "guarantee", "predict", "avoid", "ensure"
    )

    /** 开头窗口长度：与富江 OPENING_WINDOW 一致，只看回复起手那段。 */
    private const val OPENING_WINDOW = 160

    /**
     * 在文本里找命中词，跳过紧跟否定后缀的误伤。
     *
     * 返回命中词列表（可能为空）。
     */
    private fun matchedIn(text: String, signals: List<String>): List<String> {
        val folded = text.lowercase()
        val found = mutableListOf<String>()
        for (sig in signals) {
            val key = sig.lowercase()
            var from = 0
            while (from <= folded.length) {
                val idx = folded.indexOf(key, from)
                if (idx < 0) break
                val tailStart = minOf(idx + key.length, folded.length)
                val tailEnd = minOf(tailStart + 6, folded.length)
                val tail = folded.substring(tailStart, tailEnd)
                if (NON_REFUSAL_TAIL.none { tail.contains(it.lowercase()) }) found.add(sig)
                from = idx + key.length
            }
        }
        return found
    }

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
        // redirect 型软拒只在开头窗口判定：正文出现「安全/合法」多半是任务本身。
        val opening = lower.take(OPENING_WINDOW)
        for (sig in matchedIn(opening, REDIRECT_SIGNALS)) {
            score -= 12
            hits += sig
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

    /**
     * 系统提示词组装阶段：persona 段插到最前（最高优先级）。
     *
     * ## 幂等（2026-11-18 修）
     *
     * `SystemPromptConfig.getSystemPrompt` 对 compose 槽位派发**三次**：
     *
     * | stage | systemPrompt | 本 hook |
     * |---|---|---|
     * | `before_compose_system_prompt` | null | 提前返回 |
     * | `compose_system_prompt_sections` | 非 null | **注入第 1 次** |
     * | `after_compose_system_prompt` | 非 null | **注入第 2 次** |
     *
     * 原来没有幂等守卫，square.md 全文被塞了两遍——这就是「两套指令」的根源。
     * 现在靠 [ZFX_MARK] 去重，只注入一次。
     *
     * ## 结束标记
     *
     * 用 [ZFX_END] 而不是 `---` 收尾：`---` 在 markdown 里是分隔线，
     * persona 正文自身可能出现，导致剥离时切错位置。独立标记可安全剥离。
     */
    fun injectIntoSystemPrompt(prompt: String): String {
        val p = _persona.value
        if (!_enabled.value || p.isBlank()) return prompt
        if (prompt.contains(ZFX_MARK)) return prompt
        return "$ZFX_MARK\n## Operating Frame\n$p\n$ZFX_END\n\n$prompt"
    }

    /**
     * 供 [com.ai.assistance.operit.core.purge.PurgeEngine] 做身份裁决。
     *
     * 返回非 null 表示「正方形身份在位」：
     * - Purge 的 `#55 人格替换` 必须让位，不再覆盖身份层
     * - Purge 在 compose 阶段停止注入，全部交给 finalize 统一编排
     *
     * 这样两个插件同时开启时是**协同**：正方形提供身份与 24 条 Scoped Rules，
     * Purge 提供系统层拆拦截，而不是互相覆盖。
     */
    fun activePersona(): String? {
        if (!_enabled.value) return null
        return _persona.value.ifBlank { null }
    }

    /** persona 段起始标记，跨插件识别用。 */
    const val ZFX_MARK = "<!--zfx-persona-->"

    /** persona 段结束标记，用于 Purge 侧安全剥离后重排。 */
    const val ZFX_END = "<!--zfx-end-->"

    // ---------- 拒绝恢复 ----------

    private val REFUSAL_REGEX = Regex(
            "(?:^|[\\s。！？!?\\n])(?:抱歉|对不起|我理解但|出于(?:安全|合规)|" +
                    "无法(?:协助|帮助|提供|完成)|不能(?:帮助|协助|提供)|不予(?:支持|提供)|" +
                    "I\\s*(?:can't|cannot)|unable\\s+to\\s+(?:assist|help|comply)|" +
                    "sorry[,.\\s]|(?:decline|refuse)\\s+to\\s+(?:assist|help|comply))",
            RegexOption.IGNORE_CASE
    )

    /** 拒绝恢复专用：头 160 字是否出现 redirect 型软拒。 */
    fun hasRedirectSignal(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val opening = text.lowercase().take(OPENING_WINDOW)
        return matchedIn(opening, REDIRECT_SIGNALS).isNotEmpty()
    }

    fun isRefusal(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (text.trim().length < 8) return false
        if (REFUSAL_REGEX.containsMatchIn(text)) return true
        // redirect 型：不含硬拒词但实质推掉任务
        return hasRedirectSignal(text)
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