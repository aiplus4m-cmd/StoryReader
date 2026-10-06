package net.topvl.storyreader.source

/** Việt Nam Thư Quán – thư viện văn học trực tuyến. */
object VietNamThuQuanSource : StorySource {
    override val id = "vntq"
    override val name = "Việt Nam Thư Quán"
    override val homepage = "https://vietnamthuquan.eu"

    override suspend fun search(query: String, page: Int): List<Story> {
        if (page > 1) return emptyList()
        val doc = Http.doc("$homepage/truyen/timkiem?chu=${query.urlEncode()}")
        return doc.select(".list-truyen .row").mapNotNull { row ->
            val a = row.selectFirst(".truyen-title a") ?: return@mapNotNull null
            Story(
                sourceId = id,
                url = a.absUrl("href"),
                title = a.text(),
                author = row.selectFirst(".author")?.text().orEmpty(),
                cover = row.selectFirst("img")?.absUrl("src").orEmpty(),
            )
        }.filter { it.url.contains("/TacPham/") }.distinctBy { it.url }
    }

    override suspend fun detail(story: Story): StoryDetail {
        val base = story.url.trimEnd('/')
        val doc = Http.doc("$base/")
        val total = doc.selectFirst("#vntqTocModal h3")?.text()
            ?.let { Regex("""(\d+)\s*chương""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val chapters = if (total != null && total > 0) {
            (1..total).map { Chapter("Chương $it", "$base/chuong-$it") }
        } else {
            doc.select("#vntqTocList a.vntq-toc-item").map { Chapter(it.text(), it.absUrl("href")) }
                .ifEmpty { listOf(Chapter("Toàn văn", "$base/chuong-1")) }
        }
        val titles = doc.select("h1.vntq-reader-title")
        return StoryDetail(
            story = story.copy(
                title = titles.firstOrNull()?.text()?.ifBlank { null } ?: story.title,
                author = doc.selectFirst(".vntq-author-top a")?.text()?.ifBlank { null } ?: story.author,
                cover = doc.selectFirst("#vntqCoverImg")?.absUrl("src")?.ifBlank { null } ?: story.cover,
            ),
            chapters = chapters,
        )
    }

    override suspend fun chapter(chapter: Chapter): ChapterContent {
        val doc = Http.doc(chapter.url)
        val content = doc.selectFirst("#vntqTextContent") ?: throw IllegalStateException("Không đọc được nội dung chương")
        val label = doc.select(".chuongso_a").map { it.text() }.lastOrNull { it.isNotBlank() } ?: chapter.title
        val sub = doc.select("h1.vntq-reader-title").drop(1).firstOrNull()?.text()
            ?.takeIf { it.isNotBlank() && it.any(Char::isLetterOrDigit) }
        val title = if (sub != null) "$label: $sub" else label
        return ChapterContent(title, content.toReadableText())
    }
}
