// navigation/Screen.kt
sealed class Screen {
    object Main : Screen()
    object Settings : Screen()
    data class NovelList(val source: Screen? = null) : Screen()
    data class EpisodeList(val ncode: String, val source: Screen? = null) : Screen()
    // sourceパラメータを追加
    data class EpisodeView(val ncode: String, val episodeNo: String, val source: Screen? = null) : Screen()
    data class WebView(val url: String, val source: Screen? = null) : Screen()
    object RecentlyReadNovels : Screen()
    object RecentlyUpdatedNovels : Screen()
    object UpdateInfo : Screen()
    object DatabaseSync : Screen()
}