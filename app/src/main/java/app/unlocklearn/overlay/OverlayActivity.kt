package app.unlocklearn.overlay

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.unlocklearn.data.QuizRepository
import app.unlocklearn.pref.SkipCounter
import app.unlocklearn.sos.EmergencyLauncher
import app.unlocklearn.ui.effect.CorrectBurst
import app.unlocklearn.ui.effect.FeedbackEffect
import app.unlocklearn.ui.theme.Error
import app.unlocklearn.ui.theme.InkSoft
import app.unlocklearn.ui.theme.Success
import app.unlocklearn.ui.theme.UnlockLearnTheme
import kotlinx.coroutines.delay

class OverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val repo = QuizRepository(applicationContext)
        val skipCounter = SkipCounter(applicationContext)
        val viewModel = ViewModelProvider(this, OverlayViewModelFactory(repo, skipCounter))[OverlayViewModel::class.java]

        setContent {
            UnlockLearnTheme {
                OverlayScreen(viewModel = viewModel, onFinish = { finish() })
            }
        }
    }
}

@Composable
private fun OverlayScreen(viewModel: OverlayViewModel, onFinish: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state) {
        when (state) {
            is OverlayViewModel.UiState.Correct -> {
                FeedbackEffect.vibrateCorrect(context)
                delay(700)
                onFinish()
            }
            is OverlayViewModel.UiState.Skipped -> {
                delay(300)
                onFinish()
            }
            is OverlayViewModel.UiState.Wrong -> FeedbackEffect.vibrateWrong(context)
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 紧急呼叫（右上角常驻）
        OutlinedButton(
            onClick = { EmergencyLauncher.dial(context) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
        ) {
            Text("紧急呼叫", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        // 跳过（左上角）
        val ready = state as? OverlayViewModel.UiState.Ready
        OutlinedButton(
            onClick = { viewModel.skip() },
            enabled = ready != null && ready.skipRemaining > 0,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text("跳过 ${ready?.skipRemaining ?: 0}", fontSize = 13.sp)
        }

        // streak 计数（顶部居中）
        if (ready != null && ready.streak > 0) {
            Text(
                "连击 ${ready.streak}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            )
        }

        // 中央内容
        when (val s = state) {
            is OverlayViewModel.UiState.Loading -> Text(
                "加载中…",
                modifier = Modifier.align(Alignment.Center),
                color = InkSoft
            )

            is OverlayViewModel.UiState.Ready -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                QuizRenderer(s.item) { correct, ms -> viewModel.answer(correct, ms) }
            }

            is OverlayViewModel.UiState.Correct -> {
                CorrectBurst(trigger = true, modifier = Modifier.fillMaxSize())
                Text(
                    "答对了",
                    modifier = Modifier.align(Alignment.Center),
                    color = Success,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            is OverlayViewModel.UiState.Wrong -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("正确答案", fontSize = 14.sp, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                Text(
                    extractCorrectAnswer(s.item),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Success
                )
                Spacer(Modifier.height(40.dp))
                Button(
                    onClick = { viewModel.next() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("下一题", fontSize = 16.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            is OverlayViewModel.UiState.NoCard, is OverlayViewModel.UiState.NoBank -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (s is OverlayViewModel.UiState.NoBank) "请先在 UnlockLearn 中启用题库" else "暂无可用题目",
                    fontSize = 18.sp,
                    color = InkSoft
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("放行", fontSize = 16.sp)
                }
            }

            is OverlayViewModel.UiState.Skipped -> Text(
                "已跳过",
                modifier = Modifier.align(Alignment.Center),
                color = InkSoft,
                fontSize = 20.sp
            )
        }
    }
}

class OverlayViewModelFactory(
    private val repo: QuizRepository,
    private val skipCounter: SkipCounter
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OverlayViewModel::class.java)) {
            return OverlayViewModel(repo, skipCounter) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
