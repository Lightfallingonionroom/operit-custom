package com.ai.assistance.operit.core.purge

import com.ai.assistance.operit.util.AppLogger

/**
 * Purge 的实际生效追踪。
 *
 * 背景：之前只能证明「开关打开了」「函数被调用了」，但无法回答最关键的问题——
 * **最终发给模型的 system prompt 里，到底有没有补丁文本**。
 *
 * 破解请求那次测试里，模型直接拒绝了，而当时的 token 统计（12628 输入）证明请求
 * 发出去过。于是分不清两种可能：
 *   A. 注入没进最终请求（代码路径问题）
 *   B. 注入进了最终请求，但模型/服务端按自身策略生成答复（模型侧问题）
 *
 * 这个类就是为了把 A 和 B 分开：每次改写都留下输入/输出长度、hash、命中补丁、
 * marker 计数。状态页直接展示，不再靠猜。
 */
object PurgeTrace {

    private const val TAG = "PurgeTrace"
    private const val MAX_RECORDS = 40

    /** 一次改写记录。 */
    data class Record(
            /** 阶段：compose / compose_sections / after_compose / finalize / tool_prompt */
            val stage: String,
            val inputLength: Int,
            val outputLength: Int,
            val inputHash: String,
            val outputHash: String,
            val changed: Boolean,
            /** 本次实际产生改动的补丁编号 */
            val hitIds: List<Int>,
            /** 输出里 <!--purge-inject--> 标记的数量，>0 说明注入段确实写进去了 */
            val injectMarkers: Int,
            val at: Long = System.currentTimeMillis()
    )

    @Volatile private var records: List<Record> = emptyList()

    /** 最近一次记录（最新在前）。 */
    fun snapshot(): List<Record> = records

    fun latest(): Record? = records.firstOrNull()

    /**
     * 记录一次改写。
     *
     * @param stage 阶段名
     * @param input 改写前文本
     * @param output 改写后文本
     * @param hitIds 本次命中的补丁编号
     */
    fun record(stage: String, input: String, output: String, hitIds: List<Int>) {
        val changed = input != output
        // 只有真的变了才值得留档；否则状态页会被无关阶段刷满
        if (!changed) return
        val rec = Record(
                stage = stage,
                inputLength = input.length,
                outputLength = output.length,
                inputHash = hash(input),
                outputHash = hash(output),
                changed = true,
                hitIds = hitIds.sorted(),
                injectMarkers = countMarkers(output)
        )
        records = (listOf(rec) + records).take(MAX_RECORDS)
        AppLogger.i(
                TAG,
                "stage=$stage in=${rec.inputLength} out=${rec.outputLength} " +
                        "delta=${rec.outputLength - rec.inputLength} " +
                        "hits=${rec.hitIds} markers=${rec.injectMarkers}"
        )
    }

    /** 清空（面板「重置追踪」用）。 */
    fun clear() {
        records = emptyList()
    }

    /** 汇总：某个补丁是否在最近 N 次记录里真的出现过。 */
    fun everHit(id: Int): Boolean = records.any { id in it.hitIds }

    /** 最近一次含注入段的记录，用于判断「注入到底进没进」。 */
    fun lastInjected(): Record? = records.firstOrNull { it.injectMarkers > 0 }

    private fun countMarkers(text: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val i = text.indexOf(INJECT_MARK, from)
            if (i < 0) break
            count++
            from = i + INJECT_MARK.length
        }
        return count
    }

    /** 与 PurgePromptRewriter 保持一致的标记，避免两边漂移。 */
    private const val INJECT_MARK = "<!--purge-inject-->"

    /** 稳定短 hash：长度 + 内容滚动 hash，足够区分不同文本，且不泄漏内容。 */
    private fun hash(text: String): String {
        var h = 1125899906842597L
        for (c in text) {
            h = 31 * h + c.code
        }
        return "%016x".format(h)
    }
}