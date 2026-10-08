package net.topvl.storyreader

import net.topvl.storyreader.source.IMAGE_MARK
import net.topvl.storyreader.source.toReadableText
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageExtractTest {
    @Test fun keepsIllustrationsInPlace() {
        val html = """
            <div id="c">
              <p>Đoạn một.</p>
              <p><img src="/images/minh-hoa-1.jpg"></p>
              <p>Đoạn hai.</p>
              <img data-src="https://cdn.example.com/wp-content/uploads/2025/anh-2.png" src="data:image/gif;base64,AAAA">
              <img src="https://ads.example.com/banner.gif">
              <img src="https://example.com/static/icons/like.png" width="16" height="16">
              <p>Đoạn ba.</p>
            </div>
        """.trimIndent()
        val text = Jsoup.parse(html, "https://site.vn/truyen/chuong-1").getElementById("c")!!.toReadableText()
        assertEquals(
            listOf(
                "Đoạn một.",
                "${IMAGE_MARK}https://site.vn/images/minh-hoa-1.jpg",
                "Đoạn hai.",
                "${IMAGE_MARK}https://cdn.example.com/wp-content/uploads/2025/anh-2.png",
                "Đoạn ba.",
            ),
            text.lines(),
        )
    }
}
