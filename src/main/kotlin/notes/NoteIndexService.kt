package notes

import com.haroldadmin.lucilla.annotations.Id
import com.haroldadmin.lucilla.core.SearchResult
import com.haroldadmin.lucilla.core.useFts
import com.intellij.openapi.components.Service
import java.io.File

@Service
class NoteIndexService {
    data class Result(val note: NoteCard, val searchResult: SearchResult)
    private val index = useFts<NoteIndex>()
    private val indexMap = mutableMapOf<Int, NoteCard>()
    private val pathIds = mutableMapOf<String, Int>()
    private val lock = Any()
    private var nextId = 1

    fun buildIndex(notes: Set<NoteCard>) = synchronized(lock) {
        val removedPaths = pathIds.keys - notes.mapTo(mutableSetOf()) { it.path }
        removedPaths.mapNotNull { path -> indexMap[pathIds.getValue(path)] }.forEach(::remove)

        notes.filter { it.path !in pathIds && it.exist() }.forEach { note ->
            val id = nextId++
            val noteIndex = NoteIndex(id, note.path, note.name, File(note.path).readText())
            pathIds[note.path] = id
            indexMap[id] = note
            index.add(noteIndex)
        }
    }

    suspend fun rebuild(note: LoadedNoteCard) {
        val text = note.getDocument().text
        synchronized(lock) {
            val id = pathIds[note.noteCard.path] ?: nextId++
            indexMap[id]?.let { index.remove(NoteIndex(id, it.path, it.name, "")) }
            val newIndex = NoteIndex(id, note.file.absolutePath, note.noteCard.name, text)
            index.add(newIndex)
            pathIds[note.noteCard.path] = id
            indexMap[newIndex.id] = note.noteCard
        }
    }

    fun remove(note: NoteCard) {
        synchronized(lock) {
            val id = pathIds.remove(note.path) ?: return@synchronized
            index.remove(NoteIndex(id, note.path, note.name, ""))
            indexMap.remove(id)
        }
    }

    fun search(query: String) = synchronized(lock) {
        index.search(query).mapNotNull {
            indexMap[it.documentId]?.let { note -> Result(note, it) }
        }
    }
    fun autocomplete(query: String) = synchronized(lock) { index.autocomplete(query) }
}

data class NoteIndex(@Id val id: Int, val path: String, val name: String, val text: String)
