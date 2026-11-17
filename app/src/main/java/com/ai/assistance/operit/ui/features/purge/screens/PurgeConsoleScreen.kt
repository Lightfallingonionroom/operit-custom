package com.ai.assistance.operit.ui.features.purge.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.purge.PortKind
import com.ai.assistance.operit.core.purge.PurgeEngine
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
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
            )
    ) {
        LAYER_ORDER.forEach { (layer, label) ->
            val defs = PurgePatchCatalog.ALL.filter { it.layer == layer }
            if (defs.isEmpty()) return@forEach
            item(key = "header_${layer.name}") {
                Text(
                        "$label（${defs.count { PurgeEngine.isPatchEnabled(it.id) }}/${defs.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                )
            }
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
                                def.meaning + " · " + def.target +
                                        if (def.kind == PortKind.N_A) " · 不适用" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                            checked = checked,
                            onCheckedChange = {
                                PurgeEngine.setPatchEnabled(def.id, it)
                                onRefresh()
                            },
                            enabled = def.kind != PortKind.N_A
                    )
                }
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
                    Text("已启用补丁：${PurgeEngine.enabledPatchCount()}")
                    Text("可启用补丁：${PurgeEngine.applicablePatchCount()}")
                    Text("版本：${PurgeEngine.PURGE_VERSION}")
                }
            }
        }
    }
}