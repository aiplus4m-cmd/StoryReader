package net.topvl.storyreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.topvl.storyreader.data.Bookmark
import net.topvl.storyreader.data.Library
import net.topvl.storyreader.data.ReadProgress
import net.topvl.storyreader.source.Story
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

@Composable
fun LibraryScreen(
    library: Library,
    modifier: Modifier = Modifier,
    onOpen: (Story) -> Unit,
    onContinue: (ReadProgress) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val favorites by library.favorites.collectAsState()
    val history by library.history.collectAsState()

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text("Tủ truyện", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Yêu thích (${favorites.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Đang đọc (${history.size})") })
        }
        if (tab == 0) {
            if (favorites.isEmpty()) EmptyState("Chưa có truyện yêu thích.\nNhấn ♥ ở trang truyện để lưu.")
            else LazyColumn {
                items(favorites, key = { "${it.sourceId}|${it.url}" }) { s ->
                    val p = history.firstOrNull { it.story.sourceId == s.sourceId && it.story.url == s.url }
                    StoryRow(
                        s,
                        subtitle = p?.let { "Đang đọc: ${it.chapterTitle}" },
                        trailing = {
                            IconButton(onClick = { library.toggleFavorite(s) }) {
                                Icon(Icons.Filled.Favorite, "Bỏ yêu thích", tint = MaterialTheme.colorScheme.secondary)
                            }
                        },
                    ) { onOpen(s) }
                }
            }
        } else {
            if (history.isEmpty()) EmptyState("Bạn chưa đọc truyện nào.")
            else LazyColumn {
                items(history, key = { "${it.story.sourceId}|${it.story.url}" }) { p ->
                    StoryRow(
                        p.story,
                        subtitle = "${p.chapterTitle} • ${dateFmt.format(Date(p.updatedAt))}",
                        trailing = {
                            IconButton(onClick = { library.removeHistory(p.story) }) { Icon(Icons.Filled.Delete, "Xoá") }
                        },
                    ) { onContinue(p) }
                }
            }
        }
    }
}

@Composable
fun BookmarksScreen(library: Library, modifier: Modifier = Modifier, onOpen: (Bookmark) -> Unit) {
    val bookmarks by library.bookmarks.collectAsState()
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text("Bookmark", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
        if (bookmarks.isEmpty()) {
            EmptyState("Chưa có bookmark.\nKhi đọc, chạm giữa màn hình rồi nhấn biểu tượng đánh dấu.")
        } else LazyColumn {
            items(bookmarks, key = { it.key }) { b ->
                StoryRow(
                    b.story,
                    subtitle = "${b.chapterTitle}\n“${b.snippet}”",
                    trailing = {
                        IconButton(onClick = { library.removeBookmark(b) }) { Icon(Icons.Filled.Delete, "Xoá") }
                    },
                ) { onOpen(b) }
            }
        }
    }
}
