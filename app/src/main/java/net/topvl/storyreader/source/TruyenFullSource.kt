package net.topvl.storyreader.source

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object TruyenFullSource : StorySource {
    override val id = "truyenfull"
    override val name = "TruyenFull"
    override val homepage = "https://truyenfull.live"

    override suspend fun search(query: String, page: Int): List<Story> {
        val doc = Http.doc("$homepage/tim-kiem/?tukhoa=${query.urlEncode()}&page=$page")
        return doc.select(".list-truyen .row").mapNotNull { row ->
            val a = row.selectFirst("h3.truyen-title a") ?: return@mapNotNull null
            Story(
                sourceId = id,
                url = a.absUrl("href"),
                title = a.text(),
                author = row.selectFirst(".author")?.text().orEmpty(),
                cover = row.selectFirst("[data-image]")?.absUrl("data-image")
                    ?: row.selectFirst("img")?.absUrl("src").orEmpty(),
            )
        }.filter { it.url.isNotBlank() }
    }

    private fun chaptersOf(doc: Document): List<Chapter> =
        doc.select("#list-chapter ul.list-chapter li a").map { it.toChapter() }

    private fun Element.toChapter(): Chapter {
        val t = attr("title").ifBlank {
            val label = selectFirst(".chapter-text")?.text().orEmpty()
            val clone = clone().also { it.select(".chapter-text").remove() }
            listOf(label, clone.text()).filter { it.isNotBlank() }.joinToString(" ")
        }
        return Chapter(t, absUrl("href"))
    }

    override suspend fun detail(story: Story): StoryDetail = coroutineScope {
        val base = story.url.trimEnd('/') + "/"
        val doc = Http.doc(base)
        val totalPages = doc.selectFirst("#total-page")?.attr("value")?.toIntOrNull()
            ?: doc.select(".pagination a[href*=trang-]").mapNotNull {
                Regex("""trang-(\d+)""").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
            }.maxOrNull() ?: 1

        val chapters = chaptersOf(doc).toMutableList()
        // Tải các trang danh sách chương còn lại theo lô.
        (2..totalPages).chunked(8).forEach { batch ->
            batch.map { p -> async { runCatching { chaptersOf(Http.doc("${base}trang-$p/")) }.getOrDefault(emptyList()) } }
                .forEach { chapters += it.await() }
        }

        val desc = doc.selectFirst(".desc-text")?.toReadableText()?.let(::stripSpam).orEmpty()
        val title = doc.selectFirst("h3.title")?.text()?.ifBlank { null } ?: story.title
        StoryDetail(
            story = story.copy(
                title = title,
                author = doc.select("a[itemprop=author]").joinToString(", ") { it.text() }.ifBlank { story.author },
                cover = doc.selectFirst(".book img")?.absUrl("src")?.ifBlank { null } ?: story.cover,
                description = desc.ifBlank { story.description },
            ),
            chapters = chapters.distinctBy { it.url }.map { it.copy(title = it.title.removePrefix("$title - ").trim()) },
        )
    }

    /** Bỏ các dòng chèn quảng cáo/từ khoá của trang nguồn. */
    private fun stripSpam(text: String): String =
        text.lines().filterNot { line ->
            val l = line.lowercase()
            line.length < 120 && (l.contains("truyenfull") || l.contains("truyen full"))
        }.joinToString("\n").trim()

    override suspend fun chapter(chapter: Chapter): ChapterContent {
        val doc = Http.doc(chapter.url)
        val content = doc.selectFirst("#chapter-c") ?: throw IllegalStateException("Không đọc được nội dung chương")
        val storyTitle = doc.selectFirst("a.truyen-title")?.text().orEmpty()
        val title = (doc.selectFirst("a.chapter-title")?.let { it.toChapter().title } ?: chapter.title)
            .removePrefix("$storyTitle - ").trim()
        return ChapterContent(title, stripSpam(content.toReadableText()))
    }
}
