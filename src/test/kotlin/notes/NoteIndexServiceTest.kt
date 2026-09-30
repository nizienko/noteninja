package notes

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll

class NoteIndexServiceTest {
    @Test fun `failed file removal preserves registration and searchable content`() = runBlocking {
        val index = NoteIndexService()
        val note = NoteCard("Note", "/notes/first.notes")
        val registry = FilesState().apply { addFile(note) }
        index.replace(note, "bravo")
        val deleteFile: () -> Unit = { throw IllegalStateException("The file could not be deleted") }
        assertFailsWith<IllegalStateException> {
            index.remove(note) {
                deleteFile()
                registry.removeFile(note)
            }
        }
        assertEquals(note, registry.list().single())
        assertEquals(note, index.search("bravo").single().note)
        index.remove(note) { registry.removeFile(note) }
        assertTrue(registry.list().isEmpty())
        assertTrue(index.search("bravo").isEmpty())
        assertTrue(index.autocomplete("brav").isEmpty())
    }

    @Test fun `refresh observes disk edits at an already indexed path`() {
        val file = createTempDirectory("note-refresh").resolve("note.notes").toFile()
        val note = NoteCard("Note", file.path)
        val index = NoteIndexService()
        file.writeText("bravo")
        index.buildIndex(setOf(note))
        val id = index.search("bravo").single().searchResult.documentId
        file.writeText("delta")
        index.buildIndex(setOf(note))
        assertTrue(index.search("bravo").isEmpty())
        assertTrue(index.autocomplete("brav").isEmpty())
        assertEquals(id, index.search("delta").single().searchResult.documentId)
    }

    @Test fun `normalized paths reuse IDs and concurrent readers observe complete refreshes`() = runBlocking {
        withTimeout(10000) {
            val service = NoteIndexService()
            val note = NoteCard("Note", "/notes/first.notes")
            val alias = NoteCard("Alias", "/notes/sub/../first.notes")
            service.replace(note, "bravo")
            val id = service.search("bravo").single().searchResult.documentId
            service.replace(alias, "delta")
            assertTrue(service.search("bravo").isEmpty())
            assertEquals(id, service.search("delta").single().searchResult.documentId)
            val writers = (0..1).map { project ->
                async(Dispatchers.Default) {
                    repeat(40) {
                        service.refresh({ setOf(note) }) { if (project == 0) "bravo" else "delta" }
                    }
                }
            }
            val readers = (0..1).map {
                async(Dispatchers.Default) {
                    repeat(200) {
                        service.search("bravo").forEach { result -> assertEquals(note.path, result.note.path) }
                        service.search("delta").forEach { result -> assertEquals(id, result.searchResult.documentId) }
                        service.autocomplete("br")
                    }
                }
            }
            (writers + readers).awaitAll()
            service.remove(note) {}
            assertTrue(service.search("bravo").isEmpty())
            assertTrue(service.search("delta").isEmpty())
        }
    }

    @Test fun `incremental replacements remove every old token and deletion preserves other notes`() {
        val service = NoteIndexService()
        val note = NoteCard("First", "/notes/first.notes")
        val other = NoteCard("Other", "/notes/other.notes")
        service.replace(other, "zebra")
        service.replace(note, "bravo")
        val id = service.search("bravo").single().searchResult.documentId
        listOf("delta", "echo", "foxtrot").forEach { word ->
            service.replace(note, word)
            assertTrue(service.search("bravo").isEmpty())
            assertEquals(id, service.search(word).single().searchResult.documentId)
        }
        assertTrue(service.search("delta").isEmpty())
        assertTrue(service.search("echo").isEmpty())
        service.removeSnapshot(note)
        assertTrue(service.search("foxtrot").isEmpty())
        assertEquals(other, service.search("zebra").single().note)
    }

    @Test fun `colliding path hashes have distinct document IDs`() {
        val service = NoteIndexService()
        val first = NoteCard("Aa", "/notes/Aa.notes")
        val second = NoteCard("BB", "/notes/BB.notes")
        assertEquals(first.hashCode(), second.hashCode())
        service.replace(first, "bravo")
        service.replace(second, "delta")
        assertEquals(first.path, service.search("bravo").single().note.path)
        assertEquals(second.path, service.search("delta").single().note.path)
        service.replace(first, "echo")
        service.removeSnapshot(first)
        assertEquals(second.path, service.search("delta").single().note.path)
        assertTrue(service.search("echo").isEmpty())
    }

    @Test fun `shared refreshes read after serialization and publish complete snapshots`() = runBlocking {
        withTimeout(10000) {
            val service = NoteIndexService()
            val note = NoteCard("First", "/notes/first.notes")
            service.replace(note, "bravo")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val first = async {
                service.refresh({ setOf(note) }) {
                    entered.complete(Unit)
                    release.await()
                    "delta"
                }
            }
            entered.await()
            val second = async { service.refresh({ setOf(note) }) { "echo" } }
            assertEquals(note, service.search("bravo").single().note)
            assertTrue(service.search("delta").isEmpty())
            release.complete(Unit)
            first.await()
            second.await()
            assertTrue(service.search("bravo").isEmpty())
            assertTrue(service.search("delta").isEmpty())
            assertEquals(note, service.search("echo").single().note)
            service.refresh({ emptySet() }) { error("No notes should be read") }
            assertTrue(service.search("echo").isEmpty())
        }
    }

    @Test
    fun `buildIndex refreshes results when note set changes`() {
        val dir = createTempDirectory("note-index")
        val firstFile = dir.resolve("first.notes").toFile().apply { writeText("alpha bravo") }
        val secondFile = dir.resolve("second.notes").toFile().apply { writeText("charlie delta") }
        val service = NoteIndexService()

        service.buildIndex(setOf(NoteCard(firstFile.name, firstFile.path)))
        assertEquals(1, service.search("bravo").size)
        assertEquals(0, service.search("delta").size)

        service.buildIndex(setOf(NoteCard(secondFile.name, secondFile.path)))
        assertEquals(0, service.search("bravo").size)
        assertEquals(1, service.search("delta").size)
    }
}
