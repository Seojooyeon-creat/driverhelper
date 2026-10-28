package ai.driverhelper.voice

import ai.driverhelper.GuidanceSettings
import ai.driverhelper.data.Guides
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
            assertTrue("ask_$it", messages.has("ask_" + it.removePrefix("cat_")))
        }
    }

    @Test
    fun everyContactHasLabel() {
        parseContacts(File("src/main/assets/contacts.json").readText()).forEach {
            assertTrue(it.id, messages.has("contact_${it.id}"))
        }
    }

    @Test
    fun everyGuideEntryHasTitleAndBody() {
        val ids = Guides.controls.map { it.id } + Guides.signs.map { it.id } + Guides.telltaleItems.map { it.id } + Guides.checklist
        ids.forEach {
            assertTrue(it, messages.has(it))
            assertTrue("${it}_title", messages.has("${it}_title"))
        }
        Guides.autoExplain.forEach { assertTrue(it, it in Guides.telltales) }
    }
}
