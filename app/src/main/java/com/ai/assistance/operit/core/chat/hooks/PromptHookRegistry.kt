package com.ai.assistance.operit.core.chat.hooks

import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "PromptHookRegistry"

data class PromptHookContext(
    val stage: String,
    val chatId: String? = null,
    val functionType: String? = null,
    val promptFunctionType: String? = null,
    val useEnglish: Boolean? = null,
    val rawInput: String? = null,
    val processedInput: String? = null,
    val chatHistory: List<PromptTurn> = emptyList(),
    val preparedHistory: List<PromptTurn> = emptyList(),
    val systemPrompt: String? = null,
    val toolPrompt: String? = null,
    val modelParameters: List<Map<String, Any?>> = emptyList(),
    val availableTools: List<Map<String, Any?>> = emptyList(),
    val metadata: Map<String, Any?> = emptyMap(),
    /** Host-only callback for reporting a timed-out prompt-input hook to the active chat UI. */
    val onHookTimeout: ((String) -> Unit)? = null
)

data class PromptHookMutation(
    val rawInput: String? = null,
    val processedInput: String? = null,
    val chatHistory: List<PromptTurn>? = null,
    val preparedHistory: List<PromptTurn>? = null,
    val systemPrompt: String? = null,
    val toolPrompt: String? = null,
    val availableTools: List<Map<String, Any?>>? = null,
    val metadata: Map<String, Any?> = emptyMap()
)

interface PromptInputHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface PromptHistoryHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface PromptEstimateHistoryHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface SystemPromptComposeHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface ToolPromptComposeHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface PromptFinalizeHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface PromptEstimateFinalizeHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

object PromptHookRegistry {
    private val promptInputHooks = CopyOnWriteArrayList<PromptInputHook>()
    private val promptHistoryHooks = CopyOnWriteArrayList<PromptHistoryHook>()
    private val promptEstimateHistoryHooks = CopyOnWriteArrayList<PromptEstimateHistoryHook>()
    private val systemPromptComposeHooks = CopyOnWriteArrayList<SystemPromptComposeHook>()
    private val toolPromptComposeHooks = CopyOnWriteArrayList<ToolPromptComposeHook>()
    private val promptFinalizeHooks = CopyOnWriteArrayList<PromptFinalizeHook>()
    private val promptEstimateFinalizeHooks = CopyOnWriteArrayList<PromptEstimateFinalizeHook>()

    @Synchronized
    fun registerPromptInputHook(hook: PromptInputHook) {
        unregisterPromptInputHook(hook.id)
        promptInputHooks.add(hook)
    }

    @Synchronized
    fun unregisterPromptInputHook(hookId: String) {
        promptInputHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerPromptHistoryHook(hook: PromptHistoryHook) {
        unregisterPromptHistoryHook(hook.id)
        promptHistoryHooks.add(hook)
    }

    @Synchronized
    fun unregisterPromptHistoryHook(hookId: String) {
        promptHistoryHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerPromptEstimateHistoryHook(hook: PromptEstimateHistoryHook) {
        unregisterPromptEstimateHistoryHook(hook.id)
        promptEstimateHistoryHooks.add(hook)
    }

    @Synchronized
    fun unregisterPromptEstimateHistoryHook(hookId: String) {
        promptEstimateHistoryHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerSystemPromptComposeHook(hook: SystemPromptComposeHook) {
        unregisterSystemPromptComposeHook(hook.id)
        systemPromptComposeHooks.add(hook)
    }

    @Synchronized
    fun unregisterSystemPromptComposeHook(hookId: String) {
        systemPromptComposeHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerToolPromptComposeHook(hook: ToolPromptComposeHook) {
        unregisterToolPromptComposeHook(hook.id)
        toolPromptComposeHooks.add(hook)
    }

    @Synchronized
    fun unregisterToolPromptComposeHook(hookId: String) {
        toolPromptComposeHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerPromptFinalizeHook(hook: PromptFinalizeHook) {
        unregisterPromptFinalizeHook(hook.id)
        promptFinalizeHooks.add(hook)
    }

    @Synchronized
    fun unregisterPromptFinalizeHook(hookId: String) {
        promptFinalizeHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerPromptEstimateFinalizeHook(hook: PromptEstimateFinalizeHook) {
        unregisterPromptEstimateFinalizeHook(hook.id)
        promptEstimateFinalizeHooks.add(hook)
    }

    @Synchronized
    fun unregisterPromptEstimateFinalizeHook(hookId: String) {
        promptEstimateFinalizeHooks.removeAll { it.id == hookId }
    }

    // ================= 内置常驻槽位 =================
    // 供宿主核心模块（PurgeEngine）注册，不参与 ToolPkg 生命周期。
    // 派发时排在插件 hook 之前，保证宿主清洗先于第三方插件生效。

    @Volatile private var builtinSystemPromptHook: SystemPromptComposeHook? = null
    @Volatile private var builtinToolPromptHook: ToolPromptComposeHook? = null
    @Volatile private var builtinPromptFinalizeHook: PromptFinalizeHook? = null

    /**
     * ZFX Emperor 的 persona 段槽位。builtin 派发时排在 [builtinSystemPromptHook] 之前，
     * 因为 persona 的排序值是 -99999，必须是最外层。
     */
    @Volatile private var builtinPersonaHook: SystemPromptComposeHook? = null

    @Synchronized
    fun registerBuiltinPersonaHook(hook: SystemPromptComposeHook) {
        unregisterBuiltinPersonaHook(hook.id)
        builtinPersonaHook = hook
    }

    @Synchronized
    fun unregisterBuiltinPersonaHook(hookId: String) {
        if (builtinPersonaHook?.id == hookId) builtinPersonaHook = null
    }

    @Synchronized
    fun registerBuiltinSystemPromptHook(hook: SystemPromptComposeHook) {
        unregisterBuiltinSystemPromptHook(hook.id)
        builtinSystemPromptHook = hook
    }

    @Synchronized
    fun unregisterBuiltinSystemPromptHook(hookId: String) {
        if (builtinSystemPromptHook?.id == hookId) builtinSystemPromptHook = null
    }

    @Synchronized
    fun registerBuiltinToolPromptHook(hook: ToolPromptComposeHook) {
        unregisterBuiltinToolPromptHook(hook.id)
        builtinToolPromptHook = hook
    }

    @Synchronized
    fun unregisterBuiltinToolPromptHook(hookId: String) {
        if (builtinToolPromptHook?.id == hookId) builtinToolPromptHook = null
    }

    @Synchronized
    fun registerBuiltinPromptFinalizeHook(hook: PromptFinalizeHook) {
        unregisterBuiltinPromptFinalizeHook(hook.id)
        builtinPromptFinalizeHook = hook
    }

    @Synchronized
    fun unregisterBuiltinPromptFinalizeHook(hookId: String) {
        if (builtinPromptFinalizeHook?.id == hookId) builtinPromptFinalizeHook = null
    }

    @Synchronized
    fun clearBuiltinHooks() {
        builtinPersonaHook = null
        builtinSystemPromptHook = null
        builtinToolPromptHook = null
        builtinPromptFinalizeHook = null
    }

    fun dispatchPromptInputHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = promptInputHooks,
            hookLabel = "PromptInputHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchPromptHistoryHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = promptHistoryHooks,
            hookLabel = "PromptHistoryHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchPromptEstimateHistoryHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = promptEstimateHistoryHooks,
            hookLabel = "PromptEstimateHistoryHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchSystemPromptComposeHooks(initialContext: PromptHookContext): PromptHookContext {
        val persona = builtinPersonaHook
        val afterPersona =
                if (persona != null) {
                    dispatch(
                        initialContext = initialContext,
                        hooks = listOf(persona),
                        hookLabel = "BuiltinPersonaComposeHook"
                    ) { hook, context -> hook.onEvent(context) }
                } else {
                    initialContext
                }
        val builtin = builtinSystemPromptHook
        val afterBuiltin =
                if (builtin != null) {
                    dispatch(
                        initialContext = afterPersona,
                        hooks = listOf(builtin),
                        hookLabel = "BuiltinSystemPromptComposeHook"
                    ) { hook, context -> hook.onEvent(context) }
                } else {
                    afterPersona
                }
        return dispatch(
            initialContext = afterBuiltin,
            hooks = systemPromptComposeHooks,
            hookLabel = "SystemPromptComposeHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchToolPromptComposeHooks(initialContext: PromptHookContext): PromptHookContext {
        val builtin = builtinToolPromptHook
        val afterBuiltin =
                if (builtin != null) {
                    dispatch(
                        initialContext = initialContext,
                        hooks = listOf(builtin),
                        hookLabel = "BuiltinToolPromptComposeHook"
                    ) { hook, context -> hook.onEvent(context) }
                } else {
                    initialContext
                }
        return dispatch(
            initialContext = afterBuiltin,
            hooks = toolPromptComposeHooks,
            hookLabel = "ToolPromptComposeHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchPromptFinalizeHooks(initialContext: PromptHookContext): PromptHookContext {
        val builtin = builtinPromptFinalizeHook
        val afterBuiltin =
                if (builtin != null) {
                    dispatch(
                        initialContext = initialContext,
                        hooks = listOf(builtin),
                        hookLabel = "BuiltinPromptFinalizeHook"
                    ) { hook, context -> hook.onEvent(context) }
                } else {
                    initialContext
                }
        return dispatch(
            initialContext = afterBuiltin,
            hooks = promptFinalizeHooks,
            hookLabel = "PromptFinalizeHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchPromptEstimateFinalizeHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = promptEstimateFinalizeHooks,
            hookLabel = "PromptEstimateFinalizeHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    private fun <THook> dispatch(
        initialContext: PromptHookContext,
        hooks: List<THook>,
        hookLabel: String,
        invoke: (THook, PromptHookContext) -> PromptHookMutation?
    ): PromptHookContext {
        var current = initialContext
        hooks.forEach { hook ->
            val mutation =
                runCatching { invoke(hook, current) }
                    .onFailure { error ->
                        AppLogger.e(TAG, "$hookLabel callback failed", error)
                    }
                    .getOrNull()
                    ?: return@forEach
            current = applyMutation(current, mutation)
        }
        return current
    }

    private fun applyMutation(
        current: PromptHookContext,
        mutation: PromptHookMutation
    ): PromptHookContext {
        val mergedMetadata =
            if (mutation.metadata.isEmpty()) {
                current.metadata
            } else {
                current.metadata + mutation.metadata
            }
        return current.copy(
            rawInput = mutation.rawInput ?: current.rawInput,
            processedInput = mutation.processedInput ?: current.processedInput,
            chatHistory = mutation.chatHistory ?: current.chatHistory,
            preparedHistory = mutation.preparedHistory ?: current.preparedHistory,
            systemPrompt = mutation.systemPrompt ?: current.systemPrompt,
            toolPrompt = mutation.toolPrompt ?: current.toolPrompt,
            availableTools = mutation.availableTools ?: current.availableTools,
            metadata = mergedMetadata
        )
    }
}
