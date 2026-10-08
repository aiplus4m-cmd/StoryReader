package net.topvl.storyreader.source

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document

/** Tiểu Thuyết – tieuthuyet.vn */
object TieuThuyetSource : StorySource {
    override val id = "tieuthuyet"
    override val name = "Tiểu Thuyết"
    override val homepage = "https://tieuthuyet.vn"

    override suspend fun search(query: String, page: Int): List<Story> {
        val doc = Http.doc("$homepage/search?q=${query.urlEncode()}&page=$page")
        return doc.select("#story-list-container a.listtruyen_item").mapNotNull { a ->
            val url = a.absUrl("href").ifBlank { return@mapNotNull null }
            val img = a.selectFirst("img")
            Story(
                sourceId = id,
                url = url,
                title = a.selectFirst(".listtruyen_item_title")?.text() ?: a.attr("title"),
                cover = img?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } }.orEmpty(),
            )
        }.distinctBy { it.url }
    }

    private fun chaptersOf(doc: Document): List<Chapter> =
        doc.select("ul.list-chapter a.chapter-link").map {
            Chapter(it.selectFirst(".chapter-text")?.text() ?: it.text(), it.absUrl("href"))
        }

    override suspend fun detail(story: Story): StoryDetail = coroutineScope {
        val doc = Http.doc(story.url)
        val pages = doc.select("#list-chapter .pagination a").mapNotNull {
            Regex("""page_chapter=(\d+)""").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 1
        val chapters = chaptersOf(doc).toMutableList()
        (2..pages).chunked(8).forEach { batch ->
            batch.map { p ->
                async { runCatching { chaptersOf(Http.doc("${story.url}?page_chapter=$p")) }.getOrDefault(emptyList()) }
            }.forEach { chapters += it.await() }
        }
        val desc = doc.selectFirst(".desc-text")?.let { d ->
            d.clone().also { it.select(".seo-story-description").remove() }.toReadableText()
        }.orEmpty()
        StoryDetail(
            story = story.copy(
                title = doc.selectFirst("h1.title")?.text()?.ifBlank { null } ?: story.title,
                author = doc.selectFirst(".author-wrapper")?.text()?.ifBlank { null } ?: story.author,
                cover = doc.selectFirst(".book12 img, .book img, img[itemprop=image]")
                    ?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } }?.ifBlank { null } ?: story.cover,
                description = desc.ifBlank { story.description },
            ),
            chapters = chapters.distinctBy { it.url },
        )
    }

    override suspend fun chapter(chapter: Chapter): ChapterContent {
        val doc = Http.doc(chapter.url)
        val content = doc.selectFirst("#dynamic-content") ?: throw IllegalStateException("Không đọc được nội dung chương")
        val title = doc.selectFirst("a.chapter-title")?.text()?.ifBlank { null } ?: chapter.title
        return ChapterContent(title, content.toReadableText())
    }
}
