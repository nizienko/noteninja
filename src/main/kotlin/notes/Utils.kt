package notes

import java.awt.Color
import java.util.*

fun Color.toHex(): String {
    val hex = String.format("#%02x%02x%02x", red, green, blue)
    return hex.uppercase(Locale.getDefault())
}

fun String.parseColor(): Color = Color.decode(this)
