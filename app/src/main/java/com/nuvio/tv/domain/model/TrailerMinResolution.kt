package com.nuvio.tv.domain.model

enum class TrailerMinResolution(val height: Int, val width: Int) {
    P720(720, 1280),
    P1080(1080, 1920);

    companion object {
        fun fromStorage(raw: String?): TrailerMinResolution {
            return entries.firstOrNull { it.name == raw } ?: P720
        }
    }
}
