package notes

import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilesStateTest {
    @Test fun `all persisted mutations increase modification count`() {
        val service = FilesState()
        val first = NoteCard("First", "/external/first.notes")
        val second = NoteCard("Second", "/external/second.notes")
        fun changes(action: () -> Unit) {
            val before = service.state.modificationCount
            val componentBefore = service.stateModificationCount
            action()
            assertTrue(service.state.modificationCount > before)
            assertTrue(service.stateModificationCount > componentBefore)
        }
        changes { service.addFile(first) }
        changes { service.addFile(second) }
        changes { service.setLastFile(first) }
        changes { service.setName(first, "Renamed") }
        changes { service.setColor(first, "#123456") }
        changes { service.setFileList(listOf(second, first)) }
        assertEquals(listOf(second.path, first.path), service.list().map { it.path })
        assertEquals("Renamed", service.list().last().name)
        assertEquals("#123456", service.state.lastFile?.color)
        val count = service.state.modificationCount
        service.addFile(first)
        assertEquals(count, service.state.modificationCount)
        changes { service.removeFile(first) }
        assertEquals(null, service.state.lastFile)
    }

    @Test fun `legacy XML loads and mutations survive serialization`() {
        val fixture = Element("state")
            .addContent(Element("option").setAttribute("name", "files").setAttribute("value",
                """[{"name":"External","path":"/external/one.notes","color":"#ffa500"},{"name":"Two","path":"/external/two.notes"}]"""))
            .addContent(Element("option").setAttribute("name", "lastFile").setAttribute("value",
                """{"name":"External","path":"/external/one.notes","color":"#ffa500"}"""))
        val service = FilesState()
        service.loadState(XmlSerializer.deserialize(fixture, Files::class.java))
        assertEquals(listOf("/external/one.notes", "/external/two.notes"), service.list().map { it.path })
        assertEquals("#ffa500", service.state.lastFile?.color)
        val notes = service.list().toList()
        service.setFileList(notes.reversed())
        service.setName(notes[0], "Updated")
        service.setColor(notes[0], "#487de7")
        val reloaded = FilesState()
        val xml = XmlSerializer.serialize(service.state)
        assertTrue(xml.getChildren("option").any { it.getAttributeValue("name") == "files" })
        reloaded.loadState(XmlSerializer.deserialize(xml, Files::class.java))
        assertEquals(service.list().map { Triple(it.name, it.path, it.color) },
            reloaded.list().map { Triple(it.name, it.path, it.color) })
        assertEquals("Updated", reloaded.state.lastFile?.name)
        assertEquals("#487de7", reloaded.state.lastFile?.color)
    }
}
