package app.unlocklearn.data

import app.unlocklearn.data.db.entity.ItemEntity
import app.unlocklearn.data.db.entity.SchedulingStateEntity
import app.unlocklearn.quizbank.model.Item
import app.unlocklearn.quizbank.model.ItemSerializer
import app.unlocklearn.scheduler.CardStatus
import app.unlocklearn.scheduler.SchedulingState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Room Entity 与领域模型之间的转换。
 */
object EntityMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * ItemEntity -> Item。
     * 将拆开的字段重组为 JSON，用 ItemSerializer 反序列化。
     */
    fun toItem(entity: ItemEntity): Item {
        val itemObj = buildJsonObject {
            put("id", entity.id)
            put("type", entity.type)
            entity.difficulty?.let { put("difficulty", it) }
            // tags 存的是 JSON array string，解析回 JsonElement
            put("tags", json.parseToJsonElement(entity.tags.ifEmpty { "[]" }))
            // payload 存的是 JSON object string
            put("payload", json.parseToJsonElement(entity.payloadJson))
        }
        return json.decodeFromJsonElement(ItemSerializer, itemObj)
    }

    fun toSchedulingState(entity: SchedulingStateEntity): SchedulingState = SchedulingState(
        itemId = entity.itemId,
        bankId = entity.bankId,
        repetitions = entity.repetitions,
        intervalDays = entity.intervalDays,
        easeFactor = entity.easeFactor,
        lastReviewedAt = entity.lastReviewedAt,
        dueAt = entity.dueAt,
        lapses = entity.lapses,
        status = runCatching { CardStatus.valueOf(entity.status) }.getOrDefault(CardStatus.NEW),
        createdAt = entity.createdAt
    )

    fun toEntity(state: SchedulingState): SchedulingStateEntity = SchedulingStateEntity(
        itemId = state.itemId,
        bankId = state.bankId,
        repetitions = state.repetitions,
        intervalDays = state.intervalDays,
        easeFactor = state.easeFactor,
        lastReviewedAt = state.lastReviewedAt,
        dueAt = state.dueAt,
        lapses = state.lapses,
        status = state.status.name,
        createdAt = state.createdAt
    )
}
