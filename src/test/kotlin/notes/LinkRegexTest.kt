package notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinkRegexTest {
    @Test
    fun `link text accepts common file name characters`() {
        val reference = assertNotNull(NoteReferences.parse("[my file-2_test.kt:42](/tmp/note.kt)"))

        assertEquals("my file-2_test.kt", reference.label)
        assertEquals(42, reference.offset)
    }

    @Test
    fun `complete link preserves paths containing parentheses`() {
        val reference = assertNotNull(NoteReferences.parse("[Foo2.kt:7](/tmp/project (copy)/Foo2.kt)"))

        assertEquals("/tmp/project (copy)/Foo2.kt", reference.path)
    }

    @Test
    fun `link text rejects line breaks`() {
        assertNull(NoteReferences.parse("[bad\nname:3](/tmp/note.kt)"))
    }
}
