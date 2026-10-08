package net.topvl.storyreader.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.launch
import net.topvl.storyreader.source.Http
import net.topvl.storyreader.source.Sources
import net.topvl.storyreader.source.Story

enum class SearchEngine(val label: String) {
    GOOGLE("Google"), BING("Bing"), DUCKDUCKGO("DuckDuckGo");

    fun url(q: String): String {
        val e = Uri.encode(q)
        return when (this) {
            GOOGLE -> "https://www.google.com/search?hl=vi&q=$e"
            BING -> "https://www.bing.com/search?setlang=vi&q=$e"
            DUCKDUCKGO -> "https://duckduckgo.com/?kl=vn-vi&q=$e"
        }
    }
}

/** Phạm vi tìm (site:) cho từng nguồn. */
private fun siteOf(sourceId: String): String? = when (sourceId) {
    "lmvn" -> "lmvn.com/truyen"
    else -> Sources.byId(sourceId)?.homepage?.let { Uri.parse(it).host?.removePrefix("www.") }
}

class WebSearchViewModel : ViewModel() {
    var query by mutableStateOf("")
    var engine by mutableStateOf(SearchEngine.GOOGLE)
    val selected = mutableStateListOf("wattpad")
    var url by mutableStateOf<String?>(null)

    fun buildUrl(): String? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val sites = selected.mapNotNull { siteOf(it) }
        val scope = when (sites.size) {
            0 -> ""
            1 -> " site:${sites[0]}"
            else -> " (" + sites.joinToString(" OR ") { "site:$it" } + ")"
        }
        return engine.url(q + scope)
    }
}

/** Lấy đường dẫn đích thật từ link chuyển hướng của Google/Bing/DuckDuckGo. */
internal fun unwrapSearchLink(raw: String): String {
    val uri = Uri.parse(raw)
    val host = uri.host.orEmpty()
    fun param(name: String) = runCatching { uri.getQueryParameter(name) }.getOrNull()
    return when {
        host.contains("google.") && uri.path == "/url" -> param("q") ?: param("url") ?: raw
        host.contains("bing.com") && uri.path?.startsWith("/ck/") == true -> {
            val u = param("u")
            if (u != null && u.startsWith("a1")) runCatching {
                String(Base64.decode(u.substring(2), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            }.getOrDefault(raw) else raw
        }
        host.contains("duckduckgo.com") && uri.path?.startsWith("/l/") == true -> param("uddg") ?: raw
        else -> raw
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSearchScreen(vm: WebSearchViewModel, modifier: Modifier = Modifier, onOpen: (Story) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var loadedUrl by remember { mutableStateOf<String?>(null) }

    fun search() {
        focus.clearFocus()
        if (vm.selected.isEmpty()) {
            Toast.makeText(context, "Hãy chọn ít nhất một nguồn", Toast.LENGTH_SHORT).show(); return
        }
        vm.url = vm.buildUrl()
    }

    /** Chạm vào kết quả thuộc nguồn hỗ trợ -> mở thẳng truyện trong app. */
    fun tryOpen(raw: String): Boolean {
        val target = unwrapSearchLink(raw)
        if (Sources.all.none { it.handles(target) }) return false
        resolving = true
        scope.launch {
            val story = runCatching { Sources.resolve(target) }.getOrNull()
            resolving = false
            if (story != null) onOpen(story)
            else {
                Toast.makeText(context, "Không mở được trong app, hiển thị trang gốc", Toast.LENGTH_SHORT).show()
                webView?.loadUrl(target)
            }
        }
        return true
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Tìm qua công cụ tìm kiếm",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text("Tên truyện, tác giả…") },
            leadingIcon = { Icon(Icons.Filled.TravelExplore, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Clear, "Xoá") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Công cụ:", style = MaterialTheme.typography.labelMedium)
            SearchEngine.entries.forEach { e ->
                FilterChip(selected = vm.engine == e, onClick = { vm.engine = e; if (vm.url != null) search() },
                    label = { Text(e.label) })
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Nguồn:", style = MaterialTheme.typography.labelMedium)
            Sources.all.forEach { s ->
                val on = s.id in vm.selected
                FilterChip(selected = on, onClick = {
                    if (on) vm.selected.remove(s.id) else vm.selected.add(s.id)
                }, label = { Text(s.name) })
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
        if (loading || resolving) LinearProgressIndicator(Modifier.fillMaxWidth())

        val url = vm.url
        if (url == null) {
            EmptyState(
                "Tìm truyện qua Google, Bing hoặc DuckDuckGo trong phạm vi các nguồn đã chọn.\n\n" +
                    "Chạm vào kết quả thuộc nguồn được hỗ trợ để mở thẳng trong trình đọc của app."
            )
        } else {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = Http.USER_AGENT
                            CookieManager.getInstance().setAcceptCookie(true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val u = request.url.toString()
                                    if (!u.startsWith("http")) return true // chặn intent://, market://
                                    return request.isForMainFrame && tryOpen(u)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    canGoBack = view?.canGoBack() == true
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    canGoBack = view?.canGoBack() == true
                                }
                            }
                            webView = this
                        }
                    },
                    update = { wv ->
                        if (loadedUrl != url) {
                            loadedUrl = url
                            wv.loadUrl(url)
                        }
                    },
                )
            }
        }
    }
}
