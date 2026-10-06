package net.topvl.storyreader.source

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Project Gutenberg (qua API công khai Gutendex) - sách phạm vi công cộng. */
object GutenbergSource : StorySource {
    override val id = "gutenberg"
    override val name = "Project Gutenberg"
    override val homepage = "https://www.gutenberg.org"
    override val language = "đa ngôn ngữ"

    private const val API = "https://gutendex.com/books/"
    private val textCache = ConcurrentHashMap<String, List<ChapterContent>>()

    override suspend fun search(query: String, page: Int): List<Story> {
        val json = JSONObject(Http.get("$API?search=${query.urlEncode()}&page=$page"))
        val arr = json.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val bookId = o.optInt("id")
            val authors = o.optJSONArray("authors")
            val author = if (authors == null) "" else
                (0 until authors.length()).joinToString(", ") { authors.getJSONObject(it).optString("name") }
            val formats = o.optJSONObject("formats")
            Story(
                sourceId = id,
                url = "https://www.gutenberg.org/ebooks/$bookId",
                title = o.optString("title"),
                author = author,
                cover = formats?.optString("image/jpeg").orEmpty(),
                description = buildString {
                    val subjects = o.optJSONArray("subjects")
                    if (subjects != null) append((0 until subjects.length()).joinToString("; ") { subjects.getString(it) })
                },
            )
        }
    }

    private fun bookId(url: String) = Regex("""(\d+)""").findAll(url).last().value

    private suspend fun loadBook(bookId: String): List<ChapterContent> {
        textCache[bookId]?.let { return it }
        val raw = Http.get("https://www.gutenberg.org/cache/epub/$bookId/pg$bookId.txt")
        var body = raw.replace("\r\n", "\n")
        val start = Regex("""\*\*\* ?START OF (THE|THIS) PROJECT GUTENBERG.*\n""").find(body)
        if (start != null) body = body.substring(start.range.last + 1)
        val end = Regex("""\*\*\* ?END OF (THE|THIS) PROJECT GUTENBERG""").find(body)
        if (end != null) body = body.substring(0, end.range.first)
        val chapters = splitChapters(body)
        textCache[bookId] = chapters
        return chapters
    }

    internal fun splitChapters(body: String): List<ChapterContent> {
        val heading = Regex("""(?m)^\s*(CHAPTER|Chapter|CHAPITRE|Chapitre|KAPITEL|Kapitel|BOOK|PART)\s+[\dIVXLCivxlc]+[^\n]{0,80}$""")
        val marks = heading.findAll(body).toList()
        val result = mutableListOf<ChapterContent>()
        if (marks.size >= 2) {
            val pre = body.substring(0, marks.first().range.first)
            if (pre.isNotBlank() && pre.length > 500) result += ChapterContent("Mở đầu", paragraphs(pre))
            marks.forEachIndexed { i, m ->
                val endIdx = if (i + 1 < marks.size) marks[i + 1].range.first else body.length
                val text = body.substring(m.range.last + 1, endIdx)
                result += ChapterContent(m.value.trim(), paragraphs(text))
            }
        } else {
            // Không có tiêu đề chương: chia theo độ dài
            val paras = paragraphs(body).split("\n")
            val chunk = StringBuilder()
            var n = 1
            for (p in paras) {
                chunk.append(p).append('\n')
                if (chunk.length > 15000) {
                    result += ChapterContent("Phần ${n++}", chunk.toString().trim()); chunk.clear()
                }
            }
            if (chunk.isNotBlank()) result += ChapterContent("Phần $n", chunk.toString().trim())
        }
        return result.filter { it.text.isNotBlank() }
    }

    /** Ghép các dòng bị ngắt cứng thành đoạn văn. */
    private fun paragraphs(text: String): String =
        text.split(Regex("""\n\s*\n"""))
            .map { it.replace(Regex("""\s*\n\s*"""), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    override suspend fun detail(story: Story): StoryDetail {
        val bid = bookId(story.url)
        val chapters = loadBook(bid).mapIndexed { i, c -> Chapter(c.title, "${story.url}#$i") }
        return StoryDetail(story, chapters)
    }

    override suspend fun chapter(chapter: Chapter): ChapterContent {
        val base = chapter.url.substringBefore('#')
        val idx = chapter.url.substringAfter('#', "0").toIntOrNull() ?: 0
        val list = loadBook(bookId(base))
        return list.getOrNull(idx) ?: throw IllegalStateException("Không tìm thấy chương")
    }
}
