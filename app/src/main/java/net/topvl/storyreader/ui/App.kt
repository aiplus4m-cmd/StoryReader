package net.topvl.storyreader.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.topvl.storyreader.data.Library
import net.topvl.storyreader.data.toJson
import net.topvl.storyreader.data.toStory
import net.topvl.storyreader.source.Story
import org.json.JSONObject

private enum class Tab(val label: String, val icon: ImageVector) {
    SEARCH("Tìm kiếm", Icons.Filled.Search),
    LIBRARY("Tủ truyện", Icons.Filled.Favorite),
    BOOKMARKS("Bookmark", Icons.Filled.Bookmarks),
    ABOUT("Giới thiệu", Icons.Filled.Info),
}

fun NavHostController.openStory(story: Story) {
    navigate("detail?story=${Uri.encode(story.toJson().toString())}")
}

fun NavHostController.openReader(story: Story, chapter: Int, offset: Int = 0) {
    navigate("reader?story=${Uri.encode(story.toJson().toString())}&ch=$chapter&off=$offset")
}

@Composable
fun StoryReaderApp() {
    StoryReaderTheme {
        val context = LocalContext.current
        val library = remember { Library.get(context) }
        var accepted by remember { mutableStateOf(library.disclaimerAccepted) }
        val nav = rememberNavController()

        NavHost(navController = nav, startDestination = "main") {
            composable("main") {
                var tab by rememberSaveable { mutableStateOf(Tab.SEARCH) }
                val searchVm: SearchViewModel = viewModel()
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                ) { padding ->
                    val mod = Modifier.padding(bottom = padding.calculateBottomPadding())
                    when (tab) {
                        Tab.SEARCH -> SearchScreen(searchVm, library, mod) { nav.openStory(it) }
                        Tab.LIBRARY -> LibraryScreen(library, mod,
                            onOpen = { nav.openStory(it) },
                            onContinue = { p -> nav.openReader(p.story, p.chapterIndex, p.offset) })
                        Tab.BOOKMARKS -> BookmarksScreen(library, mod) { b ->
                            nav.openReader(b.story, b.chapterIndex, b.offset)
                        }
                        Tab.ABOUT -> AboutScreen(mod)
                    }
                }
            }
            composable(
                "detail?story={story}",
                arguments = listOf(navArgument("story") { type = NavType.StringType }),
            ) { entry ->
                val story = JSONObject(entry.arguments?.getString("story") ?: "{}").toStory()
                DetailScreen(
                    story = story,
                    library = library,
                    onBack = { nav.popBackStack() },
                    onRead = { ch, off -> nav.openReader(story, ch, off) },
                )
            }
            composable(
                "reader?story={story}&ch={ch}&off={off}",
                arguments = listOf(
                    navArgument("story") { type = NavType.StringType },
                    navArgument("ch") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("off") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { entry ->
                val args = entry.arguments
                val story = JSONObject(args?.getString("story") ?: "{}").toStory()
                ReaderScreen(
                    story = story,
                    startChapter = args?.getInt("ch") ?: 0,
                    startOffset = args?.getInt("off") ?: 0,
                    library = library,
                    onBack = { nav.popBackStack() },
                )
            }
        }

        if (!accepted) {
            DisclaimerDialog(onAccept = {
                library.disclaimerAccepted = true
                accepted = true
            })
        }
    }
}
