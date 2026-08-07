package com.nuvio.tv.core.viewpack

import com.google.gson.Gson
import com.google.gson.JsonParser

private val gson = Gson()

/**
 * Parse Studio export JSON. Throws [IllegalArgumentException] on bad schema.
 */
fun parseViewPackJson(raw: String): ViewPack {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) throw IllegalArgumentException("Empty view pack JSON")
    val root = JsonParser.parseString(trimmed).asJsonObject
    val schemaVersion = root.get("schemaVersion")?.asInt
        ?: throw IllegalArgumentException("Missing schemaVersion")
    if (schemaVersion != 1) {
        throw IllegalArgumentException("Unsupported schemaVersion: $schemaVersion")
    }
    if (!root.has("blocks") || !root.get("blocks").isJsonArray) {
        throw IllegalArgumentException("Pack missing blocks[]")
    }
    val parsed = gson.fromJson(trimmed, ViewPackDto::class.java)
        ?: throw IllegalArgumentException("Invalid pack JSON")
    val hours = parsed.rotateIntervalHours
        ?.takeIf { it > 0 }
        ?.let { normalizeRotateIntervalHours(it) }
        ?: MIN_ROTATE_INTERVAL_HOURS
    return ViewPack(
        schemaVersion = 1,
        id = parsed.id?.trim()?.takeIf { it.isNotEmpty() } ?: "imported",
        name = parsed.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Imported view",
        description = parsed.description?.trim()?.takeIf { it.isNotEmpty() },
        canvas = ViewPackCanvas(
            width = parsed.canvas?.width ?: 1920,
            height = parsed.canvas?.height ?: 1080
        ),
        blocks = parsed.blocks.orEmpty().mapNotNull { dto ->
            val id = dto.id?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            ViewBlock(
                id = id,
                type = dto.type?.trim().orEmpty().ifEmpty { "mediaRail" },
                x = dto.x ?: 0,
                y = dto.y ?: 0,
                w = dto.w ?: 1920,
                h = dto.h ?: 210,
                dataSource = dto.dataSource?.trim().orEmpty().ifEmpty { "none" },
                trailer = dto.trailer == true,
                label = dto.label,
                hAlign = dto.hAlign,
                contentAlign = dto.contentAlign,
                posterGrow = dto.posterGrow,
                showPosterLabels = dto.showPosterLabels,
                locked = dto.locked,
                collectionOpenStyle = dto.collectionOpenStyle
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf {
                        it == OPEN_STYLE_REFRAME || it == OPEN_STYLE_GRID || it == OPEN_STYLE_ROWS
                    }
            )
        },
        showFocusedPosterInfo = parsed.showFocusedPosterInfo == true ||
            parsed.blocks.orEmpty().any { it.showPosterLabels == true },
        catalogPosterScale = normalizePackCardScale(parsed.catalogPosterScale),
        collectionLandscapeScale = normalizePackCardScale(parsed.collectionLandscapeScale),
        collectionsOpenInReframe = parsed.collectionsOpenInReframe == true,
        rotateUnlocked = parsed.rotateUnlocked == true,
        rotateIntervalHours = hours,
        lastShuffleAt = parsed.lastShuffleAt,
        shuffleSeed = parsed.shuffleSeed?.trim()?.takeIf { it.isNotEmpty() }
    )
}

fun serializeViewPackJson(pack: ViewPack): String {
    return gson.toJson(
        ViewPackDto(
            schemaVersion = 1,
            id = pack.id,
            name = pack.name,
            description = pack.description,
            canvas = CanvasDto(pack.canvas.width, pack.canvas.height),
            blocks = pack.blocks.map { block ->
                BlockDto(
                    id = block.id,
                    type = block.type,
                    x = block.x,
                    y = block.y,
                    w = block.w,
                    h = block.h,
                    dataSource = block.dataSource,
                    trailer = block.trailer,
                    label = block.label,
                    hAlign = block.hAlign,
                    contentAlign = block.contentAlign,
                    posterGrow = block.posterGrow,
                    showPosterLabels = block.showPosterLabels,
                    locked = block.locked,
                    collectionOpenStyle = block.collectionOpenStyle
                )
            },
            showFocusedPosterInfo = pack.showFocusedPosterInfo,
            catalogPosterScale = pack.catalogPosterScale,
            collectionLandscapeScale = pack.collectionLandscapeScale,
            collectionsOpenInReframe = pack.collectionsOpenInReframe,
            rotateUnlocked = pack.rotateUnlocked,
            rotateIntervalHours = pack.rotateIntervalHours,
            lastShuffleAt = pack.lastShuffleAt,
            shuffleSeed = pack.shuffleSeed
        )
    )
}

private data class ViewPackDto(
    val schemaVersion: Int? = null,
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val canvas: CanvasDto? = null,
    val blocks: List<BlockDto>? = null,
    val showFocusedPosterInfo: Boolean? = null,
    val catalogPosterScale: Float? = null,
    val collectionLandscapeScale: Float? = null,
    val collectionsOpenInReframe: Boolean? = null,
    val rotateUnlocked: Boolean? = null,
    val rotateIntervalHours: Int? = null,
    val lastShuffleAt: Long? = null,
    val shuffleSeed: String? = null
)

private data class CanvasDto(
    val width: Int? = null,
    val height: Int? = null
)

private data class BlockDto(
    val id: String? = null,
    val type: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val w: Int? = null,
    val h: Int? = null,
    val dataSource: String? = null,
    val trailer: Boolean? = null,
    val label: String? = null,
    val hAlign: String? = null,
    val contentAlign: String? = null,
    val posterGrow: Boolean? = null,
    val showPosterLabels: Boolean? = null,
    val locked: Boolean? = null,
    val collectionOpenStyle: String? = null
)
