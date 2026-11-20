package ru.mtproxymanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mtproxymanager.data.ProxyEntity
import ru.mtproxymanager.data.ProxySecurity
import ru.mtproxymanager.data.ProxyStatus
import ru.mtproxymanager.telegram.TelegramLauncher
import ru.mtproxymanager.ui.theme.OfflineColor
import ru.mtproxymanager.ui.theme.OnlineColor
import ru.mtproxymanager.ui.theme.WarnColor

@Composable
fun MainScreen(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val onlineCount = state.proxies.count { it.status == ProxyStatus.ONLINE }
    val groups = state.proxies.groupBy { it.country }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Доступно: $onlineCount из ${state.proxies.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (state.lastCheckedAt == 0L) "Ещё не проверялись" else "Последняя проверка: ${formatTime(state.lastCheckedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val refreshStale = state.lastRefreshAt == 0L ||
                        System.currentTimeMillis() - state.lastRefreshAt > state.refreshHours * 2 * 3_600_000L
                    Text(
                        if (state.lastRefreshAt == 0L) {
                            "Списки ещё не загружались"
                        } else {
                            "Списки обновлены: ${formatDateTime(state.lastRefreshAt)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (refreshStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (refreshStale && state.lastRefreshAt != 0L) {
                        Text(
                            "Списки давно не обновлялись. Проверьте сеть или нажмите «Обновить списки».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.checkingIds.isNotEmpty()) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
                    }
                }
            }
        }

        item(key = "buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = viewModel::selectBest, modifier = Modifier.weight(1f)) {
                    Text("Выбрать лучший")
                }
                OutlinedButton(
                    onClick = viewModel::recheckAll,
                    enabled = state.checkingIds.isEmpty() && state.proxies.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Проверить снова")
                }
            }
        }

        item(key = "refresh") {
            TextButton(
                onClick = viewModel::refreshSources,
                enabled = !state.isRefreshing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isRefreshing) "Обновляем списки…" else "Обновить списки из источников")
            }
        }

        item(key = "sort") {
            SortRow(selected = state.sortMode, onSelect = viewModel::setSort)
        }

        if (state.proxies.isEmpty()) {
            item(key = "empty") { EmptyState() }
        }

        groups.forEach { (country, group) ->
            item(key = "country_$country") { CountryHeader(country, group.size) }
            items(group, key = { it.id }) { proxy ->
                ProxyCardFor(proxy, state, viewModel)
            }
        }
    }
}

@Composable
fun FavoritesScreen(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val favorites = state.proxies.filter { it.isFavorite }

    if (favorites.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Избранных пока нет", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Нажмите ☆ у прокси на главном экране, чтобы сохранить его здесь.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(favorites, key = { it.id }) { proxy ->
                ProxyCardFor(proxy, state, viewModel)
            }
        }
    }
}

@Composable
private fun SortRow(selected: SortMode, onSelect: (SortMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SortMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(mode.label) },
            )
        }
    }
}

@Composable
private fun CountryHeader(country: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(country, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Список пуст", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Обновите списки из источников или добавьте прокси по ссылке кнопкой «+» вверху. Источники можно изменить в настройках.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ProxyCardFor(proxy: ProxyEntity, state: AppUiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    ProxyCard(
        proxy = proxy,
        checking = proxy.id in state.checkingIds,
        selected = state.selectedId == proxy.id,
        onSelect = { viewModel.select(proxy.id) },
        onConnect = { viewModel.connect(proxy) },
        onFavorite = { viewModel.toggleFavorite(proxy) },
        onCopy = { copyDeepLink(context, proxy) },
    )
}

@Composable
private fun ProxyCard(
    proxy: ProxyEntity,
    checking: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onFavorite: () -> Unit,
    onCopy: () -> Unit,
) {
    val statusColor = when {
        checking -> MaterialTheme.colorScheme.outline
        proxy.status == ProxyStatus.ONLINE -> OnlineColor
        proxy.status == ProxyStatus.PARTIAL -> WarnColor
        proxy.status == ProxyStatus.OFFLINE -> OfflineColor
        else -> MaterialTheme.colorScheme.outline
    }
    val statusText = when {
        checking -> "Проверка…"
        proxy.status == ProxyStatus.ONLINE -> "Работает, протокол подтверждён"
        proxy.status == ProxyStatus.PARTIAL -> "TCP доступен, протокол не подтверждён"
        proxy.status == ProxyStatus.OFFLINE -> "Недоступен"
        else -> "Не проверен"
    }

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${proxy.server}:${proxy.port}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${proxy.typeLabel()} · ${sourceLabel(proxy.source)} · ${proxy.maskedSecret()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    proxy.pingMs?.let { "$it мс" } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (proxy.status == ProxyStatus.ONLINE) OnlineColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onFavorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = if (proxy.isFavorite) "Убрать из избранного" else "В избранное",
                        tint = if (proxy.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$statusText · безопасность: ${ProxySecurity.assess(proxy).level.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCopy) { Text("Копировать") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = onConnect, shape = RoundedCornerShape(12.dp)) {
                    Text("Подключиться")
                }
            }
        }
    }
}

private fun copyDeepLink(context: Context, proxy: ProxyEntity) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MTProto proxy", TelegramLauncher.deepLink(proxy)))
    Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
}
