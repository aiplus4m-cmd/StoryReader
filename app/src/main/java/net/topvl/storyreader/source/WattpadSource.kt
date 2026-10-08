package net.topvl.storyreader.source

import org.json.JSONObject
import org.jsoup.Jsoup

object WattpadSource : StorySource {
    override val id = "wattpad"
    override val name = "Wattpad"
    override val homepage = "https://www.wattpad.com"
    override val language = "đa ngôn ngữ"

    private const val API = "https://www.wattpad.com"
    private const val PAGE_SIZE = 50
    private const val FIELDS = "id,title,cover,description,user(name),numParts,readCount,completed,url"

    private fun parseStories(arr: org.json.JSONArray?): List<Story> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val parts = o.optInt("numParts", 0)
            val reads = o.optLong("readCount", 0)
            Story(
                sourceId = id,
                url = o.optString("url").ifBlank { "$API/story/${o.optString("id")}" },
                title = o.optString("title"),
                author = o.optJSONObject("user")?.optString("name").orEmpty(),
                cover = o.optString("cover"),
                description = o.optString("description"),
                info = listOfNotNull(
                    if (parts > 0) "$parts chương" else null,
                    if (reads > 0) "${formatCount(reads)} lượt đọc" else null,
                    if (o.optBoolean("completed")) "Hoàn thành" else null,
                ).joinToString(" • "),
            )
        }
    }

    private fun formatCount(n: Long): String = when {
        n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    override suspend fun search(query: String, page: Int): List<Story> {
        val q = query.trim()
        // "@tên_tác_giả": liệt kê truyện của tác giả đó
        if (q.startsWith("@")) {
            if (page > 1) return emptyList()
            return authorStories(q.removePrefix("@").trim())
        }
        val offset = (page - 1) * PAGE_SIZE
        val url = "$API/v4/search/stories?query=${q.urlEncode()}&limit=$PAGE_SIZE&offset=$offset&mature=1" +
            "&fields=stories($FIELDS),total"
        val json = JSONObject(Http.get(url, mapOf("Accept" to "application/json")))
        return parseStories(json.optJSONArray("stories"))
    }

    /**
     * Truyện của một tác giả. Wattpad chỉ nhận username (vd. "antinh28"), nên nếu người dùng gõ
     * tên hiển thị (vd. "An Tĩnh") thì tra cứu tài khoản trước rồi mới lấy danh sách truyện.
     */
    private suspend fun authorStories(name: String): List<Story> {
        if (name.isBlank()) return emptyList()
        suspend fun storiesOf(username: String): List<Story> =
            parseStories(
                JSONObject(Http.get("$API/api/v3/users/${username.urlEncode()}/stories?limit=50&fields=stories($FIELDS)"))
                    .optJSONArray("stories")
            )
        val direct = runCatching { storiesOf(name.replace(" ", "")) }.getOrNull()
        if (!direct.isNullOrEmpty()) return direct
        val users = org.json.JSONArray(
            Http.get("$API/v4/search/users?query=${name.urlEncode()}&limit=5&fields=username,name")
        )
        val names = (0 until users.length()).map { users.getJSONObject(it).optString("username") }.filter { it.isNotBlank() }
        if (names.isEmpty()) throw IllegalStateException("Không tìm thấy tác giả \"$name\" trên Wattpad")
        return names.take(3).flatMap { runCatching { storiesOf(it) }.getOrDefault(emptyList()) }.distinctBy { it.url }
    }

    override suspend fun resolve(url: String): Story? {
        // Link truyện: /story/123-ten ; link chương: /123456-ten-chuong
        Regex("""/story/(\d+)""").find(url)?.let {
            return Story(sourceId = id, url = "$API/story/${it.groupValues[1]}", title = url)
        }
        val partId = Regex("""wattpad\.com/(\d+)""").find(url)?.groupValues?.get(1) ?: return null
        val o = JSONObject(Http.get("$API/v4/parts/$partId?fields=id,group(id,title)"))
        val group = o.optJSONObject("group") ?: return null
        return Story(sourceId = id, url = "$API/story/${group.optString("id")}", title = group.optString("title"))
    }

    private fun storyId(url: String): String =
        Regex("""/story/(\d+)""").find(url)?.groupValues?.get(1)
            ?: Regex("""(\d{3,})""").find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("URL Wattpad không hợp lệ: $url")

    override suspend fun detail(story: Story): StoryDetail {
        val sid = storyId(story.url)
        val url = "$API/api/v3/stories/$sid?fields=id,title,description,cover,url,user(name),parts(id,title,url)"
        val o = JSONObject(Http.get(url, mapOf("Accept" to "application/json")))
        val parts = o.optJSONArray("parts")
        val chapters = if (parts == null) emptyList() else (0 until parts.length()).map { i ->
            val p = parts.getJSONObject(i)
            Chapter(
                title = p.optString("title").ifBlank { "Phần ${i + 1}" },
                url = p.optString("url").ifBlank { "$API/${p.optString("id")}" },
            )
        }
        return StoryDetail(
            story = story.copy(
                title = o.optString("title").ifBlank { story.title },
                author = o.optJSONObject("user")?.optString("name")?.ifBlank { null } ?: story.author,
                cover = o.optString("cover").ifBlank { story.cover },
                description = o.optString("description").ifBlank { story.description },
            ),
            chapters = chapters,
        )
    }

    override suspend fun chapter(chapter: Chapter): ChapterContent {
        val partId = Regex("""wattpad\.com/(\d+)""").find(chapter.url)?.groupValues?.get(1)
            ?: Regex("""(\d{3,})""").find(chapter.url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("URL chương không hợp lệ")
        val html = Http.get("$API/apiv2/storytext?id=$partId")
        val text = Jsoup.parse(html).body().toReadableText()
        return ChapterContent(chapter.title, text)
    }
}
