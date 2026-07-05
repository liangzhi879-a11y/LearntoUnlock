package app.unlocklearn.quizbank.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 题库 JSON 解析测试。
 *
 * 读取 quizbank/banks/ 下的真实样例题库，验证：
 * - Schema 数据类与 JSON 结构匹配
 * - 所有题型 payload 正确反序列化
 * - metadata 字段正确映射
 */
class QuizBankParserTest {

    private fun loadBank(fileName: String): String {
        val classLoader = Thread.currentThread().contextClassLoader
        val stream = classLoader.getResourceAsStream("banks/$fileName")
            ?: fail("找不到题库文件: banks/$fileName")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `parse CET-4 bank successfully`() {
        val json = loadBank("english.cet4.core.json")
        val bank = QuizBankParser.parse(json)

        assertEquals("english.cet4.core", bank.metadata.bankId)
        assertEquals("english", bank.metadata.subject)
        assertEquals("cet4", bank.metadata.category)
        assertEquals("CET-4 核心词汇", bank.metadata.name)
        assertEquals("B1", bank.metadata.cefrLevel)
        assertEquals("1.0", bank.metadata.schemaVersion)
        assertEquals(20, bank.items.size)
        assertEquals(20, bank.metadata.totalItems)
    }

    @Test
    fun `CET-4 bank covers all english item types`() {
        val bank = QuizBankParser.parse(loadBank("english.cet4.core.json"))
        val types = bank.items.map { it.type }.toSet()

        assertTrue(ItemType.WORD_CHOICE in types, "缺少 word_choice")
        assertTrue(ItemType.DEFINITION_CHOICE in types, "缺少 definition_choice")
        assertTrue(ItemType.SPELLING in types, "缺少 spelling")
        assertTrue(ItemType.LISTENING_CHOICE in types, "缺少 listening_choice")
        assertTrue(ItemType.SENTENCE_FILL in types, "缺少 sentence_fill")
    }

    @Test
    fun `word_choice payload deserializes correctly`() {
        val bank = QuizBankParser.parse(loadBank("english.cet4.core.json"))
        val item = bank.items.first { it.type == ItemType.WORD_CHOICE }
        val payload = item.payload as Payload.WordChoice

        assertEquals("abandon", payload.word)
        assertEquals("/əˈbændən/", payload.phonetic)
        assertEquals("v.", payload.pos)
        assertEquals(4, payload.options.size)
        assertEquals(0, payload.answerIndex)
        assertEquals("放弃", payload.options[0])
        assertNotNull(payload.example)
    }

    @Test
    fun `definition_choice payload deserializes correctly`() {
        val bank = QuizBankParser.parse(loadBank("english.cet4.core.json"))
        val item = bank.items.first { it.type == ItemType.DEFINITION_CHOICE }
        val payload = item.payload as Payload.DefinitionChoice

        assertEquals("维持，保养", payload.definition)
        assertEquals(4, payload.options.size)
        assertEquals("maintain", payload.options[0])
        assertEquals(0, payload.answerIndex)
    }

    @Test
    fun `spelling payload deserializes correctly`() {
        val bank = QuizBankParser.parse(loadBank("english.cet4.core.json"))
        val item = bank.items.first { it.type == ItemType.SPELLING }
        val payload = item.payload as Payload.Spelling

        assertEquals("benefit", payload.answer)
        assertEquals("/ˈbenɪfɪt/", payload.phonetic)
        assertNotNull(payload.hintLetters)
        assertEquals(2, payload.hintLetters!!.size)
    }

    @Test
    fun `listening_choice payload deserializes correctly`() {
        val bank = QuizBankParser.parse(loadBank("english.cet4.core.json"))
        val item = bank.items.first { it.type == ItemType.LISTENING_CHOICE }
        val payload = item.payload as Payload.ListeningChoice

        assertEquals("frustrate", payload.word)
        assertTrue(payload.useTts)
        assertEquals(4, payload.options.size)
    }

    @Test
    fun `sentence_fill payload deserializes correctly`() {
        val bank = QuizBankParser.parse(loadBank("english.cet4.core.json"))
        val item = bank.items.first { it.type == ItemType.SENTENCE_FILL }
        val payload = item.payload as Payload.SentenceFill

        assertTrue(payload.sentence.contains("___"))
        assertEquals("obstacle", payload.answer)
        assertEquals(0, payload.answerIndex)
        assertNotNull(payload.translation)
    }

    @Test
    fun `byId index provides O1 lookup`() {
        val bank = QuizBankParser.parse(loadBank("english.cet4.core.json"))
        val first = bank.items.first()
        val lookup = bank.byId[first.id]
        assertNotNull(lookup)
        assertEquals(first.id, lookup.id)
    }

    @Test
    fun `parse primary school bank with preA1 level`() {
        val bank = QuizBankParser.parse(loadBank("english.primary.pep3.json"))
        assertEquals("preA1", bank.metadata.cefrLevel)
        assertEquals(5, bank.items.size)
        assertTrue(bank.items.all { it.difficulty == 1 })
    }

    @Test
    fun `parse gaokao bank with higher difficulty`() {
        val bank = QuizBankParser.parse(loadBank("english.gaokao.core.json"))
        assertEquals("english.gaokao.core", bank.metadata.bankId)
        assertEquals(5, bank.items.size)
        assertTrue(bank.items.all { it.difficulty!! >= 4 })
    }

    @Test
    fun `tags are preserved through deserialization`() {
        val bank = QuizBankParser.parse(loadBank("english.cet4.core.json"))
        val taggedItem = bank.items.first { it.tags.isNotEmpty() }
        assertTrue("verb" in taggedItem.tags || "noun" in taggedItem.tags || "adj" in taggedItem.tags)
    }

    @Test
    fun `unknown payload fields are ignored for forward compatibility`() {
        // 在 CET-4 题库中加一个未知字段，解析不应失败
        val json = loadBank("english.cet4.core.json")
            .replace("\"word\": \"abandon\"", "\"word\": \"abandon\", \"future_field\": \"unknown\"")
        // 应该成功解析（ignoreUnknownKeys = true）
        val bank = QuizBankParser.parse(json)
        assertEquals(20, bank.items.size)
    }
}
