package com.ai.assistance.operit.terminal

/**
 * 向指定会话投递输入的结果。
 *
 * 调用方（工具层 / UI 层）需要知道输入是否真的写进了目标 PTY，
 * 而不是只在应用侧丢进队列就算成功。
 */
enum class SessionInputResult {
    /** 已写入目标 PTY 的 writer 并 flush 成功。 */
    DELIVERED,

    /** 目标会话不存在或已释放。 */
    SESSION_NOT_FOUND,

    /** 会话存在但 writer 不可用（pty 未就绪或已关闭）。 */
    SESSION_NOT_WRITABLE,

    /** 写 PTY 抛异常。 */
    WRITE_FAILED,

    /** 写 PTY 超过等待上限，调用方应视为未送达。 */
    WRITE_TIMED_OUT,
}
