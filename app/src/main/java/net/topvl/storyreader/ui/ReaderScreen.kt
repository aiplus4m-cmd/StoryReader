package net.topvl.storyreader.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import net.topvl.storyreader.data.Bookmark
import net.topvl.storyreader.data.Library
import net.topvl.storyreader.data.ReadProgress
import net.topvl.storyreader.data.ReaderSettings
import net.topvl.storyreader.data.ReaderTheme
import net.topvl.storyreader.data.Repository
import net.topvl.storyreader.source.ChapterContent
import net.topvl.storyreader.source.Sources
import net.topvl.storyreader.source.Story
import net.topvl.storyreader.source.StoryDetail

/** Một trang đã được dàn trang: [start, end) là vị trí trong chuỗi đầy đủ, bodyStart là vị trí trong nội dung chương. */
private data class Page(val text: AnnotatedString, val start: Int, val end: Int, val bodyStart: Int)

private data class Palette(val bg: Color, val fg: Color, val dim: Color)

private fun paletteOf(t: ReaderTheme) = when (t) {
    ReaderTheme.LIGHT -> Palette(Color(0xFFFFFFFF), Color(0xFF222222), Color(0xFF8A8A8A))
    ReaderTheme.SEPIA -> Palette(Color(0xFFF4ECD8), Color(0xFF4B3A2A), Color(0xFF9C8B74))
    ReaderTheme.DARK -> Palette(Color(0xFF1B1B1D), Color(0xFFCFCFCF), Color(0xFF777777))
}

private const val INDENT = "  "

private fun buildChapterText(content: ChapterContent, sourceName: String, s: ReaderSettings): Pair<AnnotatedString, Int> {
    val body = content.text.ifBlank {
        "(Chương này không có nội dung hoặc nội dung bị khoá. Hãy mở trang nguồn để đọc.)"
    }
    var headerLen = 0
    val str = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (s.fontSize * 1.25f).sp)) { append(content.title) }
        append('\n')
        withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontSize = (s.fontSize * 0.7f).sp)) {
            append("Nguồn: $sourceName")
        }
        append('\n')
        headerLen = length
        body.split('\n').forEachIndexed { i, para ->
            if (i > 0) append('\n')
            append(INDENT)
            append(para)
        }
    }
    return str to headerLen
}

private fun paginate(
    text: AnnotatedString,
    headerLen: Int,
    measurer: TextMeasurer,
    style: TextStyle,
    width: Int,
    height: Int,
): List<Page> {
    if (width <= 0 || height <= 0) return emptyList()
    val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = width))
    val pages = mutableListOf<Page>()
    var startLine = 0
    while (startLine < layout.lineCount) {
        val top = layout.getLineTop(startLine)
        var end = startLine
        while (end + 1 < layout.lineCount && layout.getLineBottom(end + 1) - top <= height) end++
        val s = layout.getLineStart(startLine)
        val e = layout.getLineEnd(end)
        if (e > s) {
            pages += Page(text.subSequence(s, e), s, e, (s - headerLen).coerceAtLeast(0))
        }
        startLine = end + 1
    }
    return pages
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    story: Story,
    startChapter: Int,
    startOffset: Int,
    library: Library,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val settings by library.settings.collectAsState()
    val bookmarks by library.bookmarks.collectAsState()
    val palette = paletteOf(settings.theme)
    val sourceName = Sources.byId(story.sourceId)?.name ?: story.sourceId

    var detail by remember { mutableStateOf<StoryDetail?>(Repository.cachedDetail(story)) }
    var chapterIndex by remember { mutableIntStateOf(startChapter) }
    var content by remember { mutableStateOf<ChapterContent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var anchor by remember { mutableIntStateOf(startOffset) }
    var anchorToEnd by remember { mutableStateOf(false) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    // Giữ màn hình sáng & ẩn thanh hệ thống khi đọc
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(showMenu) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showMenu) controller?.show(WindowInsetsCompat.Type.systemBars())
        else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(reloadKey) {
        if (detail == null) {
            try {
                detail = Repository.detail(story)
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    val chapters = detail?.chapters.orEmpty()
    val fullStory = detail?.story ?: story

    LaunchedEffect(detail, chapterIndex, reloadKey) {
        val d = detail ?: return@LaunchedEffect
        val ch = d.chapters.getOrNull(chapterIndex) ?: run {
            error = "Không tìm thấy chương"; return@LaunchedEffect
        }
        content = null; error = null
        try {
            content = Repository.chapter(story, ch)
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    // Tải trước chương kế tiếp
    LaunchedEffect(content) {
        val d = detail ?: return@LaunchedEffect
        if (content != null) d.chapters.getOrNull(chapterIndex + 1)?.let { runCatching { Repository.chapter(story, it) } }
    }

    val measurer = rememberTextMeasurer(cacheSize = 4)
    val textStyle = TextStyle(
        color = palette.fg,
        fontSize = settings.fontSize.sp,
        lineHeight = (settings.fontSize * settings.lineHeight).sp,
        fontFamily = if (settings.serif) FontFamily.Serif else FontFamily.SansSerif,
        textAlign = TextAlign.Justify,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    val insets = WindowInsets.systemBarsIgnoringVisibility
    val topPad = with(density) { insets.getTop(this).toDp() } + 30.dp
    val bottomPad = with(density) { insets.getBottom(this).toDp() } + 30.dp
    val hPad = 22.dp

    BoxWithConstraints(Modifier.fillMaxSize().background(palette.bg)) {
        val textW = with(density) { (maxWidth - hPad * 2).roundToPx() }
        val textH = with(density) { (maxHeight - topPad - bottomPad).roundToPx() } - with(density) { 4.dp.roundToPx() }
        val c = content
        val pages: List<Page> = remember(c, settings, textW, textH, palette) {
            if (c == null) emptyList() else {
                val (str, headerLen) = buildChapterText(c, sourceName, settings)
                paginate(str, headerLen, measurer, textStyle, textW, textH)
            }
        }
        val currentPages by rememberUpdatedState(pages)
        val currentChapters by rememberUpdatedState(chapters)

        // Đặt trang theo vị trí neo khi dàn trang lại
        LaunchedEffect(pages) {
            if (pages.isNotEmpty()) {
                pageIndex = if (anchorToEnd) pages.lastIndex
                else pages.indexOfLast { it.bodyStart <= anchor }.coerceAtLeast(0)
                anchorToEnd = false
                anchor = pages[pageIndex].bodyStart
            }
        }

        // Lưu tiến độ đọc
        LaunchedEffect(pageIndex, pages) {
            val ch = chapters.getOrNull(chapterIndex)
            if (pages.isNotEmpty() && ch != null) {
                library.saveProgress(
                    ReadProgress(fullStory, chapterIndex, content?.title ?: ch.title, anchor, System.currentTimeMillis())
                )
            }
        }

        fun goChapter(index: Int, toEnd: Boolean = false) {
            if (index !in currentChapters.indices) return
            anchor = 0
            anchorToEnd = toEnd
            chapterIndex = index
        }

        fun canForward() = currentPages.isNotEmpty() &&
            (pageIndex + 1 < currentPages.size || chapterIndex + 1 < currentChapters.size)

        fun canBackward() = currentPages.isNotEmpty() && (pageIndex > 0 || chapterIndex > 0)

        fun commit(forward: Boolean) {
            val ps = currentPages
            if (forward) {
                if (pageIndex + 1 < ps.size) {
                    pageIndex++; anchor = ps[pageIndex].bodyStart
                } else goChapter(chapterIndex + 1)
            } else {
                if (pageIndex > 0) {
                    pageIndex--; anchor = ps[pageIndex].bodyStart
                } else goChapter(chapterIndex - 1, toEnd = true)
            }
        }

        fun flip(forward: Boolean) {
            if (progress.isRunning) return
            if (forward && !canForward()) {
                if (currentPages.isNotEmpty()) Toast.makeText(context, "Đã hết truyện", Toast.LENGTH_SHORT).show()
                return
            }
            if (!forward && !canBackward()) return
            scope.launch {
                progress.animateTo(if (forward) 1f else -1f, tween(320))
                commit(forward)
                progress.snapTo(0f)
            }
        }

        val pageModifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    when {
                        showMenu -> showMenu = false
                        pos.x < size.width * 0.3f -> flip(false)
                        pos.x > size.width * 0.7f -> flip(true)
                        else -> showMenu = true
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val p = progress.value
                            when {
                                p > 0.2f -> { progress.animateTo(1f, tween(200)); commit(true); progress.snapTo(0f) }
                                p < -0.2f -> { progress.animateTo(-1f, tween(200)); commit(false); progress.snapTo(0f) }
                                else -> progress.animateTo(0f, tween(150))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { progress.animateTo(0f) } },
                ) { change, dragAmount ->
                    change.consume()
                    var np = (progress.value - dragAmount / size.width).coerceIn(-1f, 1f)
                    if (np > 0 && !canForward()) np = 0f
                    if (np < 0 && !canBackward()) np = 0f
                    scope.launch { progress.snapTo(np) }
                }
            }

        @Composable
        fun PageView(index: Int, modifier: Modifier = Modifier) {
            val page = pages.getOrNull(index)
            val chTitle = content?.title ?: chapters.getOrNull(chapterIndex)?.title.orEmpty()
            Box(modifier.fillMaxSize().background(palette.bg)) {
                Text(
                    chTitle,
                    color = palette.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = hPad, end = hPad, top = topPad - 24.dp),
                )
                if (page != null) {
                    Text(
                        page.text,
                        style = textStyle,
                        softWrap = true,
                        modifier = Modifier.padding(start = hPad, end = hPad, top = topPad),
                    )
                } else {
                    val next = index >= pages.size
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (next) "Chương tiếp theo…" else "Chương trước…",
                            color = palette.dim, style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .padding(start = hPad, end = hPad, bottom = bottomPad - 24.dp),
                ) {
                    Text("Nguồn: $sourceName", color = palette.dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    if (page != null) Text("${index + 1}/${pages.size}", color = palette.dim, fontSize = 11.sp)
                }
            }
        }

        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize().then(pageModifier), contentAlignment = Alignment.Center) {
                if (error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("Không tải được nội dung:\n$error", color = palette.fg, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { error = null; reloadKey++ }) { Text("Thử lại") }
                            OutlinedButton(onClick = {
                                openUrl(context, chapters.getOrNull(chapterIndex)?.url ?: fullStory.url)
                            }) { Text("Mở trang nguồn") }
                        }
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
        } else {
            val p = progress.value
            val widthPx = constraints.maxWidth.toFloat()
            Box(pageModifier) {
                if (p > 0f) {
                    // Lật tới: trang kế nằm dưới, trang hiện tại xoay quanh gáy sách
                    PageView(pageIndex + 1)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f * (1f - p))))
                    FlippingPage(p, widthPx) { PageView(pageIndex) }
                } else if (p < 0f) {
                    PageView(pageIndex)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f * (-p))))
                    FlippingPage(1f + p, widthPx) { PageView(pageIndex - 1) }
                } else {
                    PageView(pageIndex)
                }
            }
        }

        // ------------ Menu ------------
        AnimatedVisibility(showMenu, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            val page = pages.getOrNull(pageIndex)
            val nextStart = pages.getOrNull(pageIndex + 1)?.bodyStart ?: Int.MAX_VALUE
            val marked = page != null && bookmarks.any {
                it.story.sourceId == story.sourceId && it.story.url == story.url &&
                    it.chapterIndex == chapterIndex && it.offset >= page.bodyStart && it.offset < nextStart
            }
            Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                    Column(Modifier.weight(1f)) {
                        Text(fullStory.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(
                            content?.title ?: chapters.getOrNull(chapterIndex)?.title.orEmpty(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(enabled = page != null, onClick = {
                        if (page == null) return@IconButton
                        if (marked) {
                            bookmarks.filter {
                                it.story.sourceId == story.sourceId && it.story.url == story.url &&
                                    it.chapterIndex == chapterIndex && it.offset >= page.bodyStart && it.offset < nextStart
                            }.forEach { library.removeBookmark(it) }
                            Toast.makeText(context, "Đã bỏ bookmark", Toast.LENGTH_SHORT).show()
                        } else {
                            val snippet = page.text.text.replace(INDENT, "").replace('\n', ' ').trim().take(100)
                            library.addBookmark(
                                Bookmark(
                                    fullStory, chapterIndex,
                                    content?.title ?: chapters.getOrNull(chapterIndex)?.title.orEmpty(),
                                    page.bodyStart, snippet, System.currentTimeMillis(),
                                )
                            )
                            Toast.makeText(context, "Đã thêm bookmark", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Bookmark",
                            tint = if (marked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showChapters = true }) { Icon(Icons.AutoMirrored.Filled.List, "Mục lục") }
                    IconButton(onClick = {
                        openUrl(context, chapters.getOrNull(chapterIndex)?.url ?: fullStory.url)
                    }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Mở trang nguồn") }
                }
            }
        }

        AnimatedVisibility(showMenu, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = chapterIndex > 0, onClick = { goChapter(chapterIndex - 1) }) {
                            Icon(Icons.Filled.SkipPrevious, "Chương trước")
                        }
                        if (pages.size > 1) {
                            Slider(
                                value = pageIndex.toFloat(),
                                onValueChange = {
                                    pageIndex = it.toInt().coerceIn(0, pages.lastIndex)
                                    anchor = pages[pageIndex].bodyStart
                                },
                                valueRange = 0f..pages.lastIndex.toFloat(),
                                modifier = Modifier.weight(1f),
                            )
                        } else Spacer(Modifier.weight(1f))
                        IconButton(enabled = chapterIndex + 1 < chapters.size, onClick = { goChapter(chapterIndex + 1) }) {
                            Icon(Icons.Filled.SkipNext, "Chương sau")
                        }
                    }
                    Text(
                        "Chương ${chapterIndex + 1}/${chapters.size} • Trang ${pageIndex + 1}/${pages.size.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(6.dp))
                    if (showSettings) {
                        ReaderSettingsPanel(settings) { library.updateSettings(it) }
                    }
                    TextButton(onClick = { showSettings = !showSettings }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (showSettings) "Ẩn cài đặt" else "Cỡ chữ & giao diện")
                    }
                }
            }
        }
    }

    if (showChapters) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (chapterIndex - 3).coerceAtLeast(0))
        AlertDialog(
            onDismissRequest = { showChapters = false },
            title = { Text("Mục lục (${chapters.size})") },
            text = {
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 460.dp)) {
                    itemsIndexed(chapters) { i, ch ->
                        Text(
                            ch.title,
                            color = if (i == chapterIndex) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (i == chapterIndex) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable {
                                showChapters = false; showMenu = false
                                anchor = 0; anchorToEnd = false; chapterIndex = i
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChapters = false }) { Text("Đóng") } },
        )
    }
}

/** Trang đang lật: [visible] = 1 là phẳng hoàn toàn, 0 là đã lật xong (vuông góc màn hình). */
@Composable
private fun FlippingPage(visible: Float, widthPx: Float, content: @Composable () -> Unit) {
    val turned = 1f - visible
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0.5f)
            rotationY = -90f * turned
            cameraDistance = 14f * density
            // Đẩy nhẹ sang trái để giống mép giấy cong theo gáy
            translationX = -widthPx * 0.04f * turned
        }
    ) {
        content()
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.85f to Color.Black.copy(alpha = 0.10f * turned),
                    1f to Color.Black.copy(alpha = 0.35f * turned),
                )
            )
        )
    }
}

@Composable
private fun ReaderSettingsPanel(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cỡ chữ", modifier = Modifier.width(84.dp), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onChange(s.copy(fontSize = (s.fontSize - 1).coerceAtLeast(12))) }) { Text("A-") }
            Text("${s.fontSize}", modifier = Modifier.padding(horizontal = 12.dp))
            OutlinedButton(onClick = { onChange(s.copy(fontSize = (s.fontSize + 1).coerceAtMost(36))) }) { Text("A+") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Giãn dòng", modifier = Modifier.width(84.dp), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = s.lineHeight,
                onValueChange = { onChange(s.copy(lineHeight = (Math.round(it * 10) / 10f))) },
                valueRange = 1.2f..2.2f,
                steps = 9,
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nền", modifier = Modifier.width(78.dp), style = MaterialTheme.typography.bodySmall)
            listOf(ReaderTheme.LIGHT to "Sáng", ReaderTheme.SEPIA to "Giấy", ReaderTheme.DARK to "Tối").forEach { (t, label) ->
                FilterChip(selected = s.theme == t, onClick = { onChange(s.copy(theme = t)) }, label = { Text(label) },
                    leadingIcon = { Box(Modifier.size(12.dp).background(paletteOf(t).bg)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Phông", modifier = Modifier.width(78.dp), style = MaterialTheme.typography.bodySmall)
            FilterChip(selected = s.serif, onClick = { onChange(s.copy(serif = true)) }, label = { Text("Có chân") })
            FilterChip(selected = !s.serif, onClick = { onChange(s.copy(serif = false)) }, label = { Text("Không chân") })
        }
    }
}
