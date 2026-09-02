package notes.ui

import notes.NoteCard
import java.util.concurrent.atomic.AtomicReference
import javax.swing.DefaultListModel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileListPanelTest {
    @Test
    fun `file list replacement keeps the complete snapshot and selection`() {
        val model = DefaultListModel<NoteCard>()
        val notes = listOf(
            NoteCard("First", "/tmp/first.notes"),
            NoteCard("Second", "/tmp/second.notes"),
        )
        val selected = AtomicReference<NoteCard?>()

        SwingUtilities.invokeAndWait {
            selected.set(replaceFileListModel(model, notes, notes.last().path))
        }

        assertEquals(notes, model.elements().toList())
        assertEquals(notes.last(), selected.get())
    }

    @Test
    fun `file list replacement rejects background thread updates`() {
        assertFailsWith<IllegalStateException> {
            replaceFileListModel(DefaultListModel(), emptyList(), null)
        }
    }
}
