package com.nuvio.tv.core.viewpack

/**
 * Studio `.view.json` schema (schemaVersion = 1).
 * Pack blocks describe Netflix-style home chrome; TV currently applies
 * lock/rotate + reorder/filter of existing home catalog/collection rails.
 */
data class ViewPack(
    val schemaVersion: Int = 1,
    val id: String = "untitled",
    val name: String = "Untitled home",
    val description: String? = null,
    val canvas: ViewPackCanvas = ViewPackCanvas(),
    val blocks: List<ViewBlock> = emptyList(),
    /**
     * Global Netflix-style focused poster info under catalog/collection rails.
     * When true, TV shows title/facts/synopsis for the focused catalog item.
     */
    val showFocusedPosterInfo: Boolean = false,
    /** Global catalog/media poster scale (1 = Netflix default). */
    val catalogPosterScale: Float = 1f,
    /** Global collection hub landscape tile scale (1 = Netflix default). */
    val collectionLandscapeScale: Float = 1f,
    /** Global collection rail title text scale (1 = Netflix default 26sp). */
    val collectionTitleScale: Float = 1f,
    /**
     * When true, collection folders open in this pack's Netflix / Reframe
     * presentation instead of the collection's own tabbed grid. Per-block
     * [ViewBlock.collectionOpenStyle] still overrides for that collection.
     */
    val collectionsOpenInReframe: Boolean = false,
    val rotateUnlocked: Boolean = false,
    val rotateIntervalHours: Int = MIN_ROTATE_INTERVAL_HOURS,
    val lastShuffleAt: Long? = null,
    val shuffleSeed: String? = null,
    /** Nested Movies layout from Studio `screens.movies`. */
    val moviesScreen: ViewPack? = null,
    /** Nested TV Shows layout from Studio `screens.shows`. */
    val showsScreen: ViewPack? = null
)

data class ViewPackCanvas(
    val width: Int = 1920,
    val height: Int = 1080
)

data class ViewBlock(
    val id: String,
    val type: String,
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 1920,
    val h: Int = 210,
    val dataSource: String = "none",
    val trailer: Boolean = false,
    val label: String? = null,
    val hAlign: String? = null,
    val contentAlign: String? = null,
    val posterGrow: Boolean? = null,
    /** Show title under posters; null = leave TV preference / layout default. */
    val showPosterLabels: Boolean? = null,
    /** Explicit lock; null = type/dataSource defaults. */
    val locked: Boolean? = null,
    /**
     * Collection rails only: how folders opened from this rail should render.
     * One of [OPEN_STYLE_REFRAME] / [OPEN_STYLE_GRID] / [OPEN_STYLE_ROWS].
     * null = keep the collection's own view mode.
     */
    val collectionOpenStyle: String? = null
)

/** Folder opens in the Netflix-style home layout (Nuvio's FOLLOW_LAYOUT). */
const val OPEN_STYLE_REFRAME = "reframe"

/** Folder opens in the tabbed grid (Nuvio's TABBED_GRID). */
const val OPEN_STYLE_GRID = "grid"

/** Folder opens as one horizontal rail per source (Nuvio's ROWS). */
const val OPEN_STYLE_ROWS = "rows"

const val MIN_ROTATE_INTERVAL_HOURS = 12

/**
 * Rotation bookkeeping, held outside the pack document.
 *
 * The pack JSON is the synced artefact and account sync compares it verbatim, so
 * writing rotation results back into it made every rotated device look like it had
 * a different pack and get reverted on the next pull.
 */
data class ViewPackRotationState(
    val seed: String? = null,
    val lastShuffleAt: Long? = null
)

data class ViewPackRotationResult(
    val blocks: List<ViewBlock>,
    val state: ViewPackRotationState,
    val didShuffle: Boolean
)
