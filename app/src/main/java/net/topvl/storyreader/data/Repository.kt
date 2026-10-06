package net.topvl.storyreader.data

import android.util.LruCache
import net.topvl.storyreader.source.Chapter
import net.topvl.storyreader.source.ChapterContent
import net.topvl.storyreader.source.Sources
import net.topvl.storyreader.source.Story
import net.topvl.storyreader.source.StoryDetail
import java.util.concurrent.ConcurrentHashMap

/** Bộ nhớ đệm trong phiên cho chi tiết truyện và nội dung chương. */
object Repository {
    private val details = ConcurrentHashMap<String, StoryDetail>()
    private val chapters = LruCache<String, ChapterContent>(30)

    private fun key(story: Story) = "${story.sourceId}|${story.url}"

    fun cachedDetail(story: Story): StoryDetail? = details[key(story)]

    suspend fun detail(story: Story, refresh: Boolean = false): StoryDetail {
        if (!refresh) details[key(story)]?.let { return it }
        val source = Sources.byId(story.sourceId) ?: error("Không tìm thấy nguồn ${story.sourceId}")
        val d = source.detail(story)
        details[key(story)] = d
        return d
    }

    suspend fun chapter(story: Story, chapter: Chapter): ChapterContent {
        val k = "${story.sourceId}|${chapter.url}"
        chapters.get(k)?.let { return it }
        val source = Sources.byId(story.sourceId) ?: error("Không tìm thấy nguồn ${story.sourceId}")
        val c = source.chapter(chapter)
        if (c.text.isNotBlank()) chapters.put(k, c)
        return c
    }
}
