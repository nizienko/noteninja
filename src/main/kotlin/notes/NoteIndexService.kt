package notes

import com.haroldadmin.lucilla.annotations.Id
import com.haroldadmin.lucilla.core.SearchResult
import com.haroldadmin.lucilla.core.useFts
import com.intellij.openapi.components.Service
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File

@Service
class NoteIndexService {
    data class Result(val note: NoteCard, val searchResult: SearchResult)
    private val lock = Any()
    // Shared by every project; acquire before enumeration and document reads.
    private val updates = Mutex()
    private var index = useFts<NoteIndex>()
    private val snapshots = mutableMapOf<Int, NoteIndex>()
    private val pathIds = mutableMapOf<String, Int>()
    private val notesById = mutableMapOf<Int, NoteCard>()
    private var nextId = 0

    private fun key(note: NoteCard) = File(note.path).toPath().toAbsolutePath().normalize().toString()

    suspend fun refresh(notes: () -> Set<NoteCard>): Set<NoteCard> = refresh(notes, ::currentText)

    internal suspend fun refresh(
        notes: () -> Set<NoteCard>,
        readText: suspend (NoteCard) -> String,
    ): Set<NoteCard> = withContext(Dispatchers.IO) {
        updates.withLock {
            val current = notes()
            val contents = current.map { it to readText(it) }
            coroutineContext.ensureActive()
            replaceAll(contents)
            current
        }
    }

    private suspend fun currentText(note: NoteCard): String {
        val documentText = readAction {
            LocalFileSystem.getInstance().findFileByPath(key(note))?.let {
                FileDocumentManager.getInstance().getCachedDocument(it)?.text
            }
        }
        return documentText ?: File(note.path).readText()
    }

    // Disk-only entry point for regression tests without an IDE document.
    internal fun buildIndex(notes: Set<NoteCard>) {
        replaceAll(notes.map { it to File(it.path).readText() })
    }

    private fun replaceAll(contents: List<Pair<NoteCard, String>>) {
        val replacement = useFts<NoteIndex>()
        val newSnapshots = mutableMapOf<Int, NoteIndex>()
        val newPaths = mutableMapOf<String, Int>()
        val newNotes = mutableMapOf<Int, NoteCard>()
        contents.forEach { (note, text) ->
            val path = key(note)
            if (path in newPaths) return@forEach
            val id = synchronized(lock) { pathIds[path] ?: nextId++ }
            val snapshot = NoteIndex(id, path, note.name, text)
            replacement.add(snapshot)
            newSnapshots[id] = snapshot
            newPaths[path] = id
            newNotes[id] = note
        }
        synchronized(lock) {
            index = replacement
            snapshots.clear()
            snapshots.putAll(newSnapshots)
            pathIds.clear()
            pathIds.putAll(newPaths)
            notesById.clear()
            notesById.putAll(newNotes)
        }
    }

    internal fun replace(note: NoteCard, text: String) = synchronized(lock) {
        val path = key(note)
        val id = pathIds.getOrPut(path) { nextId++ }
        snapshots[id]?.let { index.remove(it) }
        val snapshot = NoteIndex(id, path, note.name, text)
        index.add(snapshot)
        snapshots[id] = snapshot
        notesById[id] = note
    }

    suspend fun rebuild(note: LoadedNoteCard) = withContext(Dispatchers.IO) {
        updates.withLock { replace(note.noteCard, currentText(note.noteCard)) }
    }

    suspend fun remove(note: NoteCard, unregister: () -> Unit) = withContext(Dispatchers.IO) {
        updates.withLock {
            unregister()
            removeSnapshot(note)
        }
    }

    internal fun removeSnapshot(note: NoteCard) = synchronized(lock) {
        pathIds.remove(key(note))?.let { id ->
            snapshots.remove(id)?.let { index.remove(it) }
            notesById.remove(id)
        }
        Unit
    }

    fun search(query: String) = synchronized(lock) {
        index.search(query).map { Result(notesById.getValue(it.documentId), it) }
    }

    fun autocomplete(query: String) = synchronized(lock) { index.autocomplete(query) }
}

data class NoteIndex(@Id val id: Int, val path: String, val name: String, val text: String)
