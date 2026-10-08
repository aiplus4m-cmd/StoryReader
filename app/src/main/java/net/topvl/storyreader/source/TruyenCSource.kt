package net.topvl.storyreader.source

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TruyenC – truyenc.com. Trang không có chức năng tìm kiếm nên ứng dụng tìm trong
 * danh mục truyện công khai (sitemap.xml) của trang theo tên truyện.
 */
object TruyenCSource : StorySource {
    override val id = "truyenc"
    override val name = "TruyenC"
    override val homepage = "https://truyenc.com"

    private const val PAGE_SIZE = 30
    private val storyUrl = Regex("""https?://truyenc\.com/truyen/([a-z0-9-]+)-(\d+)$""")
    private val mutex = Mutex()
    private var index: List<Pair<String, String>>? = null // (url, slug đã chuẩn hoá)

    private suspend fun index(): List<Pair<String, String>> = mutex.withLock {
        index ?: Regex("""<loc>\s*([^<\s]+)\s*</loc>""").findAll(Http.get("$homepage/sitemap.xml"))
            .mapNotNull { m ->
                val url = m.groupValues[1].trim()
                storyUrl.find(url)?.let { url to it.groupValues[1].replace('-', ' ') }
            }.distinctBy { it.first }.toList().also { index = it }
    }

    private fun titleFromSlug(slug: String) =
        slug.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    override suspend fun search(query: String, page: Int): List<Story> {
        val words = query.foldVi().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val q = words.joinToString(" ")
        return index()
            .filter { (_, slug) -> words.all { w -> " $slug ".contains(" $w ") || slug.contains(w) } }
            .sortedByDescending { (_, slug) -> if (slug.contains(q)) 1 else 0 }
            .drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE)
            .map { (url, slug) -> Story(sourceId = id, url = url, title = titleFromSlug(slug)) }
    }

    override suspend fun resolve(url: String): Story? {
        if (storyUrl.matches(url)) return Story(sourceId = id, url = url, title = url)
        // Link chương: lấy link "Trở về truyện"
        val back = Http.doc(url).selectFirst("a.header-title[href*='/truyen/']")?.absUrl("href") ?: return null
        return Story(sourceId = id, url = back, title = url)
    }

    override suspend fun detail(story: Story): StoryDetail {
        val doc = Http.doc(story.url)
        val info = doc.selectFirst(".card-full-left .content")
        val title = info?.selectFirst("h2")?.text()?.removePrefix("Truyện ")?.trim()?.ifBlank { null }
            ?: doc.selectFirst(".page-title")?.text()?.ifBlank { null } ?: story.title
        val chapters = doc.select("#storyChapMain a.story-chap-item").map {
            Chapter(it.selectFirst(".story-chap-name")?.text() ?: it.text(), it.absUrl("href"))
        }.ifEmpty {
            // Truyện ngắn một phần: nút "Đọc truyện"
            info?.select("a[href*='/truyen/']")?.firstOrNull { it.text().contains("Đọc") }
                ?.let { listOf(Chapter(title, it.absUrl("href"))) }.orEmpty()
        }
        return StoryDetail(
            story = story.copy(
                title = title,
                author = info?.selectFirst("h3 b")?.text()?.ifBlank { null } ?: story.author,
                cover = info?.selectFirst("img.story-image")?.absUrl("src")?.ifBlank { null } ?: story.cover,
                description = info?.selectFirst(".d-none.d-sm-block")?.toReadableText()?.ifBlank { null }
                    ?: story.description,
            ),
            chapters = chapters.distinctBy { it.url },
        )
    }

    override suspend fun chapter(chapter: Chapter): ChapterContent {
        val doc = Http.doc(chapter.url)
        val content = doc.selectFirst(".story-content") ?: throw IllegalStateException("Không đọc được nội dung chương")
        content.select("div[id^=M], script, style").remove()
        return ChapterContent(chapter.title, content.toReadableText())
    }
}
