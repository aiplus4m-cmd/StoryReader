package net.topvl.storyreader.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.topvl.storyreader.source.Http
import net.topvl.storyreader.source.WattpadAuth

/** Đăng nhập Wattpad bằng trang đăng nhập chính thức; app chỉ dùng cookie phiên, không lưu mật khẩu. */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WattpadLoginScreen(onDone: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(false) }
    val finish = { if (!done) { done = true; onDone() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đăng nhập Wattpad") },
                navigationIcon = { IconButton(onClick = finish) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Đăng nhập trực tiếp trên trang Wattpad. Ứng dụng không lưu mật khẩu, chỉ dùng phiên đăng nhập " +
                    "để tìm và đọc truyện như trên web (kể cả truyện gắn nhãn Trưởng thành nếu tài khoản cho phép). " +
                    "Nên đăng nhập bằng email/tên người dùng – Google không cho đăng nhập trong ứng dụng nhúng.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = Http.USER_AGENT
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                CookieManager.getInstance().flush()
                                // Đã có cookie phiên và đã rời trang đăng nhập -> xong
                                if (WattpadAuth.isLoggedIn() && url != null && !url.contains("/login")) finish()
                            }
                        }
                        loadUrl(WattpadAuth.LOGIN_URL)
                    }
                },
            )
        }
    }
}
