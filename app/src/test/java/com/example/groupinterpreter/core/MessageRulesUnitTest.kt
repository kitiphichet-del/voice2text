package com.example.groupinterpreter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageRulesUnitTest {
    @Test fun `keeps explicit Thai speaker label for Chinese message`() {
        val parsed = MessageRules.parse("คุณหวัง: 下午两点出发。")
        assertEquals("คุณหวัง: ", parsed.prefix)
        assertEquals("下午两点出发。", parsed.body)
        assertEquals(Direction.ZH_TO_TH, MessageRules.chooseDirection(Direction.AUTO, parsed.body, null))
        assertEquals("คุณหวัง: ออกเดินทางบ่ายสอง", MessageRules.appendTranslation(parsed, "ออกเดินทางบ่ายสอง "))
    }

    @Test fun `keeps explicit Thai speaker label for Thai message`() {
        val parsed = MessageRules.parse("อาจารย์สมชาย: พรุ่งนี้เดินทางกี่โมง")
        assertEquals("อาจารย์สมชาย: ", parsed.prefix)
        assertEquals(Direction.TH_TO_ZH, MessageRules.chooseDirection(Direction.AUTO, parsed.body, null))
    }

    @Test fun `automatically detects primary Thai and Chinese scripts`() {
        assertEquals(Direction.ZH_TO_TH, MessageRules.chooseDirection(Direction.AUTO, "你好，欢迎大家。", null))
        assertEquals(Direction.TH_TO_ZH, MessageRules.chooseDirection(Direction.AUTO, "สวัสดีครับ 中文", null))
        assertEquals(Direction.ZH_TO_TH, MessageRules.chooseDirection(Direction.AUTO, "你好，欢迎大家，我们下午出发。 ทักทาย", null))
    }

    @Test fun `manual direction takes priority when messages are mixed`() {
        assertEquals(Direction.TH_TO_ZH, MessageRules.chooseDirection(Direction.TH_TO_ZH, "你好", Language.CHINESE))
        assertEquals(Direction.ZH_TO_TH, MessageRules.chooseDirection(Direction.ZH_TO_TH, "สวัสดี", Language.THAI))
    }

    @Test fun `does not treat clock time as a speaker label`() {
        assertEquals("", MessageRules.parse("14:00 คือเวลานัดหมาย").prefix)
    }

    @Test fun `does not invent direction when language is unknown`() {
        assertNull(MessageRules.chooseDirection(Direction.AUTO, "Hello world", null))
    }
}
