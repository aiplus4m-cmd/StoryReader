package net.topvl.storyreader.source

import org.json.JSONObject
import org.jsoup.Jsoup

object WattpadSource : StorySource {
    override val id = "wattpad"
    override val name = "Wattpad"
    override val homepage = "https://www.wattpad.com"
    override val language = "đa ngôn ngữ"

    private const val API = "https://www.wattpad.com"
    private const val PAGE_SIZE = 20

    override suspend fun search(query: String, page: Int): List<Story> {
        val offset = (page - 1) * PAGE_SIZE
        val url = "$API/v4/search/stories?query=${query.urlEncode()}&limit=$PAGE_SIZE&offset=$offset" +
            "&fields=stories(id,title,cover,description,user(name),numParts,url),total"
        val json = JSONObject(Http.get(url, mapOf("Accept" to "application/json")))
        val arr = json.optJSONArray("stories") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Story(
                sourceId = id,
                url = o.optString("url").ifBlank { "$API/story/${o.optString("id")}" },
                title = o.optString("title"),
                author = o.optJSONObject("user")?.optString("name").orEmpty(),
                cover = o.optString("cover"),
                description = o.optString("description"),
            )
        }
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
