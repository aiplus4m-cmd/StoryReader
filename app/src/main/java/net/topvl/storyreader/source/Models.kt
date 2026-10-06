package net.topvl.storyreader.source

data class Story(
    val sourceId: String,
    val url: String,
    val title: String,
    val author: String = "",
    val cover: String = "",
    val description: String = "",
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
