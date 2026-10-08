package net.topvl.storyreader

import kotlinx.coroutines.runBlocking
import net.topvl.storyreader.source.Sources
import net.topvl.storyreader.source.Story
import net.topvl.storyreader.source.WattpadSource
import net.topvl.storyreader.source.coreTitle
import net.topvl.storyreader.source.rankBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankTest {
    private fun s(t: String) = Story("x", t, t)

    @Test fun coreTitleStripsTags() {
        assertEquals("Chạy không thoát", coreTitle("[ĐM/NP/Thô Tục] Chạy không thoát"))
        assertEquals("BƯỚM ĐEN", coreTitle("[HOÀN EDIT] BƯỚM ĐEN - Xuân Phong Lựu Hoả"))
    }

    @Test fun exactTitleRankedFirst() {
        val list = listOf(s("Tình yêu của ác ma 2"), s("Ác ma trong tình yêu"), s("[Hoàn] Tình Yêu Của Ác Ma - Lam"))
        assertEquals("[Hoàn] Tình Yêu Của Ác Ma - Lam", list.rankBy("tinh yeu cua ac ma").first().title)
    }

    @Test fun detectsUrls() {
        assertTrue(Sources.isUrl("https://www.wattpad.com/story/343410532-cung-chieu"))
        assertTrue(Sources.isUrl("wattpad.com/1350129368-cung-chieu-chuong-2"))
        assertTrue(!Sources.isUrl("cưng chiều"))
    }

    /** Cần mạng: link chương Wattpad (thường gặp trên Google) phải mở ra đúng truyện. */
    @Test fun wattpadChapterLinkResolvesToStory() = runBlocking {
        val story = WattpadSource.search("Cưng Chiều", 1).first()
        val detail = WattpadSource.detail(story)
        val chapterUrl = detail.chapters[1].url
        val resolved = Sources.resolve(chapterUrl)!!
        println("chapter=$chapterUrl -> ${resolved.url} (${resolved.title})")
        val id = Regex("""/story/(\d+)""").find(story.url)!!.groupValues[1]
        assertTrue(resolved.url.endsWith("/story/$id"))
    }
}
