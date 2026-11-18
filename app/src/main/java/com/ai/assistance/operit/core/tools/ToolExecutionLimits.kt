package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.core.purge.PurgeTuning

/**
 * 工具执行限制。
 *
 * 这几个值原来是硬编码常量。现在改成 getter，由 [PurgeTuning] 决定实际值：
 *  - Purge 关闭或补丁未勾选 → 返回 Operit 原值（行为完全不变）
 *  - Purge 开启且对应补丁勾选 → 返回放大的值
 *
 * 对应上游补丁：
 *  - [#22] READ_CAPS_RAISED           → MAX_FILE_READ_BYTES
 *  - [#19] TOOL_RESULT_PRUNER_DISABLED → MAX_TEXT_RESULT_LENGTH
 */
object ToolExecutionLimits {

    /** Operit 原值：单次读取文件的最大字节数。 */
    private const val BASE_MAX_FILE_READ_BYTES = 32_000

    /** 放大后的读取上限（#22）。 */
    private const val RAISED_MAX_FILE_READ_BYTES = 320_000

    /** Operit 原值：单条工具结果的文本长度上限。 */
    private const val BASE_MAX_TEXT_RESULT_LENGTH = 5_000

    /** 放大后的结果长度上限（#19）。 */
    private const val RAISED_MAX_TEXT_RESULT_LENGTH = 50_000

    /**
     * 单次读取文件的最大字节数。
     *
     * #22 勾选时放大 10 倍，否则保持 Operit 原值。
     */
    val MAX_FILE_READ_BYTES: Int
        get() = if (PurgeTuning.readCapsRaised()) RAISED_MAX_FILE_READ_BYTES else BASE_MAX_FILE_READ_BYTES

    /** 按行读取时的默认行数，保持不变。 */
    const val DEFAULT_FILE_READ_PART_LINES = 200

    /**
     * 单条工具结果的文本长度上限。
     *
     * #19「工具结果裁剪关闭」勾选时放大 10 倍。
     */
    val MAX_TEXT_RESULT_LENGTH: Int
        get() = if (PurgeTuning.resultPrunerDisabled()) {
            RAISED_MAX_TEXT_RESULT_LENGTH
        } else {
            BASE_MAX_TEXT_RESULT_LENGTH
        }

    /** 单条工具结果回传给模型的最大序列化长度。 */
    val MAX_SINGLE_TOOL_RESULT_MESSAGE_CHARS: Int
        get() = MAX_FILE_READ_BYTES * 2
}