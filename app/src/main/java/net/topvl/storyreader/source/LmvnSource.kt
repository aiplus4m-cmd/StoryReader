package net.topvl.storyreader.source

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document

/**
 * Liên Mạng Việt Nam – lmvn.com/truyen. Ô tìm kiếm của trang trả kết quả không ổn định,
 * nên ứng dụng kết hợp kết quả tìm kiếm với danh sách truyện theo chữ cái đầu và tự lọc theo từ khoá.
 */
object LmvnSource : StorySource {
    override val id = "lmvn"
    override val name = "LMVN Truyện"
    override val homepage = "https://lmvn.com/truyen/index.php"

    private const val BASE = "https://lmvn.com/truyen/index.php"
    private const val MAX_LETTER_PAGES = 15

    private fun rows(doc: Document): List<Story> =
        doc.select("td.ucell > a[href*='func=viewpost']").map { a ->
            val tr = a.parents().firstOrNull { it.tagName() == "tr" }
            val author = tr?.select("td.ucell")?.getOrNull(1)?.text().orEmpty()
            Story(
                sourceId = id,
                url = a.absUrl("href"),
                title = a.selectFirst("b")?.text() ?: a.text(),
                author = author,
                description = tr?.selectFirst("td.ucell i")?.text().orEmpty(),
            )
        }

    override suspend fun search(query: String, page: Int): List<Story> = coroutineScope {
        if (page > 1) return@coroutineScope emptyList()
        val q = query.foldVi()
        if (q.length < 2) return@coroutineScope emptyList()
        val words = q.split(' ')
        fun matches(s: Story): Boolean {
            val hay = "${s.title} ${s.author}".foldVi()
            return words.all { hay.contains(it) }
        }

        val siteSearch = async {
            runCatching { rows(Http.doc("$BASE?func=search&keyword=${query.urlEncode()}")) }.getOrDefault(emptyList())
        }
        val letter = q.first().uppercaseChar().let { if (it in 'A'..'Z') it.toString() else "#" }
        val first = runCatching { Http.doc("$BASE?func=main&a=${letter.urlEncode()}") }.getOrNull()
        val lastPage = first?.select("a[href*='page=']")?.mapNotNull {
            Regex("""page=(\d+)""").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
        }?.maxOrNull()?.coerceAtMost(MAX_LETTER_PAGES) ?: 1
        val letterRows = (first?.let(::rows).orEmpty()) + (2..lastPage).map { p ->
            async {
                runCatching {
                    rows(Http.doc("$BASE?func=main&cat=&a=${letter.urlEncode()}&b=&sortby=&sorttypes=desc&tacgiaID=&page=$p"))
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()

        (siteSearch.await() + letterRows).filter(::matches).distinctBy { it.url }
    }

    override suspend fun resolve(url: String): Story? {
        val sid = storyId(url) ?: return null
        return Story(sourceId = id, url = "$BASE?func=viewpost&id=$sid", title = url)
    }

    private fun storyId(url: String) = Regex("""[?&]id=([A-Za-z0-9]+)""").find(url)?.groupValues?.get(1)

    override suspend fun detail(story: Story): StoryDetail {
        val doc = Http.doc(story.url)
        val sid = storyId(story.url)
        val parts = doc.select("a[href*='ssid=']").filter { sid == null || storyId(it.attr("href")) == sid }
            .map { Chapter(it.text(), it.absUrl("href")) }
            .distinctBy { it.url }
        val pageTitle = doc.title().replace("\uFEFF", "").split(" - ").map { it.trim() }
        val title = pageTitle.getOrNull(0)?.ifBlank { null } ?: story.title
        return StoryDetail(
            story = story.copy(
                title = title,
                author = story.author.ifBlank { pageTitle.getOrNull(1).orEmpty() },
            ),
            chapters = parts.ifEmpty { listOf(Chapter(title, story.url)) },
        )
    }

    override suspend fun chapter(chapter: Chapter): ChapterContent {
        val doc = Http.doc(chapter.url)
        val box = doc.selectFirst("div[class^=font_]") ?: throw IllegalStateException("Không đọc được nội dung")
        val content = box.clone()
        content.select("table, script, style").remove()
        // Bỏ các dòng tiêu đề/tác giả căn giữa ở đầu trang
        content.children().takeWhile { it.tagName() == "div" && it.attr("align") == "center" }.forEach { it.remove() }
        return ChapterContent(chapter.title, content.toReadableText())
    }
}
