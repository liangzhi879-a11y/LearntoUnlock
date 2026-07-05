package app.unlocklearn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.unlocklearn.data.QuizRepository
import app.unlocklearn.data.db.entity.BankEntity
import app.unlocklearn.pref.AppSettings
import app.unlocklearn.pref.SkipCounter
import app.unlocklearn.ui.theme.UnlockLearnTheme
import app.unlocklearn.unlock.AppWatchService
import app.unlocklearn.unlock.PermissionHelper
import app.unlocklearn.unlock.UnlockWatchService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnlockLearnTheme {
                HomeScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val repo = remember { QuizRepository(context) }
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    var banks by remember { mutableStateOf<List<BankEntity>>(emptyList()) }
    var skipRemaining by remember { mutableStateOf(SkipCounter.DAILY_LIMIT) }
    var unlockMode by remember { mutableStateOf(true) }
    var appMode by remember { mutableStateOf(false) }
    var probability by remember { mutableStateOf(AppSettings.DEFAULT_PROBABILITY) }
    var overlayGranted by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }
    var usageGranted by remember { mutableStateOf(PermissionHelper.hasUsageStatsPermission(context)) }

    LaunchedEffect(Unit) { repo.allBanks.collectLatest { banks = it } }
    LaunchedEffect(Unit) { skipRemaining = SkipCounter(context).getRemaining() }
    LaunchedEffect(Unit) { settings.unlockModeEnabled.collectLatest { unlockMode = it } }
    LaunchedEffect(Unit) { settings.appModeEnabled.collectLatest { appMode = it } }
    LaunchedEffect(Unit) { settings.triggerProbability.collectLatest { probability = it } }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { BrandHeader() }
            item { TodayStatus(skipRemaining = skipRemaining, enabledCount = banks.count { it.enabled }) }

            item { SectionLabel("解答模式") }
            item {
                ModeSwitchRow(
                    title = "锁屏解答模式",
                    desc = "解锁手机后有概率触发答题",
                    enabled = unlockMode,
                    onToggle = { on ->
                        scope.launch {
                            settings.setUnlockModeEnabled(on)
                            if (on) UnlockWatchService.start(context) else UnlockWatchService.stop(context)
                        }
                    }
                )
            }
            item {
                ModeSwitchRow(
                    title = "娱乐 App 解答模式",
                    desc = "打开抖音/B站等娱乐 App 时有概率触发",
                    enabled = appMode,
                    onToggle = { on ->
                        if (on && !PermissionHelper.hasUsageStatsPermission(context)) {
                            // 需先授权使用情况访问权限
                            PermissionHelper.openUsageAccessSettings(context)
                        }
                        scope.launch {
                            settings.setAppModeEnabled(on)
                            if (on && PermissionHelper.hasUsageStatsPermission(context)) {
                                AppWatchService.start(context)
                            } else if (!on) {
                                AppWatchService.stop(context)
                            }
                        }
                    }
                )
            }
            if (appMode && !PermissionHelper.hasUsageStatsPermission(context)) {
                item {
                    val rom = remember { PermissionHelper.detectRom() }
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        PermissionHint(
                            text = "娱乐 App 模式需要「使用情况访问权限」，点击前往授权",
                            onClick = { PermissionHelper.openUsageAccessSettings(context) }
                        )
                        Text(
                            text = "当前机型：${rom.displayName}\n路径：${rom.usageAccessGuide}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }
            }

            item { SectionLabel("触发概率") }
            item {
                ProbabilitySlider(
                    probability = probability,
                    onChange = { p ->
                        probability = p
                        scope.launch { settings.setTriggerProbability(p) }
                    }
                )
            }

            item { SectionLabel("题库") }
            if (banks.isEmpty()) {
                item { LoadingRow() }
            } else {
                items(banks, key = { it.bankId }) { bank ->
                    BankRow(
                        bank = bank,
                        onToggle = { enabled ->
                            scope.launch { repo.setBankEnabled(bank.bankId, enabled) }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
            item { SectionLabel("权限") }
            item {
                PermissionRow(
                    title = "显示在其他应用上层",
                    desc = "答题覆盖层所需",
                    granted = overlayGranted,
                    onClick = {
                        PermissionHelper.openOverlaySettings(context)
                        overlayGranted = PermissionHelper.canDrawOverlays(context)
                    }
                )
            }
            item {
                PermissionRow(
                    title = "使用情况访问",
                    desc = "娱乐 App 模式所需",
                    granted = usageGranted,
                    onClick = {
                        PermissionHelper.openUsageAccessSettings(context)
                        usageGranted = PermissionHelper.hasUsageStatsPermission(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            text = "UnlockLearn",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "解锁即学习",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TodayStatus(skipRemaining: Int, enabledCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "今日",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            StatusItem(value = enabledCount.toString(), label = "已启用")
            StatusItem(value = "0", label = "连击")
            StatusItem(value = skipRemaining.toString(), label = "跳过")
        }
    }
}

@Composable
private fun StatusItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ModeSwitchRow(
    title: String,
    desc: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun ProbabilitySlider(probability: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("触发概率", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${(probability * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = probability,
            onValueChange = onChange,
            valueRange = 0f..1f,
            steps = 19, // 每 5% 一档
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PermissionHint(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        Text("去授权", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = if (granted) "已授权" else "未授权",
            fontSize = 13.sp,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "题库导入中…",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun BankRow(bank: BankEntity, onToggle: (Boolean) -> Unit) {
    val levelLabel = listOfNotNull(bank.cefrLevel).ifEmpty { listOf(bank.category) }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!bank.enabled) }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bank.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "$levelLabel · ${bank.totalItems} 词",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = if (bank.enabled) "已启用" else "启用",
            fontSize = 13.sp,
            color = if (bank.enabled) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}
