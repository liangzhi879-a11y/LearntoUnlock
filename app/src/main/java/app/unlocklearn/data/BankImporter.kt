package app.unlocklearn.data

import android.content.Context
import android.util.Log
import app.unlocklearn.data.db.UnlockLearnDatabase
import app.unlocklearn.data.db.entity.BankEntity
import app.unlocklearn.data.db.entity.ItemEntity
import app.unlocklearn.data.db.entity.SchedulingStateEntity
import app.unlocklearn.quizbank.model.QuizBankParser
import app.unlocklearn.scheduler.CardStatus
import app.unlocklearn.scheduler.SM2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 题库导入器。从 assets/banks 目录下的 JSON 文件读取，写入 Room。
 *
 * 幂等：banks 表非空时跳过，避免重复导入。
 * 首次启动时由 Application 或 Repository 调用 [importIfNeeded]。
 */
class BankImporter(private val context: Context) {

    private val db = UnlockLearnDatabase.get(context)

    suspend fun importIfNeeded() {
        if (db.bankDao().count() > 0) {
            Log.i(TAG, "Banks already imported, skipping")
            return
        }
        val files = context.assets.list(BANKS_DIR) ?: run {
            Log.w(TAG, "No banks assets found at $BANKS_DIR")
            return
        }
        for (fileName in files.sorted()) {
            if (!fileName.endsWith(".json")) continue
            runCatching { importBank(fileName) }
                .onFailure { Log.e(TAG, "Failed to import $fileName", it) }
        }
    }

    private suspend fun importBank(fileName: String) {
        val content = context.assets.open("$BANKS_DIR/$fileName").bufferedReader().use { it.readText() }
        val bank = QuizBankParser.parse(content)
        val m = bank.metadata

        db.bankDao().insertAll(
            listOf(
                BankEntity(
                    bankId = m.bankId,
                    subject = m.subject,
                    category = m.category,
                    name = m.name,
                    nameEn = m.nameEn,
                    cefrLevel = m.cefrLevel,
                    totalItems = m.totalItems,
                    source = m.source,
                    license = m.license,
                    updatedAt = m.updatedAt,
                    enabled = false
                )
            )
        )

        // 从原始 JSON 树提取 item 字段，存为 ItemEntity
        val bankElement = Json.parseToJsonElement(content).jsonObject
        val itemsArray = bankElement["items"]?.jsonArray ?: return
        val now = System.currentTimeMillis()

        val itemEntities = itemsArray.map { element ->
            val obj = element.jsonObject
            ItemEntity(
                id = obj.getValue("id").jsonPrimitive.content,
                bankId = m.bankId,
                type = obj.getValue("type").jsonPrimitive.content,
                difficulty = (obj["difficulty"] as? JsonPrimitive)?.intOrNull,
                tags = obj["tags"]?.toString() ?: "[]",
                payloadJson = obj.getValue("payload").toString()
            )
        }
        db.itemDao().insertAll(itemEntities)

        // 为每个 item 初始化调度状态（NEW）
        val schedulingEntities = itemEntities.map { item ->
            SchedulingStateEntity(
                itemId = item.id,
                bankId = item.bankId,
                repetitions = 0,
                intervalDays = 0,
                easeFactor = SM2.EF_INITIAL,
                lastReviewedAt = 0L,
                dueAt = now,
                lapses = 0,
                status = CardStatus.NEW.name,
                createdAt = now
            )
        }
        schedulingEntities.forEach { db.schedulingDao().upsert(it) }

        Log.i(TAG, "Imported ${m.bankId}: ${itemEntities.size} items")
    }

    companion object {
        private const val TAG = "BankImporter"
        private const val BANKS_DIR = "banks"
    }
}
