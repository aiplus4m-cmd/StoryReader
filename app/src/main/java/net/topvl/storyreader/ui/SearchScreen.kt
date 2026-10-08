package net.topvl.storyreader.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.topvl.storyreader.data.Library
import net.topvl.storyreader.source.Sources
import net.topvl.storyreader.source.Story
import net.topvl.storyreader.source.rankBy
import net.topvl.storyreader.source.matchScore
import net.topvl.storyreader.source.WattpadAuth
import net.topvl.storyreader.source.WattpadSource

data class SourceResult(
    val loading: Boolean = false,
    val stories: List<Story> = emptyList(),
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = false,
)

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
    var lastQuery by mutableStateOf("")
        private set
    val results = mutableStateMapOf<String, SourceResult>()
    /** Nguồn đang mở rộng phần "kết quả ít liên quan". */
    val showLoose = mutableStateMapOf<String, Boolean>()
    private val jobs = mutableListOf<Job>()

    fun search(enabled: List<String>) {
        val q = query.trim()
        if (q.isEmpty()) return
        jobs.forEach { it.cancel() }
        jobs.clear()
        results.clear()
        showLoose.clear()
        lastQuery = q
        enabled.forEach { id -> load(id, q, 1) }
    }

    fun loadMore(id: String) {
        val cur = results[id] ?: return
        if (cur.loading) return
        load(id, lastQuery, cur.page + 1)
    }

    private fun load(id: String, q: String, page: Int) {
        val source = Sources.byId(id) ?: return
        val prev = results[id] ?: SourceResult()
        results[id] = prev.copy(loading = true, error = null)
        jobs += viewModelScope.launch {
            try {
                val list = source.search(q, page)
                val merged = (if (page == 1) list else prev.stories + list).distinctBy { it.url }.rankBy(q)
                results[id] = SourceResult(
                    stories = merged,
                    page = page,
                    hasMore = list.isNotEmpty() && merged.size > prev.stories.size,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                results[id] = prev.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }
}

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    library: Library,
    modifier: Modifier = Modifier,
    onWattpadLogin: () -> Unit = {},
    onOpen: (Story) -> Unit,
) {
    var loginTick by remember { mutableStateOf(0) }
    val wattpadLoggedIn = remember(loginTick) { WattpadAuth.isLoggedIn() }
    val disabled by library.disabledSources.collectAsState()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resolving by remember { mutableStateOf(false) }

    fun submit() {
        focus.clearFocus()
        val text = vm.query.trim()
        if (Sources.isUrl(text)) {
            // Dán link truyện/chương (ví dụ link tìm được trên Google) để mở trực tiếp
            resolving = true
            scope.launch {
                val story = runCatching { Sources.resolve(text) }.getOrNull()
                resolving = false
                if (story != null) onOpen(story)
                else Toast.makeText(context, "Không nhận ra đường dẫn này thuộc nguồn nào", Toast.LENGTH_LONG).show()
            }
        } else {
            vm.search(Sources.all.map { it.id }.filterNot { it in disabled })
        }
    }
    val enabledIds = Sources.all.map { it.id }.filterNot { it in disabled }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Nhảm StoryReader",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Tên truyện, @username Wattpad, hoặc dán link…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Clear, "Xoá") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Sources.all.forEach { s ->
                FilterChip(
                    selected = s.id !in disabled,
                    onClick = { library.setSourceEnabled(s.id, s.id in disabled) },
                    label = { Text(s.name) },
                )
            }
        }
        if (WattpadSource.id !in disabled) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (wattpadLoggedIn) "Wattpad: đã đăng nhập" else "Wattpad: chưa đăng nhập – truyện Trưởng thành (Mature) bị ẩn",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (wattpadLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (wattpadLoggedIn) {
                    TextButton(onClick = { WattpadAuth.logout(); loginTick++ }) { Text("Đăng xuất") }
                } else {
                    TextButton(onClick = onWattpadLogin) { Text("Đăng nhập") }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
        if (resolving) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Đang mở đường dẫn…", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (vm.lastQuery.isEmpty()) {
            EmptyState(
                "Nhập từ khoá để tìm truyện trên ${enabledIds.size} nguồn.\nChạm vào tên nguồn để bật/tắt.\n\n" +
                    "Mẹo: dán link truyện hoặc link chương (ví dụ tìm được trên Google) để mở trực tiếp; " +
                    "gõ @username (ví dụ @antinh28) để xem truyện của tác giả trên Wattpad."
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            Sources.all.filter { vm.results.containsKey(it.id) }.forEach { source ->
                val r = vm.results[source.id] ?: return@forEach
                item(key = "h-${source.id}") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(source.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("${r.stories.size} kết quả", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (r.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                if (r.error != null) {
                    item(key = "e-${source.id}") {
                        Text("Lỗi: ${r.error}", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                } else if (!r.loading && r.stories.isEmpty()) {
                    item(key = "n-${source.id}") {
                        Text("Không tìm thấy truyện phù hợp.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                // Truyện khớp tên lên trước; kết quả ít liên quan gom lại phía dưới
                val (matched, loose) = r.stories.partition { matchScore(vm.lastQuery, it) > 0 }
                val expanded = vm.showLoose[source.id] == true
                items(matched, key = { "${source.id}-${it.url}" }) { story ->
                    StoryRow(story, subtitle = story.info.ifBlank { null }) { onOpen(story) }
                }
                if (source.id == WattpadSource.id && matched.isEmpty() && !r.loading && r.error == null &&
                    !wattpadLoggedIn && !vm.lastQuery.startsWith("@")
                ) {
                    item(key = "wp-hint") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                "Không có truyện nào trùng tên. Truyện bạn tìm có thể gắn nhãn Trưởng thành – " +
                                    "Wattpad chỉ hiển thị loại truyện này khi đã đăng nhập.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onWattpadLogin) { Text("Đăng nhập Wattpad") }
                        }
                    }
                }
                if (loose.isNotEmpty()) {
                    if (expanded || vm.lastQuery.startsWith("@")) {
                        if (matched.isNotEmpty()) item(key = "loose-h-${source.id}") {
                            Text(
                                "Kết quả ít liên quan",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        items(loose, key = { "${source.id}-${it.url}" }) { story ->
                            StoryRow(story, subtitle = story.info.ifBlank { null }) { onOpen(story) }
                        }
                    } else {
                        item(key = "loose-${source.id}") {
                            TextButton(onClick = { vm.showLoose[source.id] = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("Hiện ${loose.size} kết quả ít liên quan")
                            }
                        }
                    }
                }
                if (r.hasMore && !r.loading) {
                    item(key = "m-${source.id}") {
                        TextButton(onClick = { vm.loadMore(source.id) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("Xem thêm từ ${source.name}")
                        }
                    }
                }
                item(key = "d-${source.id}") { HorizontalDivider(Modifier.padding(top = 4.dp)) }
            }
            item(key = "web-search") {
                Text(
                    "Không thấy truyện? Dùng tab \"Tìm web\" để tìm qua Google/Bing và mở thẳng trong app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
