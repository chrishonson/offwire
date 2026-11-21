package com.chrishonson.gemmavoice.voice

// Markdown emphasis and heading marks would be read aloud as symbols.
internal fun speakableText(text: String): String =
    text.filterNot { it == '*' || it == '#' || it == '`' }.trim()
