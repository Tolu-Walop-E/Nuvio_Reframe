package com.nuvio.tv.domain.model

enum class TrailerMinResolution(
    val height: Int,
    val width: Int,
    val capHeight: Int = 1080,
    val capWidth: Int = 1920
) {
    P720(720, 1280, 1080, 1920),
    P1080(1080, 1920, 1080, 1920);

    companion object {
        fun fromStorage(raw: String?): TrailerMinResolution {
            return entries.firstOrNull { it.name == raw } ?: P720
        }
    }
}
