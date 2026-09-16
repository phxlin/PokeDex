package com.pokedex.app.core

/**
 * PokeAPI flavor text is wrapped for the original handheld screens: it contains
 * hard newlines, form-feed (U+000C) characters between sentences, non-breaking
 * spaces and soft hyphens. Collapse all of that into one clean paragraph.
 */
fun cleanFlavorText(raw: String): String =
    raw.replace('', ' ')   // form feed
        .replace('­', ' ')  // soft hyphen
        .replace(' ', ' ')  // non-breaking space
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
