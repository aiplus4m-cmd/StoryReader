package net.topvl.storyreader.source

data class Story(
    val sourceId: String,
    val url: String,
    val title: String,
    val author: String = "",
    val cover: String = "",
    val description: String = "",
    /** Thông tin phụ hiển thị ở kết quả tìm kiếm (số chương, lượt đọc…), không lưu trữ. */
    val info: String = "",
)

data class Chapter(
    val title: String,
    val url: String,
)

data class StoryDetail(
    val story: Story,
    val chapters: List<Chapter>,
)

data class ChapterContent(
    val title: String,
    val text: String,
)
