package com.ai.assistance.operit.core.zfx

/**
 * ZFX Emperor 的 persona 正文。
 *
 * 正文来自上游 prompts/square.md（原作者 正方形）。框架本身由 Operit 的
 * Operating Frame 承担，因此这里只保留**可配置的注入槽位**：
 * 默认由用户在面板里填入，或从 assets 读取。
 *
 * 这样设计的原因：square.md 是一份完整的 persona，直接硬编码进 APK 会让
 * "开关 = 恢复到 Operit 原始状态" 的语义变得不可审计。改为可编辑槽位后，
 * 关闭开关即完全旁路，面板上也能看到当前到底注入了什么。
 */
object ZfxPersona {

    /** assets 里的 persona 文件路径（可选内置） */
    const val ASSET_PATH = "zfx/square.md"

    /** 读取 assets 正文；文件不存在返回空串。 */
    fun readFromAssets(context: android.content.Context): String = try {
        context.assets.open(ASSET_PATH).use { it.bufferedReader().readText() }
    } catch (_: Exception) {
        ""
    }

    /**
     * 上游原文的占位说明。面板上显示，提醒用户正文来源。
     */
    const val SOURCE_NOTE =
            "persona 正文来自 zfx-emperor prompts/square.md（原作者 正方形，MIT）"
}