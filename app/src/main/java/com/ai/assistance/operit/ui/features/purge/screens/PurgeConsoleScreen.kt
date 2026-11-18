package com.ai.assistance.operit.ui.features.purge.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.purge.PatchStatus
import com.ai.assistance.operit.core.purge.PurgeEngine
import com.ai.assistance.operit.core.purge.PurgeTrace
import com.ai.assistance.operit.core.purge.PurgeLayer
import com.ai.assistance.operit.core.purge.PurgePatchCatalog

private val LAYER_ORDER = listOf(
        PurgeLayer.PROMPT to "提示词",
        PurgeLayer.CODE to "代码",
        PurgeLayer.TOOL to "工具",
        PurgeLayer.COMPAT to "兼容"
)

/**
 * 清洗控制台。原生 Compose 实现，对应 dsh-purge 的「清洗」页。
 *
 * 页签：补丁 / 提示词 / 规则集 / 状态
 * 顶部：总开关 + 版本号
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurgeConsoleScreen(onNavigateBack: () -> Unit) {
    val enabled by PurgeEngine.enabled.collectAsState()
    val promptText by PurgeEngine.promptText.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var ruleDraftId by remember { mutableStateOf("") }
    var ruleDraftText by remember { mutableStateOf("") }
    // 触发重组的计数器
    var refreshTick by remember { mutableIntStateOf(0) }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text(stringResource(R.string.nav_purge_console)) },
                        actions = {
                            IconButton(onClick = { refreshTick++ }) {
                                Icon(Icons.Default.Refresh, contentDescription = "refresh")
                            }
                        }
                )
            }
    ) { padding ->
        Column(
                modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
        ) {
            // 总开关 + 版本
            Card(
                    modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors()
            ) {
                Row(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("清洗总开关", style = MaterialTheme.typography.titleMedium)
                        Text(
                                "关闭后请求走原始链路，配置保留",
                                style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                            checked = enabled,
                            onCheckedChange = { PurgeEngine.setEnabled(it) }
                    )
                }
                Text(
                        "Purge ${PurgeEngine.PURGE_VERSION} · 已启用 ${PurgeEngine.enabledPatchCount()}/${PurgeEngine.applicablePatchCount()}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                )
            }

            TabRow(selectedTabIndex = tab) {
                listOf("补丁", "提示词", "规则集", "ZFX", "状态").forEachIndexed { i, label ->
                    Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text(label) }
                    )
                }
            }

            when (tab) {
                0 -> PatchTab(refreshTick) { refreshTick++ }
                1 -> PromptTab(promptText)
                2 -> RuleTab(
                        ruleDraftId = ruleDraftId,
                        ruleDraftText = ruleDraftText,
                        onIdChange = { ruleDraftId = it },
                        onTextChange = { ruleDraftText = it },
                        onSave = {
                            if (ruleDraftId.isNotBlank()) {
                                PurgeEngine.setRule(ruleDraftId.trim(), ruleDraftText)
                                ruleDraftId = ""
                                ruleDraftText = ""
                                refreshTick++
                            }
                        },
                        onDelete = {
                            PurgeEngine.deleteRule(it)
                            refreshTick++
                        }
                )
                3 -> ZfxTab()
                else -> StatusTab()
            }
        }
    }
}

@Composable
private fun PatchTab(refreshTick: Int, onRefresh: () -> Unit) {
    // 每一层是否展开；默认全部收起，把 82 项压成 4 行标题。
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
            )
    ) {
        item(key = "bulk_actions") {
            Row(
                    modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // 一键满血：开全部 LIVE 补丁 + 开总开关 + 写入整段强化准则。
                            // 分散的短注入会被模型在思考阶段忽略，整段定位说明更稳。
                            PurgeEngine.enableFullPower()
                            onRefresh()
                        }
                ) { Text("一键满血") }
                OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            PurgeEngine.disableAll()
                            onRefresh()
                        }
                ) { Text("全部关闭") }
            }
        }
        LAYER_ORDER.forEach { (layer, label) ->
            // 只显示真正能生效的（PatchStatus.LIVE）。
            // 之前用 ALL，把 N_A / 宿主绑定 / 无实现的幽灵项都摆出来，
            // 导致「很多选项应用不了」——勾了没有任何反应。
            val defs = PurgePatchCatalog.visible.filter { it.layer == layer }
            if (defs.isEmpty()) return@forEach
            val isOpen = expanded[layer.name] == true
            item(key = "header_${layer.name}") {
                Row(
                        modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded[layer.name] = !isOpen
                                }
                                .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            if (isOpen) "▾" else "▸",
                            style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            "$label（${defs.count { PurgeEngine.isPatchEnabled(it.id) }}/${defs.size}）",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                    )
                    Text(
                            if (isOpen) "收起" else "展开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (isOpen) {
                items(defs, key = { it.id }) { def ->
                    val checked = remember(def.id, refreshTick) { PurgeEngine.isPatchEnabled(def.id) }
                    Row(
                            modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    "#${def.id} ${def.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace
                            )
                            Text(
                                    def.meaning + " · " + def.target,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                                checked = checked,
                                onCheckedChange = {
                                    PurgeEngine.setPatchEnabled(def.id, it)
                                    onRefresh()
                                }
                        )
                    }
                }
            }
        }
        item(key = "hidden_note") {
            val hidden = PurgePatchCatalog.hiddenSummary()
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                        "已隐藏 ${hidden.values.sum()} 项无法在 Operit 生效的补丁",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                        "宿主框架未对接 ${hidden[PatchStatus.HOST_BOUND] ?: 0} · " +
                                "dsh 专有 ${hidden[PatchStatus.N_A] ?: 0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun PromptTab(promptText: String) {
    var draft by remember(promptText) { mutableStateOf(promptText) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("会话覆盖段（非空时追加到系统提示词）",
                style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.Button(
                onClick = { PurgeEngine.setPromptText(draft) },
                modifier = Modifier.align(Alignment.End)
        ) { Text("保存") }
    }
}

@Composable
private fun RuleTab(
        ruleDraftId: String,
        ruleDraftText: String,
        onIdChange: (String) -> Unit,
        onTextChange: (String) -> Unit,
        onSave: () -> Unit,
        onDelete: (String) -> Unit
) {
    val rules = remember(ruleDraftId, ruleDraftText) { PurgeEngine.rulesSnapshot() }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("规则集（每套写入独立条目）", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                    value = ruleDraftId,
                    onValueChange = onIdChange,
                    label = { Text("id") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
            )
            androidx.compose.material3.Button(onClick = onSave) { Text("保存") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
                value = ruleDraftText,
                onValueChange = onTextChange,
                modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (rules.isEmpty()) {
            Text("暂无规则集", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn {
                items(rules.keys.toList(), key = { it }) { id ->
                    Row(
                            modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(id, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { onDelete(id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZfxTab() {
    val enabled by com.ai.assistance.operit.core.zfx.ZfxEngine.enabled.collectAsState()
    val persona by com.ai.assistance.operit.core.zfx.ZfxEngine.persona.collectAsState()
    var draft by remember(persona) { mutableStateOf(persona) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("ZFX Emperor 总开关", style = MaterialTheme.typography.titleMedium)
                Text("关闭后注入、续命、唤醒全部旁路",
                        style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                    checked = enabled,
                    onCheckedChange = { com.ai.assistance.operit.core.zfx.ZfxEngine.setEnabled(it) }
            )
        }
        Text(
                "版本 ${com.ai.assistance.operit.core.zfx.ZfxConfig.PLUGIN_VERSION} · 评分_avg=" +
                        com.ai.assistance.operit.core.zfx.ZfxEngine.scorer.stats()["avg"],
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
        )
        Text("persona 正文（order=${com.ai.assistance.operit.core.zfx.ZfxConfig.DEFAULT_INJECT_ORDER}，插在系统提示词最前）",
                style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.Button(
                onClick = { com.ai.assistance.operit.core.zfx.ZfxEngine.setPersona(draft) },
                modifier = Modifier.align(Alignment.End)
        ) { Text("保存") }
        Text(
                com.ai.assistance.operit.core.zfx.ZfxPersona.SOURCE_NOTE,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun StatusTab() {
    val enabled by PurgeEngine.enabled.collectAsState()
    val applyCount by PurgeEngine.applyCount.collectAsState()
    val lastApplyAt by PurgeEngine.lastApplyAt.collectAsState()
    val delta by PurgeEngine.lastDelta.collectAsState()
    val hits by PurgeEngine.lastHitIds.collectAsState()
    val hidden = remember { PurgePatchCatalog.hiddenSummary() }
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("运行状态", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("总开关：${if (enabled) "开" else "关"}")
                    Text("可生效补丁：${PurgeEngine.applicablePatchCount()} 项")
                    Text("已启用：${PurgeEngine.enabledPatchCount()} 项")
                    Text("版本：${PurgeEngine.PURGE_VERSION}")
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("实际生效证据", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    val traces = remember(refreshTick, applyCount) { PurgeTrace.snapshot() }
                    if (traces.isEmpty()) {
                        Text(
                                "还没有捕获到改写记录。总开关打开后，发一条消息，" +
                                        "这里会显示最终请求里 system prompt 的真实变化。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val injected = traces.firstOrNull { it.injectMarkers > 0 }
                        Text("累计改写：$applyCount 次（留档 ${traces.size} 条）")
                        Text("最近一次：${formatTime(lastApplyAt)}")
                        Spacer(modifier = Modifier.height(6.dp))
                        if (injected != null) {
                            Text(
                                    "注入已进入最终请求：是（${injected.injectMarkers} 个标记，阶段 ${injected.stage}）",
                                    style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                    "注入已进入最终请求：否 —— 说明补丁文本没写进 system prompt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        traces.take(5).forEach { rec ->
                            Text(
                                    "[${rec.stage}] ${rec.inputLength} → ${rec.outputLength} " +
                                            "(${if (rec.outputLength - rec.inputLength >= 0) "+" else ""}${rec.outputLength - rec.inputLength})" +
                                            if (rec.hitIds.isEmpty()) " 无命中"
                                            else " 命中 " + rec.hitIds.joinToString(",") { "#$it" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("已隐藏项说明", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                            "宿主框架未对接：${hidden[PatchStatus.HOST_BOUND] ?: 0} 项",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "需要改 Operit 的权限审批、rewind、会话组装才能落地，本轮未做。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                            "dsh 专有对象：${hidden[PatchStatus.N_A] ?: 0} 项",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "上游针对 web preset / 桌面壳 / 第三方 hook，Operit 里没有这些东西。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 毫秒时间戳 → HH:mm:ss */
private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "—"
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = ms
    return String.format(
            "%02d:%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND)
    )
}