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
    fun buildIndex(notes: Set<NoteCard>) {
        if (indexMap.isEmpty()) {
            index.clear()
            notes.map { note ->
                val content = File(note.path).readText()
                NoteIndex(note.hashCode(), note.path, note.name, content)
                    .also { indexMap[it.id] = note }
            }.forEach { idx -> index.add(idx) }
        }
    }

    suspend fun rebuild(note: LoadedNoteCard) {
        indexMap[note.noteCard.hashCode()]?.let { index.remove(NoteIndex(it.hashCode(), it.path, it.name, "")) }
        val newIndex =
            NoteIndex(note.noteCard.hashCode(), note.file.absolutePath, note.file.name, note.getDocument().text)
        index.add(newIndex)
        indexMap[newIndex.id] = note.noteCard
    }

    fun search(query: String) = index.search(query).map {
        Result(indexMap[it.documentId] ?: throw IllegalStateException("No note with id ${it.documentId}"), it)
    }
    fun autocomplete(query: String) = index.autocomplete(query)
}

data class NoteIndex(@Id val id: Int, val path: String, val name: String, val text: String)