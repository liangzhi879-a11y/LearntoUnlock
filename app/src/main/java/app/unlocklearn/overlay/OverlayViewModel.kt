package app.unlocklearn.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.unlocklearn.data.QuizRepository
import app.unlocklearn.pref.SkipCounter
import app.unlocklearn.quizbank.model.Item
import app.unlocklearn.quizbank.model.Payload
import app.unlocklearn.scheduler.AnswerQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 覆盖答题层 ViewModel。
 *
 * 状态机：
 * Loading → Ready → (答对) Correct → finish
 *                  → (答错) Wrong → next() → Loading...
 *                  → skip() → Skipped → finish
 * Loading → NoBank / NoCard → finish（放行，不卡用户）
 */
class OverlayViewModel(
    private val repo: QuizRepository,
    private val skipCounter: SkipCounter
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Ready(val item: Item, val skipRemaining: Int, val streak: Int) : UiState()
        object Correct : UiState()
        data class Wrong(val item: Item) : UiState()
        object NoCard : UiState()
        object NoBank : UiState()
        object Skipped : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var streak: Int = 0

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val item = repo.selectNextItem()
            if (item == null) {
                val enabled = repo.getEnabledBankIds()
                _state.value = if (enabled.isEmpty()) UiState.NoBank else UiState.NoCard
            } else {
                val remaining = skipCounter.getRemaining()
                _state.value = UiState.Ready(item, remaining, streak)
            }
        }
    }

    /**
     * 答题回调。
     * @param correct 是否答对
     * @param responseMs 响应毫秒，用于 SM-2 quality 评分
     */
    fun answer(correct: Boolean, responseMs: Long) {
        val current = _state.value as? UiState.Ready ?: return
        val quality = AnswerQuality.fromResult(correct, responseMs)
        viewModelScope.launch {
            repo.recordAnswer(current.item.id, quality)
            if (correct) {
                streak += 1
                _state.value = UiState.Correct
            } else {
                streak = 0
                _state.value = UiState.Wrong(current.item)
            }
        }
    }

    /** 答错后换下一题 */
    fun next() {
        load()
    }

    /** 跳过（消耗一次跳过次数后放行） */
    fun skip() {
        val current = _state.value as? UiState.Ready ?: return
        viewModelScope.launch {
            if (skipCounter.consumeSkip()) {
                repo.recordAnswer(current.item.id, AnswerQuality.SKIPPED)
                _state.value = UiState.Skipped
            }
        }
    }
}

/**
 * 从 payload 提取正确答案的展示文本（用于答错后显示）。
 */
fun extractCorrectAnswer(item: Item): String = when (val p = item.payload) {
    is Payload.WordChoice -> p.options.getOrElse(p.answerIndex) { p.word }
    is Payload.DefinitionChoice -> p.options.getOrElse(p.answerIndex) { "—" }
    is Payload.SentenceFill -> p.answer
    is Payload.Spelling -> p.answer
    is Payload.ListeningChoice -> p.options.getOrElse(p.answerIndex) { p.word }
    is Payload.SingleChoice -> p.options.getOrElse(p.answerIndex) { "—" }
    is Payload.MultiChoice -> p.answerIndices.joinToString { p.options.getOrElse(it) { "—" } }
    is Payload.TrueFalse -> if (p.answer) "正确" else "错误"
    is Payload.FormulaChoice -> p.options.getOrElse(p.answerIndex) { "—" }
}
