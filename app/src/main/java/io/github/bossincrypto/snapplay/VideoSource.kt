package io.github.bossincrypto.snapplay

import java.net.URI

internal fun parseVideoUrl(value: String): String? = runCatching {
    val normalized = value.trim()
    val scheme = URI(normalized).scheme
    normalized.takeIf { scheme == "https" || scheme == "http" }
}.getOrNull()
