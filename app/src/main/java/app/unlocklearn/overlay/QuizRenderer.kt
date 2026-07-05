package app.unlocklearn.overlay

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unlocklearn.quizbank.model.Item
import app.unlocklearn.quizbank.model.Payload
import app.unlocklearn.ui.theme.Error
import app.unlocklearn.ui.theme.InkSoft
import app.unlocklearn.ui.theme.Success
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 题型渲染分派。根据 item.payload 类型选择对应 Composable。
 * @param onAnswer (correct, responseMs) 答题回调
 */
@Composable
fun QuizRenderer(item: Item, onAnswer: (Boolean, Long) -> Unit) {
    val startTime = remember { System.currentTimeMillis() }
    val answer: (Boolean) -> Unit = { correct ->
        onAnswer(correct, System.currentTimeMillis() - startTime)
    }
    when (val p = item.payload) {
        is Payload.WordChoice -> WordChoiceView(p, answer)
        is Payload.DefinitionChoice -> DefinitionChoiceView(p, answer)
        is Payload.Spelling -> SpellingView(p, answer)
        is Payload.ListeningChoice -> ListeningChoiceView(p, answer)
        is Payload.SentenceFill -> SentenceFillView(p, answer)
        is Payload.SingleChoice -> SingleChoiceView(p.question, p.options, p.answerIndex, answer)
        is Payload.TrueFalse -> TrueFalseView(p.statement, p.answer, answer)
        is Payload.MultiChoice -> SingleChoiceView(p.question, p.options, p.answerIndices.first(), answer)
        is Payload.FormulaChoice -> SingleChoiceView(p.question, p.options, p.answerIndex, answer)
    }
}

private enum class OptionState { DEFAULT, CORRECT, WRONG, DISABLED }

@Composable
private fun OptionButton(
    text: String,
    state: OptionState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container = when (state) {
        OptionState.DEFAULT -> MaterialTheme.colorScheme.surface
        OptionState.CORRECT -> Success
        OptionState.WRONG -> Error
        OptionState.DISABLED -> MaterialTheme.colorScheme.surface
    }
    val content = when (state) {
        OptionState.DEFAULT -> MaterialTheme.colorScheme.onBackground
        OptionState.CORRECT -> androidx.compose.ui.graphics.Color.White
        OptionState.WRONG -> androidx.compose.ui.graphics.Color.White
        OptionState.DISABLED -> InkSoft
    }
    OutlinedButton(
        onClick = onClick,
        enabled = state == OptionState.DEFAULT,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WordChoiceView(p: Payload.WordChoice, onAnswer: (Boolean) -> Unit) {
    var selected by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(800)
            onAnswer(selected == p.answerIndex)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(p.word, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        p.phonetic?.let {
            Text(it, fontSize = 16.sp, color = InkSoft, modifier = Modifier.padding(top = 8.dp))
        }
        p.pos?.let {
            Text(it, fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(36.dp))
        OptionList(p.options, p.answerIndex, selected) { selected = it }
    }
}

@Composable
private fun DefinitionChoiceView(p: Payload.DefinitionChoice, onAnswer: (Boolean) -> Unit) {
    var selected by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(800)
            onAnswer(selected == p.answerIndex)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(p.definition, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        p.phoneticHint?.let {
            Text(it, fontSize = 14.sp, color = InkSoft, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(36.dp))
        OptionList(p.options, p.answerIndex, selected) { selected = it }
    }
}

@Composable
private fun SentenceFillView(p: Payload.SentenceFill, onAnswer: (Boolean) -> Unit) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val answerIdx = p.answerIndex ?: p.options.indexOf(p.answer)
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(800)
            onAnswer(selected == answerIdx)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(p.sentence, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        p.translation?.let {
            Text(it, fontSize = 14.sp, color = InkSoft, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(36.dp))
        OptionList(p.options, answerIdx, selected) { selected = it }
    }
}

@Composable
private fun ListeningChoiceView(p: Payload.ListeningChoice, onAnswer: (Boolean) -> Unit) {
    val tts = rememberTts()
    var selected by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(800)
            onAnswer(selected == p.answerIndex)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = { tts?.speak(p.word, TextToSpeech.QUEUE_FLUSH, null, "word_${p.word}") },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.padding(vertical = 8.dp)
        ) { Text("播放", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 24.dp)) }
        p.phonetic?.let {
            Text(it, fontSize = 14.sp, color = InkSoft, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(28.dp))
        OptionList(p.options, p.answerIndex, selected) { selected = it }
    }
}

@Composable
private fun SpellingView(p: Payload.Spelling, onAnswer: (Boolean) -> Unit) {
    var input by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val correct = input.trim().equals(p.answer, ignoreCase = true)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(p.definition, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        p.phonetic?.let {
            Text(it, fontSize = 14.sp, color = InkSoft, modifier = Modifier.padding(top = 8.dp))
        }
        // hint：显示部分字母
        p.hintLetters?.let { hints ->
            Text(buildHintString(p.answer, hints), fontSize = 18.sp, color = InkSoft, modifier = Modifier.padding(top = 16.dp))
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { if (!submitted) input = it },
            enabled = !submitted,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入单词") },
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { submitted = true; onAnswer(correct) },
            enabled = input.isNotBlank() && !submitted,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (submitted) (if (correct) "正确" else "答案: ${p.answer}") else "提交") }
    }
}

@Composable
private fun SingleChoiceView(
    question: String,
    options: List<String>,
    answerIndex: Int,
    onAnswer: (Boolean) -> Unit
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(800)
            onAnswer(selected == answerIndex)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(question, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        OptionList(options, answerIndex, selected) { selected = it }
    }
}

@Composable
private fun TrueFalseView(statement: String, answer: Boolean, onAnswer: (Boolean) -> Unit) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val answerIdx = if (answer) 0 else 1
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(800)
            onAnswer(selected == answerIdx)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(statement, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        OptionList(listOf("正确", "错误"), answerIdx, selected) { selected = it }
    }
}

@Composable
private fun OptionList(
    options: List<String>,
    answerIndex: Int,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, opt ->
            val state = when {
                selected == null -> OptionState.DEFAULT
                i == answerIndex -> OptionState.CORRECT
                i == selected -> OptionState.WRONG
                else -> OptionState.DISABLED
            }
            OptionButton(opt, state) { onSelect(i) }
        }
    }
}

private fun buildHintString(answer: String, hintLetters: List<Int>): String {
    return answer.mapIndexed { i, c ->
        if (i in hintLetters) c else '_'
    }.joinToString(" ")
}

@Composable
private fun rememberTts(): TextToSpeech? {
    val context = LocalContext.current
    val tts = remember { TextToSpeech(context, null) }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }
    // 设置英文语种（若可用）
    LaunchedEffect(Unit) {
        tts.language = Locale.ENGLISH
    }
    return tts
}
