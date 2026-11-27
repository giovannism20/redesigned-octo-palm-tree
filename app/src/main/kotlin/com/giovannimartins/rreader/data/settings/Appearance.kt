package com.giovannimartins.rreader.data.settings

/** A choice saved in the settings under a [key] that never changes once released, whatever the code calls it. */
interface StoredChoice {
    val key: String
}

/** The entry saved as [key], or [default] when nothing was saved or the value is not one of these entries. */
internal fun <T : StoredChoice> List<T>.byKey(
    key: String?,
    default: T,
): T = firstOrNull { it.key == key } ?: default

/** How large the text of every screen is: a step on top of the phone's own font size. */
enum class TextSize(
    /** Multiplier applied on top of the phone's font scale. */
    val scale: Float,
    override val key: String,
) : StoredChoice {
    NORMAL(scale = 1f, key = "normal"),
    LARGE(scale = 1.15f, key = "large"),
    LARGER(scale = 1.3f, key = "larger"),
    LARGEST(scale = 1.5f, key = "largest"),
}

/** The app's colours: dark (the default), light, or whichever the phone uses. */
enum class ThemeChoice(
    override val key: String,
) : StoredChoice {
    /** Paper-coloured text on night blue: the app's original look. */
    DARK(key = "dark"),

    /** Ink text on paper. */
    LIGHT(key = "light"),

    /** Dark or light, following the phone's dark theme setting. */
    PHONE(key = "phone"),
}

/** The typeface of the follow-along reader's text; everything else always uses the app's typeface. */
enum class ReadingFont(
    override val key: String,
) : StoredChoice {
    /** Atkinson Hyperlegible Next, the app's typeface, made so similar letters are easy to tell apart. */
    ATKINSON(key = "atkinson"),

    /** Literata, a serif book face. */
    LITERATA(key = "literata"),
}

/** The listener's choices for how the app looks. */
data class Appearance(
    val textSize: TextSize = TextSize.NORMAL,
    val theme: ThemeChoice = ThemeChoice.DARK,
    val readingFont: ReadingFont = ReadingFont.ATKINSON,
)
