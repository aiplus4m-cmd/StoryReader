package net.topvl.storyreader

import kotlinx.coroutines.runBlocking
import net.topvl.storyreader.source.GutenbergSource
import net.topvl.storyreader.source.StorySource
import net.topvl.storyreader.source.TruyenFullSource
import net.topvl.storyreader.source.WattpadSource
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kiểm tra trực tiếp (cần mạng) — chạy trên CI để phát hiện nguồn thay đổi cấu trúc. */
class SourcesLiveTest {

    private fun check(source: StorySource, query: String) = runBlocking {
        println("==== ${source.name}: search '$query'")
        val results = source.search(query, 1)
        println("results=${results.size}")
        results.take(3).forEach { println("  - ${it.title} | ${it.author} | ${it.url} | cover=${it.cover.take(60)}") }
        assertTrue("Không có kết quả", results.isNotEmpty())
        val detail = source.detail(results.first())
        println("detail: ${detail.story.title} by ${detail.story.author}; chapters=${detail.chapters.size}")
        println("desc: ${detail.story.description.take(200)}")
        detail.chapters.take(3).forEach { println("  * ${it.title} -> ${it.url}") }
        assertTrue("Không có chương", detail.chapters.isNotEmpty())
        val ch = source.chapter(detail.chapters.first())
        println("chapter '${ch.title}' length=${ch.text.length}")
        println(ch.text.take(400))
        println("...")
        println(ch.text.takeLast(200))
        assertTrue("Nội dung chương trống", ch.text.length > 100)
    }

    @Test fun wattpad() = check(WattpadSource, "tình yêu")
    @Test fun truyenfull() = check(TruyenFullSource, "tien nghich")
    @Test fun gutenberg() = check(GutenbergSource, "sherlock")
}
