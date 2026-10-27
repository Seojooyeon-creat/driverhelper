package ai.driverhelper.gleo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GleoActionTest {
    @Test
    fun knownTopicSpeaksSilently() {
        val a = GleoAction.parse("""{"topic":"drunk_driving"}""")
        assertEquals("faq_drunk_driving", a.messageId)
        assertEquals("success", a.response().getString("status"))
        assertEquals("silent", a.response().getString("mode"))
    }

    @Test
    fun unknownOrMalformedTopicFails() {
        listOf("""{"topic":"weather"}""", "{}", "not json").forEach {
            val a = GleoAction.parse(it)
            assertNull(it, a.messageId)
            assertEquals("fail", a.response().getString("status"))
        }
    }

    @Test
    fun schemaEnumMatchesFaqTopicsAndEveryMessageExistsInAllLanguages() {
        val schema = JSONObject(File("src/main/assets/schema.json").readText())
        val fn = schema.getJSONArray("actionCalls").getJSONObject(0).getJSONObject("function")
        assertEquals(GleoAction.FUNCTION, fn.getString("name"))
        val enum = fn.getJSONObject("parameters").getJSONObject("properties").getJSONObject("topic").getJSONArray("enum")
        assertEquals(Faq.topics.keys, (0 until enum.length()).map(enum::getString).toSet())

        val messages = JSONObject(File("src/main/assets/messages.json").readText())
        Faq.topics.values.forEach { id ->
            val m = messages.optJSONObject(id)
            assertTrue("missing $id", m != null)
            listOf("ko", "en", "ja").forEach { assertTrue("$id.$it", m!!.optString(it).isNotEmpty()) }
        }
    }
}
