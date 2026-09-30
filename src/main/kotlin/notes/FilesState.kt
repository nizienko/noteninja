package notes

import com.google.gson.Gson
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import java.io.File


@Service
@State(name = "notes.xml", storages = [Storage("notes.xml", roamingType = RoamingType.DISABLED)])
class FilesState : SimplePersistentStateComponent<Files>(Files()) {
    @Synchronized fun addFile(note: NoteCard) {
        if (note in state.files) return
        state.files = (state.files + note).toMutableSet()
        state.markChanged()
    }

    @Synchronized fun removeFile(note: NoteCard) {
        if (note in state.files) {
            state.files = (state.files - note).toMutableSet()
            state.markChanged()
        }
        if (state.lastFile == note) {
            state.lastFile = null
            state.markChanged()
        }
    }

    @Synchronized fun setFileList(list: List<NoteCard>) {
        if (state.files.toList() == list.distinct()) return
        val existing = state.files.associateBy { it.path }
        state.files = list.mapTo(linkedSetOf()) { existing[it.path] ?: it }
        state.markChanged()
    }

    @Synchronized fun setLastFile(note: NoteCard) {
        if (state.lastFile == note) return
        state.lastFile = state.files.firstOrNull { it == note } ?: note
        state.markChanged()
    }

    @Synchronized fun list(): Set<NoteCard> = state.files.toSet()
    @Synchronized fun find(note: NoteCard): NoteCard? = state.files.firstOrNull { it == note }

    @Synchronized fun setName(note: NoteCard, name: String) {
        val existing = state.files.firstOrNull { it == note } ?: return
        updateMetadata(existing, name, existing.color)
    }

    @Synchronized fun setColor(note: NoteCard, color: String?) {
        val existing = state.files.firstOrNull { it == note } ?: return
        updateMetadata(existing, existing.name, color)
    }

    private fun updateMetadata(note: NoteCard, name: String, color: String?) {
        val existing = state.files.firstOrNull { it == note } ?: return
        if (existing.name == name && existing.color == color) return
        val updated = existing.copy(name = name, color = color)
        state.files = state.files.mapTo(linkedSetOf()) { if (it == note) updated else it }
        if (state.lastFile == note) state.lastFile = updated
        state.markChanged()
    }
}

class Files : BaseState() {
    // Keep the legacy converter-backed XML fields; every mutation is explicitly tracked by FilesState.
    fun markChanged() = incrementModificationCount()
    @OptionTag(converter = NotesConverter::class)
    var files: MutableSet<NoteCard> = mutableSetOf()
    @OptionTag(converter = NoteCardConverter::class)
    var lastFile: NoteCard? = null
}

data class NoteCard(
    val name: String, val path: String, val color: String? = null,
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
