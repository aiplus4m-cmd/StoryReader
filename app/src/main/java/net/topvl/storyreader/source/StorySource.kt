package net.topvl.storyreader.source

interface StorySource {
    /** Mã định danh duy nhất của nguồn. */
    val id: String

    /** Tên hiển thị. */
    val name: String

    /** Trang chủ của nguồn (hiển thị ghi rõ nguồn). */
    val homepage: String

    /** Ngôn ngữ chủ yếu. */
    val language: String get() = "vi"

    suspend fun search(query: String, page: Int = 1): List<Story>

    suspend fun detail(story: Story): StoryDetail

    suspend fun chapter(chapter: Chapter): ChapterContent

    /** Nguồn có nhận đường dẫn này không (để mở trực tiếp khi người dùng dán link). */
    fun handles(url: String): Boolean {
        val host = runCatching { java.net.URI(homepage).host.removePrefix("www.") }.getOrNull() ?: return false
        val h = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: return false
        return h == host || h.endsWith(".$host")
    }

    /** Chuyển một đường dẫn (truyện hoặc chương) thành truyện. */
    suspend fun resolve(url: String): Story? =
        Story(sourceId = id, url = url.replace(Regex("""/chuong-[^/]*/?$"""), "/"), title = url)
}

object Sources {
    val all: List<StorySource> = listOf(
        WattpadSource,
        TruyenFullSource,
        VietNamThuQuanSource,
        TieuThuyetSource,
        TruyenCSource,
        LmvnSource,
        GutenbergSource,
    )

    fun byId(id: String): StorySource? = all.firstOrNull { it.id == id }

    fun isUrl(text: String) = Regex("""^(https?://)?([a-z0-9-]+\.)+[a-z]{2,}(/\S*)?$""", RegexOption.IGNORE_CASE)
        .matches(text.trim()) && text.contains('/')

    /** Mở trực tiếp từ đường dẫn người dùng dán vào ô tìm kiếm. */
    suspend fun resolve(text: String): Story? {
        val url = text.trim().let { if (it.startsWith("http")) it else "https://$it" }
        return all.firstOrNull { it.handles(url) }?.resolve(url)
    }
}
