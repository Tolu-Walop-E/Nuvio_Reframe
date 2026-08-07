package com.nuvio.tv.domain.deeplink

sealed interface AppDeepLink {
    data class Meta(
        val type: String,
        val id: String
    ) : AppDeepLink

    data class AddonInstall(
        val manifestUrl: String
    ) : AppDeepLink

    /** Studio view pack install — [packUrl] is an http(s) URL to `.view.json`. */
    data class ViewPackInstall(
        val packUrl: String
    ) : AppDeepLink
}
