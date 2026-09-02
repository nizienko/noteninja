package notes

import com.google.gson.Gson
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import java.io.File


@Service
@State(name = "notes.xml", storages = [Storage("notes.xml", roamingType = RoamingType.DISABLED)])
class FilesState : SimplePersistentStateComponent<Files>(Files()) {
    fun addFile(note: NoteCard) {
        if (state.files.add(note)) {
            state.markModified()
        }
    }

    fun removeFile(note: NoteCard) {
        val newState = state.files.toMutableSet().also { it.remove(note) }
        if (newState != state.files) {
            state.files = newState
            state.markModified()
        }
    }

    fun setFileList(list: List<NoteCard>) {
        val newFiles = list.toMutableSet()
        if (newFiles.toList() != state.files.toList()) {
            state.files = newFiles
            state.markModified()
        }
    }

    fun setLastFile(note: NoteCard) {
        if (state.lastFile != note) {
            state.lastFile = note
            state.markModified()
        }
    }

    fun fileChanged(note: NoteCard) {
        if (state.files.any { it.path == note.path }) {
            state.markModified()
        }
        if (state.lastFile?.path == note.path) {
            state.lastFile = note
        }
    }

    fun list(): Set<NoteCard> = state.files.toSet()
}

class Files : BaseState() {
    @OptionTag(converter = NotesConverter::class)
    var files: MutableSet<NoteCard> = mutableSetOf()
    @OptionTag(converter = NoteCardConverter::class)
    var lastFile: NoteCard? = null

    fun markModified() {
        incrementModificationCount()
    }
}

data class NoteCard(
    var name: String, val path: String, var color: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        return this.path == (other as? NoteCard)?.path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }
}

fun NoteCard.exist(): Boolean = File(path).exists()

private val gson = Gson()

class NoteCardConverter : Converter<NoteCard>() {
    override fun toString(value: NoteCard): String {
        return gson.toJson(value)
    }

    override fun fromString(value: String): NoteCard {
        return gson.fromJson(value, NoteCard::class.java)
    }
}
class NoteList: ArrayList<NoteCard>()
class NotesConverter : Converter<MutableSet<NoteCard>>() {
    override fun fromString(value: String): MutableSet<NoteCard> {
        val list = gson.fromJson(value, NoteList::class.java)
        return list.toMutableSet()
    }

    override fun toString(value: MutableSet<NoteCard>): String {
        return gson.toJson(value.toList())
    }
}
