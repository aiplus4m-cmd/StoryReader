package net.topvl.storyreader.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import net.topvl.storyreader.source.Sources
import net.topvl.storyreader.source.Story

const val DEV_NAME = "NhảmStudio"
const val DEV_WEB = "https://topvl.net"

const val DISCLAIMER_TEXT =
    "StoryReader là ứng dụng đọc truyện miễn phí, hoạt động như một trình duyệt chuyên dụng: " +
        "ứng dụng KHÔNG lưu trữ, sở hữu hay phát hành bất kỳ nội dung truyện nào trên máy chủ riêng.\n\n" +
        "• Toàn bộ nội dung (tên truyện, ảnh bìa, mô tả, nội dung chương) được tải trực tiếp từ các website nguồn " +
        "công khai và luôn được ghi rõ nguồn gốc trong ứng dụng.\n" +
        "• Bản quyền nội dung thuộc về tác giả, dịch giả và website nguồn tương ứng. Hãy ủng hộ tác giả và website nguồn.\n" +
        "• $DEV_NAME không chịu trách nhiệm về tính chính xác, hợp pháp hay bản quyền của nội dung do các nguồn cung cấp, " +
        "cũng như mọi thiệt hại phát sinh từ việc sử dụng nội dung đó.\n" +
        "• StoryReader không liên kết, không được tài trợ hay xác nhận bởi bất kỳ website nguồn nào. " +
        "Tên và nhãn hiệu thuộc về chủ sở hữu tương ứng.\n" +
        "• Nếu bạn là chủ sở hữu nội dung và muốn gỡ bỏ một nguồn khỏi ứng dụng, vui lòng liên hệ qua $DEV_WEB.\n" +
        "• Người dùng tự chịu trách nhiệm về việc sử dụng nội dung theo quy định pháp luật nơi mình sinh sống."

fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
fun DisclaimerDialog(onAccept: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Miễn trừ trách nhiệm") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(DISCLAIMER_TEXT, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("Nguồn truyện:", fontWeight = FontWeight.Bold)
                Sources.all.forEach { Text("• ${it.name} – ${it.homepage}", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("Tôi đồng ý") } },
        dismissButton = { TextButton(onClick = { (context as? Activity)?.finish() }) { Text("Thoát") } },
    )
}

@Composable
fun SourceBadge(sourceId: String, modifier: Modifier = Modifier) {
    val name = Sources.byId(sourceId)?.name ?: sourceId
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun Cover(url: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.MenuBook, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun StoryRow(
    story: Story,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(story.cover, Modifier.width(56.dp).height(80.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(story.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (story.author.isNotBlank()) {
                Text(story.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            SourceBadge(story.sourceId)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
