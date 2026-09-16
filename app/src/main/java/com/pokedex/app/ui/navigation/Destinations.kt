package com.pokedex.app.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val TEAM_EDITOR = "teamEditor/{teamId}"
    const val DETAIL = "detail/{idOrName}?banner={banner}"
    const val RESULT = "result/{payload}"

    fun teamEditor(teamId: Long): String = "teamEditor/$teamId"
    const val ARG_TEAM_ID = "teamId"

    fun detail(idOrName: String, banner: String? = null): String {
        val base = "detail/${Uri.encode(idOrName)}"
        return if (banner.isNullOrBlank()) base else "$base?banner=${Uri.encode(banner)}"
    }

    fun result(payload: String): String = "result/${Uri.encode(payload)}"

    const val ARG_ID_OR_NAME = "idOrName"
    const val ARG_BANNER = "banner"
    const val ARG_PAYLOAD = "payload"
}
