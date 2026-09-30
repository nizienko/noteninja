package notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NoteReferenceTest {
    @Test fun `common and sensitive filenames round trip`() {
        listOf("foo-bar.kt", "test2.kt", "my file.kt", "日本語.kt", "a[b](c):d.kt", "100%+file.kt", "a\\b.kt").forEach { name ->
            val path = "/project dir/$name"
            val text = NoteReferences.format(name, path, 123)
            val parsed = assertNotNull(NoteReferences.parse(text), text)
            assertEquals(name, parsed.label)
            assertEquals(path, parsed.path)
            assertEquals(123, parsed.offset)
            assertEquals(':', text[parsed.suffixStart])
            assertEquals("123]", text.substring(parsed.suffixStart + 1).substringBefore('('))
        }
    }

    @Test fun `legacy links and colon labels remain supported`() {
        val parsed = assertNotNull(NoteReferences.parse("[a:b.kt:42](/path/a(b):c.kt)"))
        assertEquals("a:b.kt", parsed.label)
        assertEquals("/path/a(b):c.kt", parsed.path)
        assertEquals(42, parsed.offset)
        assertNotNull(NoteReferences.parse("[foo.kt:0](/old/foo.kt)"))
        assertEquals("/old/100%20+file.kt", NoteReferences.parse("[foo.kt:0](/old/100%20+file.kt)")?.path)
    }

    @Test fun `malformed offsets and destinations are rejected`() {
        listOf("[foo:-1](/a)", "[foo:x](/a)", "[foo:2147483648](/a)", "[foo:1](/a))",
            "[foo:1](</a%xy>)", "[foo:1](/a", "[foo:1](/a) trailing", "[:1](/a)").forEach {
            assertNull(NoteReferences.parse(it), it)
        }
    }
}
