package net.topvl.storyreader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.topvl.storyreader.BuildConfig
import net.topvl.storyreader.R
import net.topvl.storyreader.source.Sources

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.logo_full),
            contentDescription = "Logo $DEV_NAME",
            modifier = Modifier.fillMaxWidth(0.8f).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Text("StoryReader", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("Phiên bản ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Nhà phát triển", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Dev: $DEV_NAME")
                Text(
                    "Web: $DEV_WEB",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { openUrl(context, DEV_WEB) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Nguồn truyện", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Nội dung được tải trực tiếp từ các website dưới đây. Mỗi truyện và chương đều ghi rõ nguồn gốc.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                Sources.all.forEach { s ->
                    Text(
                        "• ${s.name} (${s.language}) — ${s.homepage}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 3.dp).clickable { openUrl(context, s.homepage) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Miễn trừ trách nhiệm", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(DISCLAIMER_TEXT, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} $DEV_NAME",
            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}
