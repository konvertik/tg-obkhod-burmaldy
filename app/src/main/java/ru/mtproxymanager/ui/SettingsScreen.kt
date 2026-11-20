package ru.mtproxymanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mtproxymanager.data.REFRESH_HOURS_OPTIONS
import ru.mtproxymanager.data.ThemeMode
import ru.mtproxymanager.data.net.SourceResult

@Composable
fun SettingsScreen(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    var newSource by rememberSaveable { mutableStateOf("") }
    var sourceError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "h_theme") { SectionTitle("Тема") }
        item(key = "theme") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.theme == mode,
                                    onClick = { viewModel.setTheme(mode) },
                                    role = Role.RadioButton,
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = state.theme == mode, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(themeLabel(mode))
                        }
                    }
                }
            }
        }

        item(key = "h_bypass") { SectionTitle("Обход блокировок") }
        item(key = "fake_tls") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Приоритет FakeTLS-прокси", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Такие прокси маскируются под HTTPS и реже блокируются DPI. Автовыбор предпочитает их.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.preferFakeTls,
                            onCheckedChange = viewModel::setPreferFakeTls,
                        )
                    }
                }
            }
        }

        item(key = "h_sources") { SectionTitle("Источники прокси") }
        item(key = "sources_hint") {
            Text(
                "Список ссылок https://… на страницы с прокси. Приложение извлекает из них только ссылки t.me/proxy и tg://proxy и проверяет каждую.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.sources, key = { "src_$it" }) { url ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.removeSource(url) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить источник")
                    }
                }
            }
        }
        item(key = "add_source") {
            Column {
                OutlinedTextField(
                    value = newSource,
                    onValueChange = {
                        newSource = it
                        sourceError = null
                    },
                    label = { Text("Адрес источника (https://)") },
                    singleLine = true,
                    isError = sourceError != null,
                    supportingText = { sourceError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.addSource(newSource) { error ->
                                if (error == null) newSource = "" else sourceError = error
                            }
                        },
                        enabled = newSource.isNotBlank(),
                    ) { Text("Добавить") }
                    OutlinedButton(
                        onClick = viewModel::refreshSources,
                        enabled = !state.isRefreshing,
                    ) { Text(if (state.isRefreshing) "Обновляем…" else "Обновить сейчас") }
                }
            }
        }
        if (state.lastReport.isNotEmpty()) {
            item(key = "report") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Результат последнего обновления", fontWeight = FontWeight.SemiBold)
                        state.lastReport.forEach { result ->
                            Text(reportLine(result), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item(key = "h_auto") { SectionTitle("Автообновление") }
        item(key = "hours") {
            Column {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    REFRESH_HOURS_OPTIONS.forEach { hours ->
                        FilterChip(
                            selected = state.refreshHours == hours,
                            onClick = { viewModel.setRefreshHours(hours) },
                            label = { Text("каждые $hours ч") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Списки источников обновляются с выбранным интервалом. Недоступные серверы перепроверяются примерно каждые 15 минут, когда есть сеть. Уведомлений приложение не показывает.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "h_about") { SectionTitle("О приложении") }
        item(key = "about") {
            Text(
                "Безопасность прокси оценивается по типу секрета: высокая — FakeTLS, средняя — padding, низкая — обычный секрет. " +
                    "Оценка не сетевая и не гарантирует защиту. " +
                    "Приложение только передаёт ссылку на прокси в Telegram. Оно не создаёт VPN и не перехватывает трафик. " +
                    "Пинг — это TCP-проверка доступности сервера: она не подтверждает работу прокси. " +
                    "Telegram не сообщает приложению, подключены ли вы, поэтому подтверждение нужно в самом Telegram.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Как в системе"
    ThemeMode.LIGHT -> "Светлая"
    ThemeMode.DARK -> "Тёмная"
}

private fun reportLine(result: SourceResult): String = when (result) {
    is SourceResult.Ok -> "✓ ${sourceLabel(result.url)}: ${result.candidates.size} прокси"
    is SourceResult.Failed -> "✗ ${sourceLabel(result.url)}: ${result.reason}"
}
