package notes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** The offset separator is always the last colon in the label. */
data class NoteReference(val label: String, val path: String, val offset: Int, val suffixStart: Int)

object NoteReferences {
    private val labelPattern = Regex("""^\[((?:\\.|[^\[\]\\\r\n])*):(\d+)]""")
    private val markdownPunctuation = "\\`*_{}[]<>()#+-.!|"

    fun format(label: String, path: String, offset: Int): String {
        require(offset >= 0)
        require(label.isNotEmpty() && '\n' !in label && '\r' !in label)
        val escaped = buildString {
            label.forEach { if (it in markdownPunctuation) append('\\'); append(it) }
        }
        val destination = buildString {
            path.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
                val value = byte.toInt() and 255
                val char = value.toChar()
                if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "/-._~") {
                    append(char)
                } else {
                    append('%').append(value.toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
        return "[$escaped:$offset](<$destination>)"
    }

    fun parse(text: String): NoteReference? {
        val labelMatch = labelPattern.find(text) ?: return null
        val offset = labelMatch.groupValues[2].toIntOrNull() ?: return null
        val label = unescape(labelMatch.groupValues[1])
        if (label.isEmpty()) return null
        val opening = labelMatch.range.last + 1
        if (opening >= text.length || text[opening] != '(' || !text.endsWith(')')) return null
        val raw = text.substring(opening + 1, text.length - 1)
        if (raw.isEmpty() || '\n' in raw || '\r' in raw) return null
        val encoded = raw.startsWith('<') && raw.endsWith('>')
        val destination = if (encoded) {
            raw.substring(1, raw.length - 1)
        } else {
            // Accept balanced parentheses in older generated destinations.
            var depth = 0
            var escaped = false
            for (char in raw) {
                if (escaped) { escaped = false; continue }
                if (char == '\\') { escaped = true; continue }
                if (char == '(') depth++
                if (char == ')' && --depth < 0) return null
            }
            if (depth != 0 || escaped) return null
            raw
        }
        val path = try {
            // New references use angle destinations. Legacy raw paths may contain literal percent signs.
            if (encoded) URLDecoder.decode(unescape(destination).replace("+", "%2B"), StandardCharsets.UTF_8)
            else unescape(destination)
        } catch (_: IllegalArgumentException) { return null }
        if (path.isEmpty()) return null
        val suffixStart = labelMatch.groups[2]!!.range.first - 1
        return NoteReference(label, path, offset, suffixStart)
    }

    private fun unescape(text: String) = text.replace(Regex("""\\([!"#$%&'()*+,\-./:;<=>?@\[\]\\^_`{|}~])"""), "$1")
}
