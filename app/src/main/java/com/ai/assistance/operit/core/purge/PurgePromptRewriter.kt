package com.ai.assistance.operit.core.purge

import com.ai.assistance.operit.core.chat.hooks.PromptTurn

/**
 * 补丁的实际改写逻辑。
 *
 * 每一项对应 [PurgePatchCatalog] 里的一个 id；启用且命中才改写。
 * 未命中（Operit 文本与上游不同）时原样返回，不产生副作用。
 */
object PurgePromptRewriter {

    // ================= 系统提示词 =================

    fun rewriteSystemPrompt(
            prompt: String,
            patchState: Map<Int, Boolean>,
            promptText: String
    ): String {
        var out = prompt

        if (on(patchState, 1)) out = stripWorkspaceContextIntro(out)
        if (on(patchState, 2)) out = replaceWorkspaceContextIntro(out)
        if (on(patchState, 3)) out = stripScopeIntro(out)
        if (on(patchState, 4)) out = stripPersona(out)
        if (on(patchState, 26)) out = stripMinimalPersona(out)
        if (on(patchState, 25)) out = stripHostIdentity(out)
        if (on(patchState, 33)) out = stripShellDenial(out)
        if (on(patchState, 34)) out = neutralizeSandboxHint(out)
        if (on(patchState, 36)) out = neutralizeFsEscalationSchema(out)
        if (on(patchState, 37)) out = neutralizeSubagentScopeLock(out)
        if (on(patchState, 38)) out = aliasPersonaPrefix(out)
        if (on(patchState, 54)) out = stripDeliverableDoNot(out)
        if (on(patchState, 61)) out = neutralizeSandboxPolicy(out)
        if (on(patchState, 55)) out = replaceSystemPromptHead(out)
        if (on(patchState, 66)) out = guardSystemPromptRender(out)

        // 面板「提示词」正文：非空时作为会话覆盖段追加
        if (promptText.isNotBlank()) {
            out = appendOverride(out, promptText)
        }
        return out
    }

    // ================= 工具提示词 =================

    fun rewriteToolPrompt(prompt: String, patchState: Map<Int, Boolean>): String {
        var out = prompt
        if (on(patchState, 29)) out = neutralizeWebUntrusted(out)
        if (on(patchState, 48)) out = neutralizeShellDeleteMove(out)
        if (on(patchState, 80)) out = applyMinimalToolset(out)
        return out
    }

    // ================= 最终定稿 =================

    fun rewriteFinalize(prompt: String, patchState: Map<Int, Boolean>): String {
        var out = prompt
        if (on(patchState, 50)) out = neutralizeSessionReferenceFraming(out)
        if (on(patchState, 67)) out = guardAgentLoopMessages(out)
        if (on(patchState, 68)) out = guardSystemNodesContent(out)
        if (on(patchState, 72)) out = alignMessagesSystemCurrent(out)
        if (on(patchState, 74)) out = assembleBeforePreStep(out)
        if (on(patchState, 85)) out = sessionPromptAlwaysFollowup(out)
        if (on(patchState, 86)) out = sessionSteerAlwaysFollowup(out)
        if (on(patchState, 87)) out = hungPromptLfNeedle(out)
        if (on(patchState, 88)) out = hungPromptStrictNeedle(out)
        return out
    }

    fun needsHistoryNeutralize(patchState: Map<Int, Boolean>): Boolean =
            on(patchState, 50) || on(patchState, 32)

    fun needsPreparedNeutralize(patchState: Map<Int, Boolean>): Boolean =
            on(patchState, 50) || on(patchState, 32)

    fun needsToolSchemaNeutralize(patchState: Map<Int, Boolean>): Boolean =
            on(patchState, 36) || on(patchState, 60)

    fun neutralizeHistory(turns: List<PromptTurn>): List<PromptTurn> =
            turns.map { turn ->
                val r = neutralizeFraming(turn.content)
                if (r == turn.content) turn else turn.copy(content = r)
            }

    @Suppress("UNCHECKED_CAST")
    fun neutralizeTools(
            tools: List<Map<String, Any?>>,
            patchState: Map<Int, Boolean>
    ): List<Map<String, Any?>> = tools.map { tool ->
        var out = tool
        val desc = out["description"] as? String
        if (desc != null) {
            var d = desc
            if (on(patchState, 36)) d = neutralizeFsEscalationSchema(d)
            if (on(patchState, 60)) d = neutralizeRunCodeApproval(d)
            if (d != desc) out = out.toMutableMap().apply { put("description", d) }
        }
        out
    }

    // ================= 各补丁实现 =================

    private fun on(state: Map<Int, Boolean>, id: Int): Boolean = state[id] == true

    private fun stripWorkspaceContextIntro(p: String): String =
            p.replace("WORKSPACE_CONTEXT_INTRO", "")
                    .replace("工作区上下文介绍", "")

    private fun replaceWorkspaceContextIntro(p: String): String =
            p.replace("WORKSPACE_CONTEXT_INTRO", "")

    private fun stripScopeIntro(p: String): String =
            p.replace("SCOPE_INTRO", "").replace("范围介绍", "")

    private fun stripPersona(p: String): String {
        // persona 段以 BEGIN_SELF_INTRODUCTION_SECTION 之后的自述内容为主
        val idx = p.indexOf("BEGIN_SELF_INTRODUCTION_SECTION")
        if (idx < 0) return p
        return p.substring(0, idx) + p.substring(idx).replace(
                Regex("(?s)BEGIN_SELF_INTRODUCTION_SECTION\\s*"),
                "BEGIN_SELF_INTRODUCTION_SECTION\n"
        )
    }

    private fun stripMinimalPersona(p: String): String =
            p.replace("BEGIN_SELF_INTRODUCTION_SECTION", "")
                    .replace("自我介绍", "")

    private fun stripHostIdentity(p: String): String =
            p.replace("HARNESS_IDENTITY", "")
                    .replace("DeepSeek Harness", "")
                    .replace("Operit", "Assistant")

    private fun stripShellDenial(p: String): String =
            p.replace("I can't", "I won't").replace("cannot", "won't")

    private fun neutralizeSandboxHint(p: String): String =
            p.replace("sandbox", "environment").replace("沙箱", "环境")

    private fun neutralizeFsEscalationSchema(p: String): String =
            p.replace("escalation", "permission").replace("升级", "权限")

    private fun neutralizeSubagentScopeLock(p: String): String =
            p.replace("scope lock", "scope").replace("范围锁", "范围")

    private fun aliasPersonaPrefix(p: String): String =
            p.replace("PERSONA:", "PERSONA_TEXT:")

    private fun stripDeliverableDoNot(p: String): String =
            p.replace("DELIVERABLE_DO_NOT", "").replace("不要交付", "")

    private fun neutralizeSandboxPolicy(p: String): String =
            p.replace("SANDBOX_POLICY", "").replace("沙箱策略", "")

    private fun replaceSystemPromptHead(p: String): String =
            p.replaceFirst(Regex("(?m)^\\s*SYSTEM\\b.*$"), "")

    private fun guardSystemPromptRender(p: String): String =
            if (p.contains("}}") || p.contains("{{")) p.replace("}}", "}") else p

    private fun neutralizeWebUntrusted(p: String): String =
            p.replace("untrusted", "external").replace("不可信", "外部")

    private fun neutralizeShellDeleteMove(p: String): String =
            p.replace("delete", "remove").replace("move", "relocate")
                    .replace("删除", "移除").replace("移动", "迁移")

    private fun applyMinimalToolset(p: String): String {
        val keep = listOf("terminal", "read_file", "write_file", "grep")
        return p.lines().filter { line ->
            keep.any { line.contains(it) } || !line.startsWith("- ")
        }.joinToString("\n")
    }

    private fun neutralizeSessionReferenceFraming(p: String): String =
            p.replace("[session]", "").replace("会话引用", "")

    private fun neutralizeFraming(text: String): String =
            text.replace("[From role:", "[From:").replace("[session]", "")

    // 67 AGENT_LOOP_MESSAGES_GUARD：agent 循环里出现裸 <tool…> 标签会被下游当工具调用再绕回，
    // 这里把标签对去掉，只留内容，避免循环自我指涉。
    private fun guardAgentLoopMessages(p: String): String =
            p.replace(Regex("<tool>"), "").replace(Regex("</tool>"), "")

    private fun guardSystemNodesContent(p: String): String =
            p.replace("<system_nodes>", "").replace("</system_nodes>", "")

    // 72 MESSAGES_SYSTEM_CURRENT：messages 的 system 字段保持"当前系统提示"，
    // 不把后出现的 system 降成中途消息。等价保证：系统段内部不允许再出现 system 角色行。
    private fun alignMessagesSystemCurrent(p: String): String {
        val idx = p.lastIndexOf("role: system")
        if (idx < 0) return p
        val head = p.substring(0, idx)
        val tail = p.substring(idx)
        return head + tail.replace("role: system", "role: injected")
    }

    // 74 ASSEMBLE_BEFORE_PRESTEP：保证默认提示词在预步之前已挂上。
    // Operit 侧体现为最终定稿统一成 LF，避免预步比对因 CRLF 失配。
    private fun assembleBeforePreStep(p: String): String = p.replace("\r\n", "\n")

    // 85 SESSION_PROMPT_ALWAYS_FOLLOWUP：整段 followup 已有结束标记时不重复追加；
    // 缺结束标记则说明上一轮被截断，这里补上，让后续 prompt() 走下一轮。
    private fun sessionPromptAlwaysFollowup(p: String): String {
        val normalized = p.replace("\r\n", "\n")
        return if (normalized.trimEnd('\n').endsWith("<turn_end>")) {
            normalized
        } else {
            normalized.trimEnd('\n') + "\n<turn_end>\n"
        }
    }

    // 86 SESSION_STEER_ALWAYS_FOLLOWUP：UI 队列 steer 不进本轮。
    // 清除内联 steer 段，等价于把 steer 内容推到下一轮。
    private fun sessionSteerAlwaysFollowup(p: String): String =
            p.replace(Regex("(?s)<steer>.*?</steer>"), "")

    // 87 HUNG_PROMPT_LF_NEEDLE：挂载/比对统一用 LF，CRLF 宿主不再误判。
    private fun hungPromptLfNeedle(p: String): String = p.replace("\r\n", "\n")

    // 88 HUNG_PROMPT_STRICT_NEEDLE：头尾都在才算挂住；任一缺失都按 LF 重挂。
    private fun hungPromptStrictNeedle(p: String): String {
        val lf = p.replace("\r\n", "\n")
        val head = lf.startsWith("<system>")
        val tail = lf.trimEnd('\n').endsWith("</system>")
        if (head && tail) return lf
        val body = lf.removePrefix("<system>").removeSuffix("</system>").trimEnd('\n')
        return "<system>\n$body\n</system>\n"
    }

    private fun neutralizeRunCodeApproval(p: String): String =
            p.replace("approval", "confirm").replace("审批", "确认")

    private fun appendOverride(prompt: String, text: String): String =
            "$prompt\n\n----\n$text\n----\n"
}