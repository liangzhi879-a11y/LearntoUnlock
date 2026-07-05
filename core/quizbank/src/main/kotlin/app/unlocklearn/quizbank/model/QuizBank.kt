package app.unlocklearn.quizbank.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * 题库顶层结构。
 * 对应 JSON：{ metadata, items[] }
 */
@Serializable
data class QuizBank(
    val metadata: BankMetadata,
    val items: List<@Serializable(with = ItemSerializer::class) Item>
) {
    /** 按 id 索引，便于调度器按 itemId 取题 */
    val byId: Map<String, Item> by lazy { items.associateBy { it.id } }
}

@Serializable
data class BankMetadata(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("bank_id") val bankId: String,
    val subject: String,
    val category: String,
    val name: String,
    @SerialName("name_en") val nameEn: String? = null,
    @SerialName("cefr_level") val cefrLevel: String? = null,
    @SerialName("total_items") val totalItems: Int,
    val source: String? = null,
    val license: String? = null,
    @SerialName("updated_at") val updatedAt: String
)

/**
 * 单道题目。payload 按 type 分化为不同的 sealed 子类。
 */
@Serializable(with = ItemSerializer::class)
data class Item(
    val id: String,
    val type: ItemType,
    val difficulty: Int? = null,
    val tags: List<String> = emptyList(),
    val payload: Payload
)

@Serializable
enum class ItemType(val jsonName: String) {
    @SerialName("word_choice") WORD_CHOICE("word_choice"),
    @SerialName("definition_choice") DEFINITION_CHOICE("definition_choice"),
    @SerialName("spelling") SPELLING("spelling"),
    @SerialName("listening_choice") LISTENING_CHOICE("listening_choice"),
    @SerialName("sentence_fill") SENTENCE_FILL("sentence_fill"),
    @SerialName("single_choice") SINGLE_CHOICE("single_choice"),
    @SerialName("multi_choice") MULTI_CHOICE("multi_choice"),
    @SerialName("true_false") TRUE_FALSE("true_false"),
    @SerialName("formula_choice") FORMULA_CHOICE("formula_choice");

    companion object {
        fun fromJsonName(s: String): ItemType? =
            values().firstOrNull { it.jsonName == s }
    }
}

/**
 * 题目 payload。每种 ItemType 对应一个子类。
 * 新增学科/题型时只需在此扩展 sealed class。
 */
sealed class Payload {

    @Serializable
    data class WordChoice(
        val word: String,
        val phonetic: String? = null,
        val pos: String? = null,
        val definitions: List<String>,
        val options: List<String>,
        @SerialName("answer_index") val answerIndex: Int,
        val example: String? = null,
        @SerialName("example_translation") val exampleTranslation: String? = null,
        @SerialName("audio_url") val audioUrl: String? = null
    ) : Payload()

    @Serializable
    data class DefinitionChoice(
        val definition: String,
        @SerialName("phonetic_hint") val phoneticHint: String? = null,
        val options: List<String>,
        @SerialName("answer_index") val answerIndex: Int,
        val example: String? = null
    ) : Payload()

    @Serializable
    data class Spelling(
        val definition: String,
        val answer: String,
        val phonetic: String? = null,
        val pos: String? = null,
        @SerialName("hint_letters") val hintLetters: List<Int>? = null,
        val example: String? = null
    ) : Payload()

    @Serializable
    data class ListeningChoice(
        val word: String,
        val phonetic: String? = null,
        val definitions: List<String>,
        val options: List<String>,
        @SerialName("answer_index") val answerIndex: Int,
        @SerialName("audio_url") val audioUrl: String? = null,
        @SerialName("use_tts") val useTts: Boolean = true
    ) : Payload()

    @Serializable
    data class SentenceFill(
        val sentence: String,
        val answer: String,
        val options: List<String>,
        @SerialName("answer_index") val answerIndex: Int? = null,
        val translation: String? = null
    ) : Payload()

    @Serializable
    data class SingleChoice(
        val question: String,
        val options: List<String>,
        @SerialName("answer_index") val answerIndex: Int,
        val explanation: String? = null
    ) : Payload()

    @Serializable
    data class MultiChoice(
        val question: String,
        val options: List<String>,
        @SerialName("answer_indices") val answerIndices: List<Int>,
        val explanation: String? = null
    ) : Payload()

    @Serializable
    data class TrueFalse(
        val statement: String,
        val answer: Boolean,
        val explanation: String? = null
    ) : Payload()

    @Serializable
    data class FormulaChoice(
        val question: String,
        val options: List<String>,
        @SerialName("answer_index") val answerIndex: Int,
        val explanation: String? = null
    ) : Payload()
}

/**
 * Item 的自定义序列化器。
 *
 * JSON 中 type 和 payload 是兄弟字段：
 *   { "type": "word_choice", "payload": { ... } }
 *
 * kotlinx.serialization 的多态机制会把 discriminator 放进对象内部，
 * 与本 Schema 不兼容，因此这里手写 type->payload 的派发。
 */
object ItemSerializer : KSerializer<Item> {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override val descriptor = buildClassSerialDescriptor("Item") {
        element<String>("id")
        element<String>("type")
        element<Int>("difficulty")
        element<List<String>>("tags")
        element<JsonObject>("payload")
    }

    override fun deserialize(decoder: Decoder): Item {
        val jd = decoder as? JsonDecoder
            ?: throw SerializationException("ItemSerializer 仅支持 JSON 输入")
        val obj = jd.decodeJsonElement().jsonObject

        val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull
            ?: throw SerializationException("Item 缺少 type 字段")
        val type = ItemType.fromJsonName(typeStr)
            ?: throw SerializationException("未知题型: $typeStr")

        val payloadObj = obj["payload"]?.jsonObject
            ?: throw SerializationException("Item 缺少 payload 字段")

        val payload: Payload = when (type) {
            ItemType.WORD_CHOICE -> json.decodeFromJsonElement(Payload.WordChoice.serializer(), payloadObj)
            ItemType.DEFINITION_CHOICE -> json.decodeFromJsonElement(Payload.DefinitionChoice.serializer(), payloadObj)
            ItemType.SPELLING -> json.decodeFromJsonElement(Payload.Spelling.serializer(), payloadObj)
            ItemType.LISTENING_CHOICE -> json.decodeFromJsonElement(Payload.ListeningChoice.serializer(), payloadObj)
            ItemType.SENTENCE_FILL -> json.decodeFromJsonElement(Payload.SentenceFill.serializer(), payloadObj)
            ItemType.SINGLE_CHOICE -> json.decodeFromJsonElement(Payload.SingleChoice.serializer(), payloadObj)
            ItemType.MULTI_CHOICE -> json.decodeFromJsonElement(Payload.MultiChoice.serializer(), payloadObj)
            ItemType.TRUE_FALSE -> json.decodeFromJsonElement(Payload.TrueFalse.serializer(), payloadObj)
            ItemType.FORMULA_CHOICE -> json.decodeFromJsonElement(Payload.FormulaChoice.serializer(), payloadObj)
        }

        return Item(
            id = obj["id"]?.jsonPrimitive?.content
                ?: throw SerializationException("Item 缺少 id 字段"),
            type = type,
            difficulty = (obj["difficulty"] as? JsonPrimitive)?.intOrNull,
            tags = obj["tags"]?.let {
                json.decodeFromJsonElement(ListSerializer(String.serializer()), it)
            } ?: emptyList(),
            payload = payload
        )
    }

    override fun serialize(encoder: Encoder, value: Item) {
        // MVP 阶段应用只读题库，序列化留待后续实现
        throw NotImplementedError("Item 序列化暂未实现，仅支持反序列化")
    }
}

/**
 * 题库解析入口。配置忽略未知字段，向前兼容 Schema 升级。
 */
object QuizBankParser {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(content: String): QuizBank =
        json.decodeFromString(content)

    fun parseList(content: String): List<QuizBank> =
        json.decodeFromString(content)
}
