package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStartupGateTest {

    @Test
    fun `placeholder catalog rows are not real rails`() {
        val row = catalogRow(id = "__placeholder_addon_movie_top_0")
        assertFalse(row.hasRealCatalogItems())
        assertFalse(
            HomeUiState(catalogRows = listOf(row), homeRows = listOf(HomeRow.Catalog(row)))
                .hasRealHomeRails()
        )
    }

    @Test
    fun `a catalog with real items is enough to mount home`() {
        val row = catalogRow(id = "tt123")
        assertTrue(row.hasRealCatalogItems())
        assertTrue(
            HomeUiState(catalogRows = listOf(row)).hasRealHomeRails()
        )
    }

    @Test
    fun `a collection hub counts as a real rail`() {
        assertTrue(
            HomeUiState(homeRows = listOf(HomeRow.CollectionRow(Collection(id = "c1", title = "Hub"))))
                .hasRealHomeRails()
        )
    }

    @Test
    fun `continue watching alone is not enough`() {
        assertFalse(HomeUiState().hasRealHomeRails())
    }

    private fun catalogRow(id: String) = CatalogRow(
        addonId = "addon",
        addonName = "Addon",
        addonBaseUrl = "https://example.com",
        catalogId = "top",
        catalogName = "Top",
        type = ContentType.MOVIE,
        items = listOf(
            MetaPreview(
                id = id,
                type = ContentType.MOVIE,
                name = "Title",
                poster = null,
                posterShape = com.nuvio.tv.domain.model.PosterShape.POSTER,
                background = null,
                logo = null,
                description = null,
                releaseInfo = null,
                imdbRating = null,
                genres = emptyList()
            )
        )
    )
}
