package net.topvl.storyreader.source

import android.webkit.CookieManager

/**
 * Phiên đăng nhập Wattpad dùng chung với WebView đăng nhập trong app.
 * Khi chưa đăng nhập, Wattpad ẩn các truyện gắn nhãn Trưởng thành (Mature) khỏi tìm kiếm.
 */
object WattpadAuth {
    const val LOGIN_URL = "https://www.wattpad.com/login"
    private const val SITE = "https://www.wattpad.com"

    /** Cookie Wattpad hiện có (null khi chạy ngoài Android, ví dụ unit test). */
    fun cookies(): String? = runCatching { CookieManager.getInstance()?.getCookie(SITE) }.getOrNull()

    fun isLoggedIn(): Boolean = cookies()?.split(';')?.any { it.trim().startsWith("token=") } == true

    fun logout() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    fun appliesTo(host: String) = host == "wattpad.com" || host.endsWith(".wattpad.com")
}
