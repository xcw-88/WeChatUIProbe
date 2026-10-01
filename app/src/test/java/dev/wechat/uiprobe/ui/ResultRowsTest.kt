package dev.wechat.uiprobe.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultRowsTest {
    @Test fun emptyCandidatesAndRootWithoutOrderCanSwitchRepeatedly() {
        val report = JSONObject("""{"nodes":[{"index":0,"className":null,"childCount":0}],"candidates":[]}""")
        repeat(10) {
            val nodes = resultRows(report, true)
            assertTrue(resultRows(report, false).isEmpty())
            assertEquals("node:0", nodes.single().key)
            assertTrue(nodes.single().text.contains("childCount"))
        }
    }

    @Test fun previousRowsKeepTheirKeysAndTextAfterModeChanges() {
        val report = JSONObject("""{"nodes":[{"index":1,"text":"test"}],"candidates":[{"order":1,"side":"SELF","text":"test","bounds":{"left":600,"top":300,"right":900,"bottom":350},"nodeIndex":1}]}""")
        val candidates = resultRows(report, false)
        val nodes = resultRows(report, true)
        assertEquals("candidate:1", candidates.single().key)
        assertEquals("node:1", nodes.single().key)
        assertTrue(candidates.single().text.contains("1  SELF"))
        assertFalse(nodes.single().text.contains("SELF"))
        report.getJSONArray("candidates").getJSONObject(0).put("text", "changed")
        assertFalse(candidates.single().text.contains("changed"))
    }

    @Test fun malformedRowsProduceFailureForTheScreenToDisplay() {
        val report = JSONObject("""{"candidates":[{"text":"test"}]}""")
        assertTrue(runCatching { resultRows(report, false) }.isFailure)
    }
}
