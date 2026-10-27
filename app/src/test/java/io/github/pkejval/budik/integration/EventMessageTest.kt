package io.github.pkejval.budik.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventMessageTest {
    private val message = EventMessage("ringing", 3, "Práce \"ráno\"", "06:30", 1_700_000_000_000)

    @Test
    fun jsonEscapesLabel() {
        val json = message.toJson()
        assertTrue(json, json.startsWith("{\"event\":\"ringing\",\"alarm_id\":3,\"label\":\"Práce \\\"ráno\\\"\""))
        assertTrue(json.contains("\"timestamp\":1700000000000"))
    }

    @Test
    fun rendersPlaceholders() {
        assertEquals("budik/3/ringing", message.render("budik/{alarm_id}/{event}"))
    }

    @Test
    fun urlEncodesValues() {
        assertEquals(
            "https://x/?l=Pr%C3%A1ce+%22r%C3%A1no%22",
            message.render("https://x/?l={label}", urlEncode = true),
        )
    }
}
