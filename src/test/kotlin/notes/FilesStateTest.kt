package notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilesStateTest {
    @Test
    fun `mutations update the persistent state modification count`() {
        val filesState = FilesState()
        val note = NoteCard("Note", "/tmp/note.notes")

        val initialCount = filesState.stateModificationCount
        filesState.addFile(note)
        assertTrue(filesState.stateModificationCount > initialCount)

        val addedCount = filesState.stateModificationCount
        note.color = "#FFFFFF"
        filesState.fileChanged(note)
        assertTrue(filesState.stateModificationCount > addedCount)

        filesState.removeFile(note)
        assertEquals(emptySet(), filesState.list())
    }
}
