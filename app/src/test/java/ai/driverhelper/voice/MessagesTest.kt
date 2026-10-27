package ai.driverhelper.voice

import ai.driverhelper.GuidanceSettings
import ai.driverhelper.data.parseContacts
import ai.driverhelper.rule.MessageId
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MessagesTest {
    private val messages = JSONObject(File("src/main/assets/messages.json").readText())

    @Test
    fun everyMessageHasAllThreeLanguages() {
        messages.keys().forEach { id ->
            val m = messages.getJSONObject(id)
            listOf("ko", "en", "ja").forEach { assertTrue("$id.$it", m.optString(it).isNotEmpty()) }
        }
    }

    @Test
    fun everyCategoryHasLabelAndFineDetail() {
        GuidanceSettings.ALL_CATEGORIES.forEach {
            assertTrue(it, messages.has(it))
            assertTrue(MessageId.detail(it), messages.has(MessageId.detail(it)))
        }
    }

    @Test
    fun everyContactHasLabel() {
        parseContacts(File("src/main/assets/contacts.json").readText()).forEach {
            assertTrue(it.id, messages.has("contact_${it.id}"))
        }
    }
}
