package com.nuvio.tv.core.deeplink

import com.nuvio.tv.domain.deeplink.AppDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {
    @Test
    fun parsesMetaQueryDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("nuvio://meta?type=series&id=tt0944947")
        )
    }

    @Test
    fun parsesAddonInstallDeepLink() {
        assertEquals(
            AppDeepLink.AddonInstall("https://free.nebulapro.xyz/sports/i/free/manifest.json"),
            DeepLinkParser.parse("nuvio://free.nebulapro.xyz/sports/i/free/manifest.json")
        )
    }

    @Test
    fun parsesStremioAddonInstallDeepLink() {
        assertEquals(
            AppDeepLink.AddonInstall("https://free.nebulapro.xyz/sports/i/free/manifest.json"),
            DeepLinkParser.parse("stremio://free.nebulapro.xyz/sports/i/free/manifest.json")
        )
    }

    @Test
    fun parsesDirectImdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("nuvio://series/tt0944947")
        )
    }

    @Test
    fun parsesProviderImdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("nuvio://imdb/series/tt0944947")
        )
    }

    @Test
    fun parsesProviderTmdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tmdb:1399"),
            DeepLinkParser.parse("nuvio://tmdb/tv/1399")
        )
    }

    @Test
    fun doesNotTreatAuthLinkAsAddonInstall() {
        assertNull(DeepLinkParser.parse("nuvio://auth/trakt?code=abc"))
    }

    @Test
    fun doesNotTreatNonHostStremioLinkAsAddonInstall() {
        assertNull(DeepLinkParser.parse("stremio://detail/series/tt0944947"))
    }

    @Test
    fun parsesViewPackInstallDeepLink() {
        val packUrl = "https://jsonblob.com/api/jsonBlob/abc123"
        assertEquals(
            AppDeepLink.ViewPackInstall(packUrl),
            DeepLinkParser.parse(
                "nuvio://viewpack?url=${java.net.URLEncoder.encode(packUrl, "UTF-8")}"
            )
        )
    }

    @Test
    fun parsesViewPackInstallAliasHost() {
        val packUrl = "https://litter.catbox.moe/example.view.json"
        assertEquals(
            AppDeepLink.ViewPackInstall(packUrl),
            DeepLinkParser.parse(
                "nuvio://view-pack?url=${java.net.URLEncoder.encode(packUrl, "UTF-8")}"
            )
        )
    }

    @Test
    fun rejectsViewPackInstallWithHttpUrl() {
        assertNull(
            DeepLinkParser.parse(
                "nuvio://viewpack?url=${java.net.URLEncoder.encode("http://192.168.0.1/pack.json", "UTF-8")}"
            )
        )
    }

    @Test
    fun rejectsViewPackInstallWithoutUrl() {
        assertNull(DeepLinkParser.parse("nuvio://viewpack"))
    }
}
