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
}

object Sources {
    val all: List<StorySource> = listOf(
        WattpadSource,
        TruyenFullSource,
        VietNamThuQuanSource,
        GutenbergSource,
    )

    fun byId(id: String): StorySource? = all.firstOrNull { it.id == id }
}
