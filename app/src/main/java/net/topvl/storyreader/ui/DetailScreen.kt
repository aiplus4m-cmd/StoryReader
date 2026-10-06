package net.topvl.storyreader.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.topvl.storyreader.data.Library
import net.topvl.storyreader.data.Repository
import net.topvl.storyreader.data.same
import net.topvl.storyreader.source.Sources
import net.topvl.storyreader.source.Story

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    story: Story,
    library: Library,
    onBack: () -> Unit,
    onRead: (chapter: Int, offset: Int) -> Unit,
) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf(Repository.cachedDetail(story)) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(detail == null) }
    var reversed by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val favorites by library.favorites.collectAsState()
    val history by library.history.collectAsState()
    val isFav = favorites.any { it.same(story) }
    val progress = history.firstOrNull { it.story.same(story) }
    val source = Sources.byId(story.sourceId)

    LaunchedEffect(reloadKey) {
        if (detail != null && reloadKey == 0) return@LaunchedEffect
        loading = true; error = null
        try {
            detail = Repository.detail(story, refresh = reloadKey > 0)
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
        }
        loading = false
    }

    val full: Story = detail?.story ?: story

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(full.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
                actions = {
                    IconButton(onClick = { library.toggleFavorite(full) }) {
                        Icon(
                            if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "Yêu thích",
                            tint = if (isFav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { openUrl(context, full.url) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Mở trang nguồn")
                    }
                },
            )
        }
    ) { padding ->
        val chapters = detail?.chapters.orEmpty()
        val indexed = chapters.withIndex().toList().let { if (reversed) it.reversed() else it }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(Modifier.padding(16.dp)) {
                    Cover(full.cover, Modifier.width(110.dp).height(160.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(full.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (full.author.isNotBlank()) Text("Tác giả: ${full.author}", style = MaterialTheme.typography.bodyMedium)
                        Text("Số chương: ${if (loading) "…" else chapters.size}", style = MaterialTheme.typography.bodySmall)
                        SourceBadge(full.sourceId)
                    }
                }
            }
            item {
                // Ghi rõ nguồn truyện
                Text(
                    "Nguồn: ${source?.name ?: full.sourceId} — ${full.url}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp).clickable { openUrl(context, full.url) },
                )
                Text(
                    "Nội dung thuộc bản quyền của tác giả và ${source?.name ?: "trang nguồn"}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = chapters.isNotEmpty(), onClick = {
                        if (progress != null) onRead(progress.chapterIndex, progress.offset) else onRead(0, 0)
                    }) {
                        Text(if (progress != null) "Đọc tiếp" else "Đọc từ đầu")
                    }
                    if (progress != null) {
                        OutlinedButton(enabled = chapters.isNotEmpty(), onClick = { onRead(0, 0) }) { Text("Đọc lại từ đầu") }
                    }
                }
                if (progress != null) {
                    Text("Đang đọc: ${progress.chapterTitle}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            if (full.description.isNotBlank()) {
                item {
                    Column(Modifier.padding(16.dp).animateContentSize().clickable { expanded = !expanded }) {
                        Text("Giới thiệu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            full.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!expanded) Text("Xem thêm", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Danh sách chương", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { reversed = !reversed }) { Icon(Icons.Filled.SwapVert, "Đảo thứ tự") }
                }
            }
            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                error != null -> item {
                    Column(Modifier.padding(16.dp)) {
                        Text("Không tải được truyện: $error", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { reloadKey++ }) { Text("Thử lại") }
                    }
                }
                chapters.isEmpty() -> item { Text("Truyện chưa có chương nào.", Modifier.padding(16.dp)) }
            }
            itemsIndexed(indexed, key = { _, c -> c.index }) { _, (i, c) ->
                val reading = progress?.chapterIndex == i
                Text(
                    c.title,
                    modifier = Modifier.fillMaxWidth().clickable { onRead(i, 0) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (reading) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (reading) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
