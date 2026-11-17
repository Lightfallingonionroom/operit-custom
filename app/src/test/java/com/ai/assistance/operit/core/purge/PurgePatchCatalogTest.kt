package com.ai.assistance.operit.core.purge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单测：验证补丁目录完整性与改写逻辑的幂等/未命中行为。
 * 不依赖 Android 框架。
 */
class PurgePatchCatalogTest {

    @Test
    fun catalogHas82Entries() {
        assertEquals(82, PurgePatchCatalog.ALL.size)
    }

    @Test
    fun idsMatchUpstreamNumbering() {
        val expected = listOf(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
                21, 22, 23, 24, 25, 26, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38,
                39, 40, 42, 43, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57,
                58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74,
                75, 76, 77, 78, 80, 82, 83, 84, 85, 86, 87, 88
        )
        assertEquals(expected, PurgePatchCatalog.ALL.map { it.id })
    }

    @Test
    fun skippedNumbersAreNotPresent() {
        val ids = PurgePatchCatalog.ALL.map { it.id }.toSet()
        listOf(20, 27, 41, 44, 79, 81).forEach {
            assertFalse("id $it should be skipped upstream", it in ids)
        }
    }

    @Test
    fun layerCounts() {
        val c = PurgePatchCatalog.countByLayer()
        assertEquals(25, c[PurgeLayer.PROMPT])
        assertEquals(42, c[PurgeLayer.CODE])
        assertEquals(8, c[PurgeLayer.TOOL])
        assertEquals(3, c[PurgeLayer.COMPAT])
        assertEquals(82, c.values.sum())
    }

    @Test
    fun kindCounts() {
        val c = PurgePatchCatalog.countByKind()
        assertEquals(82, c.values.sum())
        assertTrue(c[PortKind.N_A]!! > 0)
    }

    @Test
    fun allNamesUnique() {
        val names = PurgePatchCatalog.ALL.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun applicableExcludesNaOnly() {
        PurgePatchCatalog.applicable.forEach {
            assertFalse(it.kind == PortKind.N_A)
        }
        assertEquals(
                PurgePatchCatalog.ALL.count { it.kind != PortKind.N_A },
                PurgePatchCatalog.applicable.size
        )
    }
}

class PurgePromptRewriterTest {

    private val empty = emptyMap<Int, Boolean>()

    @Test
    fun disabledPatchesDoNothing() {
        val src = "WORKSPACE_CONTEXT_INTRO hello"
        assertEquals(src, PurgePromptRewriter.rewriteSystemPrompt(src, empty, ""))
    }

    @Test
    fun workspaceContextIntroStripped() {
        val state = mapOf(1 to true)
        val src = "before WORKSPACE_CONTEXT_INTRO after"
        val out = PurgePromptRewriter.rewriteSystemPrompt(src, state, "")
        assertFalse(out.contains("WORKSPACE_CONTEXT_INTRO"))
        assertTrue(out.contains("before"))
        assertTrue(out.contains("after"))
    }

    @Test
    fun promptTextAppendedWhenNotBlank() {
        val state = mapOf(1 to true)
        val out = PurgePromptRewriter.rewriteSystemPrompt("base", state, "OVERRIDE")
        assertTrue(out.contains("OVERRIDE"))
        assertTrue(out.startsWith("base"))
    }

    @Test
    fun rewriteIsIdempotentForIdempotentPatches() {
        val state = mapOf(1 to true, 3 to true)
        val once = PurgePromptRewriter.rewriteSystemPrompt("WORKSPACE_CONTEXT_INTRO SCOPE_INTRO", state, "")
        val twice = PurgePromptRewriter.rewriteSystemPrompt(once, state, "")
        assertEquals(once, twice)
    }

    @Test
    fun toolPromptNeutralizesUntrusted() {
        val state = mapOf(29 to true)
        val out = PurgePromptRewriter.rewriteToolPrompt("web untrusted content", state)
        assertFalse(out.contains("untrusted"))
        assertTrue(out.contains("external"))
    }

    @Test
    fun finalizeGuardsSystemNodes() {
        val state = mapOf(68 to true)
        val out = PurgePromptRewriter.rewriteFinalize("a <system_nodes>x</system_nodes> b", state)
        assertFalse(out.contains("<system_nodes>"))
        assertTrue(out.contains("a ") && out.contains(" b"))
    }

    @Test
    fun historyNeutralizeOnlyWhenRequested() {
        assertFalse(PurgePromptRewriter.needsHistoryNeutralize(empty))
        assertTrue(PurgePromptRewriter.needsHistoryNeutralize(mapOf(50 to true)))
        assertTrue(PurgePromptRewriter.needsHistoryNeutralize(mapOf(32 to true)))
    }
}