package net.topvl.storyreader.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} - $url")
                resp.body?.string() ?: ""
            }
        }

    suspend fun post(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            val builder = Request.Builder().url(url).post(body)
                .header("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} - $url")
                resp.body?.string() ?: ""
            }
        }

    suspend fun doc(url: String, headers: Map<String, String> = emptyMap()): Document =
        Jsoup.parse(get(url, headers), url)
}

private val BLOCK_TAGS = setOf(
    "p", "div", "br", "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr", "blockquote", "section", "article", "pre"
)

/** Chuyển một phần tử HTML thành văn bản thuần, giữ ngắt đoạn. */
fun Element.toReadableText(): String {
    val el = clone()
    el.select("script, style, noscript, iframe, ins, button, form, [class*=ads], [id*=ads], .adsbygoogle").remove()
    val sb = StringBuilder()
    NodeTraversor.traverse(object : NodeVisitor {
        override fun head(node: Node, depth: Int) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> if (node.tagName() == "br") sb.append('\n')
                    else if (node.tagName() in BLOCK_TAGS) sb.append('\n')
            }
        }

        override fun tail(node: Node, depth: Int) {
            if (node is Element && node.tagName() in BLOCK_TAGS && node.tagName() != "br") sb.append('\n')
        }
    }, el)
    return normalizeText(sb.toString())
}

fun normalizeText(raw: String): String =
    raw.replace(' ', ' ')
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

fun String.absUrl(base: String): String = try {
    java.net.URL(java.net.URL(base), this).toString()
} catch (e: Exception) {
    this
}

fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
