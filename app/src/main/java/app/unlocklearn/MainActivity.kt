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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import app.unlocklearn.pref.SkipCounter
import app.unlocklearn.ui.theme.UnlockLearnTheme
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
    val scope = rememberCoroutineScope()

    var banks by remember { mutableStateOf<List<BankEntity>>(emptyList()) }
    var skipRemaining by remember { mutableStateOf(SkipCounter.DAILY_LIMIT) }

    // 观察题库变化（导入完成后会自动更新）
    LaunchedEffect(Unit) {
        repo.allBanks.collectLatest { banks = it }
    }
    // 加载今日跳过剩余次数
    LaunchedEffect(Unit) {
        skipRemaining = SkipCounter(context).getRemaining()
    }

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
            item { SectionLabel("设置") }
            item { SettingsRow() }
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
            color = MaterialTheme.colorScheme.primary
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

@Composable
private fun SettingsRow() {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SettingItem("触发概率", "50%")
        SettingItem("每日跳过上限", "${SkipCounter.DAILY_LIMIT} 次")
        SettingItem("勿扰时段", "23:00 - 07:00")
        SettingItem("应急联系人", "未设置")
    }
}

@Composable
private fun SettingItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
