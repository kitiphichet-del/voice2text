import com.example.groupinterpreter.core.Direction
import com.example.groupinterpreter.core.Language
import com.example.groupinterpreter.core.MessageRules

fun main() {
    fun checkEqual(expected: Any?, actual: Any?) {
        check(expected == actual) { "expected=[$expected] actual=[$actual]" }
    }

    val chinese = MessageRules.parse("คุณหวัง: 下午两点出发。")
    checkEqual("คุณหวัง: ", chinese.prefix)
    checkEqual("下午两点出发。", chinese.body)
    checkEqual(Direction.ZH_TO_TH, MessageRules.chooseDirection(Direction.AUTO, chinese.body, null))
    checkEqual("คุณหวัง: ออกเดินทางบ่ายสอง", MessageRules.appendTranslation(chinese, "ออกเดินทางบ่ายสอง "))

    val thai = MessageRules.parse("อาจารย์สมชาย: พรุ่งนี้เดินทางกี่โมง")
    checkEqual("อาจารย์สมชาย: ", thai.prefix)
    checkEqual(Direction.TH_TO_ZH, MessageRules.chooseDirection(Direction.AUTO, thai.body, null))

    val plain = MessageRules.parse("你好，欢迎大家。"); checkEqual("", plain.prefix)
    checkEqual(Direction.ZH_TO_TH, MessageRules.chooseDirection(Direction.AUTO, plain.body, null))
    checkEqual(Direction.TH_TO_ZH, MessageRules.chooseDirection(Direction.AUTO, "สวัสดีครับ 中文", null))
    checkEqual(Direction.ZH_TO_TH, MessageRules.chooseDirection(Direction.AUTO, "你好，欢迎大家，我们下午出发。 ทักทาย", null))
    checkEqual(null, MessageRules.chooseDirection(Direction.AUTO, "Hello world", null))
    checkEqual(Direction.TH_TO_ZH, MessageRules.chooseDirection(Direction.TH_TO_ZH, "你好", Language.CHINESE))
    val time = MessageRules.parse("14:00 คือเวลานัดหมาย")
    checkEqual("", time.prefix)
    println("PASS: 13 message parsing, script direction, speaker preservation and override assertions")
}
