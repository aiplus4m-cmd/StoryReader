package net.topvl.storyreader.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.topvl.storyreader.source.Story
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(
    val story: Story,
    val chapterIndex: Int,
    val chapterTitle: String,
    /** Vị trí ký tự trong chương (độc lập với cỡ chữ). */
    val offset: Int,
    val snippet: String,
    val createdAt: Long,
) {
    val key get() = "${story.sourceId}|${story.url}|$chapterIndex|$offset"
}

data class ReadProgress(
    val story: Story,
    val chapterIndex: Int,
    val chapterTitle: String,
    /** Vị trí ký tự trong chương. */
    val offset: Int,
    val updatedAt: Long,
)

enum class ReaderTheme { LIGHT, SEPIA, DARK }

data class ReaderSettings(
    val fontSize: Int = 19,
    val lineHeight: Float = 1.5f,
    val theme: ReaderTheme = ReaderTheme.SEPIA,
    val serif: Boolean = true,
)

/** Lưu trữ cục bộ: yêu thích, bookmark, lịch sử đọc, cài đặt. */
class Library(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("library", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(loadStories(KEY_FAV))
    val favorites: StateFlow<List<Story>> = _favorites

    private val _bookmarks = MutableStateFlow(loadBookmarks())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<ReadProgress>> = _history

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ReaderSettings> = _settings

    private val _disabledSources = MutableStateFlow(prefs.getStringSet(KEY_DISABLED, emptySet())!!.toSet())
    val disabledSources: StateFlow<Set<String>> = _disabledSources

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER, false)
        set(v) = prefs.edit().putBoolean(KEY_DISCLAIMER, v).apply()

    // ---------- Favorites ----------
    fun isFavorite(story: Story) = _favorites.value.any { it.same(story) }

    fun toggleFavorite(story: Story) {
        val list = _favorites.value.toMutableList()
        val idx = list.indexOfFirst { it.same(story) }
        if (idx >= 0) list.removeAt(idx) else list.add(0, story)
        _favorites.value = list
        prefs.edit().putString(KEY_FAV, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    // ---------- Bookmarks ----------
    fun addBookmark(b: Bookmark) {
        val list = _bookmarks.value.filterNot { it.key == b.key }.toMutableList()
        list.add(0, b)
        saveBookmarks(list)
    }

    fun removeBookmark(b: Bookmark) = saveBookmarks(_bookmarks.value.filterNot { it.key == b.key })

    /** Bookmark nằm trong khoảng ký tự [start, end) của chương. */
    fun bookmarkIn(story: Story, chapterIndex: Int, start: Int, end: Int): Bookmark? =
        _bookmarks.value.firstOrNull {
            it.story.same(story) && it.chapterIndex == chapterIndex && it.offset in start until end
        }

    private fun saveBookmarks(list: List<Bookmark>) {
        _bookmarks.value = list
        val arr = JSONArray(list.map {
            JSONObject()
                .put("story", it.story.toJson())
                .put("ci", it.chapterIndex)
                .put("ct", it.chapterTitle)
                .put("off", it.offset)
                .put("snippet", it.snippet)
                .put("at", it.createdAt)
        })
        prefs.edit().putString(KEY_BM, arr.toString()).apply()
    }

    // ---------- History ----------
    fun progressOf(story: Story): ReadProgress? = _history.value.firstOrNull { it.story.same(story) }

    fun saveProgress(p: ReadProgress) {
        val list = _history.value.filterNot { it.story.same(p.story) }.toMutableList()
        list.add(0, p)
        val trimmed = list.take(200)
        _history.value = trimmed
        val arr = JSONArray(trimmed.map {
            JSONObject()
                .put("story", it.story.toJson())
                .put("ci", it.chapterIndex)
                .put("ct", it.chapterTitle)
                .put("off", it.offset)
                .put("at", it.updatedAt)
        })
        prefs.edit().putString(KEY_HIS, arr.toString()).apply()
    }

    fun removeHistory(story: Story) {
        val list = _history.value.filterNot { it.story.same(story) }
        _history.value = list
        prefs.edit().putString(KEY_HIS, JSONArray(list.map {
            JSONObject().put("story", it.story.toJson()).put("ci", it.chapterIndex)
                .put("ct", it.chapterTitle).put("off", it.offset).put("at", it.updatedAt)
        }).toString()).apply()
    }

    // ---------- Settings ----------
    fun updateSettings(s: ReaderSettings) {
        _settings.value = s
        prefs.edit()
            .putInt("fs", s.fontSize)
            .putFloat("lh", s.lineHeight)
            .putString("theme", s.theme.name)
            .putBoolean("serif", s.serif)
            .apply()
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        val set = _disabledSources.value.toMutableSet()
        if (enabled) set.remove(id) else set.add(id)
        _disabledSources.value = set
        prefs.edit().putStringSet(KEY_DISABLED, set).apply()
    }

    private fun loadSettings() = ReaderSettings(
        fontSize = prefs.getInt("fs", 19),
        lineHeight = prefs.getFloat("lh", 1.5f),
        theme = runCatching { ReaderTheme.valueOf(prefs.getString("theme", "SEPIA")!!) }.getOrDefault(ReaderTheme.SEPIA),
        serif = prefs.getBoolean("serif", true),
    )

    private fun loadStories(key: String): List<Story> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).toStory() }
    }.getOrDefault(emptyList())

    private fun loadBookmarks(): List<Bookmark> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_BM, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Bookmark(
                o.getJSONObject("story").toStory(), o.optInt("ci"), o.optString("ct"),
                o.optInt("off"), o.optString("snippet"), o.optLong("at"),
            )
        }
    }.getOrDefault(emptyList())

    private fun loadHistory(): List<ReadProgress> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_HIS, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ReadProgress(o.getJSONObject("story").toStory(), o.optInt("ci"), o.optString("ct"), o.optInt("off"), o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_FAV = "favorites"
        private const val KEY_BM = "bookmarks"
        private const val KEY_HIS = "history"
        private const val KEY_DISCLAIMER = "disclaimer_accepted"
        private const val KEY_DISABLED = "disabled_sources"

        @Volatile
        private var instance: Library? = null
        fun get(context: Context): Library =
            instance ?: synchronized(this) {
                instance ?: Library(context.applicationContext).also { instance = it }
            }
    }
}

fun Story.same(other: Story) = sourceId == other.sourceId && url == other.url

fun Story.toJson(): JSONObject = JSONObject()
    .put("src", sourceId).put("url", url).put("title", title)
    .put("author", author).put("cover", cover).put("desc", description)

fun JSONObject.toStory() = Story(
    sourceId = optString("src"), url = optString("url"), title = optString("title"),
    author = optString("author"), cover = optString("cover"), description = optString("desc"),
)
