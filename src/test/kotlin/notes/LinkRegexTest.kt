package notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinkRegexTest {
    @Test
    fun `link text accepts common file name characters`() {
        val match = assertNotNull(linkTextRegex.matchEntire("[my file-2_test.kt:42]"))

        assertEquals("my file-2_test.kt", match.groupValues[1])
        assertEquals("42", match.groupValues[2])
    }

    @Test
    fun `complete link preserves paths containing parentheses`() {
        val match = assertNotNull(linkRegex.matchEntire("[Foo2.kt:7](/tmp/project (copy)/Foo2.kt)"))

        assertEquals("/tmp/project (copy)/Foo2.kt", match.groupValues[3])
    }

    @Test
    fun `link text rejects line breaks`() {
        assertNull(linkTextRegex.matchEntire("[bad\nname:3]"))
    }
}
