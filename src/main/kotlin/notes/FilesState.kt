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
        state.files.add(note)
    }

    fun removeFile(note: NoteCard) {
        val newState = state.files.toMutableSet().also { it.remove(note) }
        state.files = newState
    }

    fun setFileList(list: List<NoteCard>) {
        state.files.clear()
        state.files.addAll(list)
    }

    fun setLastFile(note: NoteCard) {
        state.lastFile = note
    }

    fun list(): Set<NoteCard> = state.files
}

class Files : BaseState() {
    @OptionTag(converter = NotesConverter::class)
    var files: MutableSet<NoteCard> = mutableSetOf()
    @OptionTag(converter = NoteCardConverter::class)
    var lastFile: NoteCard? = null
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