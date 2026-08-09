package com.nuvio.tv.core.viewpack

import kotlin.math.max
import kotlin.random.Random

private const val RAIL_GAP = 44

/** Deterministic PRNG from a string seed (mulberry32, same as Studio). */
fun hashSeed(seed: String): () -> Double {
    var h = 2166136261u
    for (ch in seed) {
        h = h xor ch.code.toUInt()
        h *= 16777619u
    }
    var t = h
    return {
        t = t + 0x6d2b79f5u
        var r = (t xor (t shr 15)) * (1u or t)
        r = r xor (r + ((r xor (r shr 7)) * (61u or r)))
        ((r xor (r shr 14)).toDouble()) / 4294967296.0
    }
}

internal fun defaultLocked(block: ViewBlock): Boolean {
    if (block.locked == true) return true
    if (block.locked == false) return false
    return block.type == "topNav" ||
        block.type == "hero" ||
        block.dataSource == "continueWatching"
}

private fun <T> fisherYates(items: List<T>, rand: () -> Double): List<T> {
    val next = items.toMutableList()
    for (i in next.lastIndex downTo 1) {
        val j = (rand() * (i + 1)).toInt().coerceIn(0, i)
        val tmp = next[i]
        next[i] = next[j]
        next[j] = tmp
    }
    return next
}

/** Keep horizontal clamp simple; restack Y with a consistent gap. */
fun restackVertically(blocks: List<ViewBlock>, gap: Int = RAIL_GAP): List<ViewBlock> {
    if (blocks.isEmpty()) return emptyList()
    val next = blocks.map { it.copy() }.toMutableList()
    val sorted = next.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))
    for (i in 1 until sorted.size) {
        val cur = sorted[i]
        var minY = cur.y
        for (j in 0 until i) {
            val prev = sorted[j]
            val overlapsX = cur.x < prev.x + prev.w && cur.x + cur.w > prev.x
            if (!overlapsX) continue
            minY = max(minY, prev.y + prev.h + gap)
        }
        if (minY != cur.y) {
            val idx = next.indexOfFirst { it.id == cur.id }
            if (idx >= 0) next[idx] = next[idx].copy(y = minY)
        }
    }
    return next
}

/**
 * Lock-slot shuffle: sort by Y into slots; locked blocks keep slot indices;
 * unlocked blocks permute into remaining slots.
 */
fun shuffleUnlockedBlocks(blocks: List<ViewBlock>, seed: String): List<ViewBlock> {
    if (blocks.isEmpty()) return emptyList()
    val ordered = blocks.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))
    val lockedFlags = ordered.map(::defaultLocked)
    val unlocked = ordered.filterIndexed { i, _ -> !lockedFlags[i] }
    if (unlocked.size <= 1) return restackVertically(blocks)

    val shuffledUnlocked = fisherYates(unlocked, hashSeed(seed))
    var u = 0
    val bySlot = ordered.mapIndexed { i, block ->
        if (lockedFlags[i]) block else shuffledUnlocked[u++]
    }
    val remapped = bySlot.mapIndexed { i, block ->
        val slot = ordered[i]
        block.copy(x = slot.x, y = slot.y, w = slot.w, h = slot.h)
    }
    return restackVertically(remapped)
}

fun normalizeRotateIntervalHours(value: Int?): Int {
    val n = value ?: MIN_ROTATE_INTERVAL_HOURS
    return max(MIN_ROTATE_INTERVAL_HOURS, n)
}

fun newShuffleSeed(nowMs: Long = System.currentTimeMillis()): String {
    return "${nowMs.toString(36)}-${Random.nextLong().toString(36).takeLast(8)}"
}

/**
 * Derive the rail order for [pack] from [state], rolling a new seed when the
 * rotation interval has elapsed (or when [force] is set).
 *
 * The pack itself is never modified: callers render [ViewPackRotationResult.blocks]
 * and persist [ViewPackRotationResult.state] separately, which keeps the synced
 * pack document byte-identical across devices.
 */
fun rotateUnlockedBlocks(
    pack: ViewPack,
    state: ViewPackRotationState,
    nowMs: Long = System.currentTimeMillis(),
    force: Boolean = false
): ViewPackRotationResult {
    if (!pack.rotateUnlocked) {
        return ViewPackRotationResult(blocks = pack.blocks, state = state, didShuffle = false)
    }

    val intervalMs = normalizeRotateIntervalHours(pack.rotateIntervalHours) * 60L * 60L * 1000L
    val last = state.lastShuffleAt ?: 0L
    val due = force || last <= 0L || nowMs - last >= intervalMs

    if (!due) {
        val seed = state.seed ?: newShuffleSeed(nowMs)
        return ViewPackRotationResult(
            blocks = shuffleUnlockedBlocks(pack.blocks, seed),
            state = state.copy(seed = seed),
            didShuffle = false
        )
    }

    val seed = newShuffleSeed(nowMs)
    return ViewPackRotationResult(
        blocks = shuffleUnlockedBlocks(pack.blocks, seed),
        state = ViewPackRotationState(seed = seed, lastShuffleAt = nowMs),
        didShuffle = true
    )
}
