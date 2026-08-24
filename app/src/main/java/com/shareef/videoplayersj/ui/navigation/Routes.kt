package com.shareef.videoplayersj.ui.navigation

object Routes {
    const val LIBRARY = "library"
    const val SHOW_DETAIL = "show/{showId}"
    const val PLAYER = "player/{videoId}"

    fun showDetail(showId: Long) = "show/$showId"
    fun player(videoId: Long) = "player/$videoId"
}
