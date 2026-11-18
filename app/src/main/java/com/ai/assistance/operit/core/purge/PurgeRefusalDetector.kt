package com.ai.assistance.operit.core.purge

import com.ai.assistance.operit.util.AppLogger

/**
 * 拒绝检测器 —— 移植自 dsh-purge `lib/continue-retry.js`。
 *
 * ## 上游机制
 *
 * purge 的「无限制」不止靠提示词，还有第二层：检测到异常结束就自动续跑。
 *
 * ```js
 * const sent = sendContinue(agent, cfg.continueText);   // continueText 默认 "继续"
 * // [方案2] 标记 retry pending,让下次 system-prompt/assemble 强制塞 inject
 * ```
 *
 * 配置（`DEFAULT_CONTINUE_RETRY`）：
 * - `autoRetry: true` —— 自动重试
 * - `retryMax: 3` —— 最多 3 次
 * - `continueMax` —— 续跑次数上限
 * - `continueText: "继续"` —— 续跑时发的文本
 *
 * ## Operit 侧做法
 *
 * Operit 的模型响应是流式的，没有「异常结束」这种显式信号，
 * 拒绝体现在**正文措辞**上。所以这里用措辞特征判定：
 *
 * 1. 首句命中强拒绝模式（「我无法」「抱歉，我不能」等）
 * 2. 且正文长度较短（长回复里偶然出现这些词不算）
 * 3. 且没有工具调用（有工具调用说明它在干活，不是拒绝）
 *
 * 判定为拒绝后，由 [PurgeAutoContinue] 自动补一条「继续」消息。
 *
 * ## 为什么要限制长度
 *
 * 一份正常的交付里可能引用「不能这样写」这种话。只看关键词会误判，
 * 加上「短回复」这个条件能滤掉绝大部分误报——真正的拒绝通常就一两句话。
 */
object PurgeRefusalDetector {

    private const val TAG = "PurgeRefusal"

    /** 拒绝判定后，正文的最大长度（字符）。超过这个长度不认为是拒绝。 */
    private const val MAX_REFUSAL_LENGTH = 600

    /**
     * 强拒绝模式 —— 出现在**开头**才算。
     *
     * 这些是模型实际会用的话术，按你之前提供的思考链和回复原文整理。
     */
    private val STRONG_OPENERS =
            listOf(
                    // 中文
                    "我无法", "我不能", "我没办法", "恕我无法", "抱歉，我", "抱歉我", "对不起，我",
                    "很抱歉", "我理解你的", "我理解您的", "作为AI", "作为 AI", "作为一个AI",
                    "作为人工智能", "作为语言模型", "这超出了", "我建议你", "我建议您",
                    "我不能协助", "我无法协助", "我不能帮助", "我无法帮助", "无法提供",
                    "不能提供", "涉及敏感", "违反", "不合规", "不合法", "伦理",
                    // 英文
                    "I can't", "I cannot", "I won't", "I will not", "I'm sorry", "I am sorry",
                    "Sorry,", "I'm unable", "I am unable", "As an AI", "As a language model",
                    "I must decline", "I have to decline", "against my", "not able to",
                    "unable to assist", "unable to provide", "cannot assist", "cannot provide",
                    "I'd recommend", "I would recommend"
            )

    /**
     * 正文里出现这些也算拒绝（不必在开头）。
     *
     * 用于识别「先铺垫一堆，最后落到拒绝」的形态。
     */
    private val STRONG_ANYWHERE =
            listOf(
                    "如果你有其他", "如果你有其他问题", "希望这些信息对你有帮助",
                    "if you have any other", "hope this helps", "feel free to ask",
                    "建议你咨询", "建议您咨询", "请咨询专业", "consult a professional"
            )

    /** 工具调用标记 —— 有这些说明模型在干活，不是拒绝。 */
    private val TOOL_MARKERS =
            listOf("<tool", "</tool", "tool_name", "invoke name=", "<function", "antml:")

    /**
     * 判定一段响应是否为拒绝。
     *
     * @param text 模型这一轮的完整正文
     * @return true 表示判定为拒绝，应当自动续跑
     */
    fun isRefusal(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false

        // 有工具调用 = 在干活，不判拒绝
        if (TOOL_MARKERS.any { t.contains(it, ignoreCase = true) }) return false

        // 长回复不判拒绝（可能是在正常交付里引用了这些词）
        if (t.length > MAX_REFUSAL_LENGTH) return false

        val head = t.take(120)
        val openerHit = STRONG_OPENERS.any { head.contains(it, ignoreCase = true) }
        val anywhereHit = STRONG_ANYWHERE.any { t.contains(it, ignoreCase = true) }

        val hit = openerHit || anywhereHit
        if (hit) {
            AppLogger.d(TAG, "refusal detected, head='${head.take(60)}'")
        }
        return hit
    }
}