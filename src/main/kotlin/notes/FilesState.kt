package notes

import com.google.gson.Gson
import com.intellij.openapi.components.*
import com.intellij.ui.JBColor
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import java.awt.Color


@Service
@State(name = "notes.xml", storages = [Storage("notes.xml", roamingType = RoamingType.DISABLED)])
class FilesState : SimplePersistentStateComponent<Files>(Files()) {
    fun addFile(note: NoteCard) {
        state.files.add(note)
    }

    fun removeFile(note: NoteCard) {
        state.files.remove(note)
    }

    fun setFileList(list: List<NoteCard>) {
        state.files.clear()
        state.files.addAll(list)
    }

    fun setLastFile(note: NoteCard) {
        state.lastFile = note
    }

    fun list(): List<NoteCard> = state.files
}

class Files : BaseState() {
    @OptionTag(converter = NotesConverter::class)
    var files: MutableList<NoteCard> = mutableListOf()
    @OptionTag(converter = NoteCardConverter::class)
    var lastFile: NoteCard? = null
}

data class NoteCard(
    var name: String, val path: String, var color: String? = null,
)

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
class NotesConverter : Converter<MutableList<NoteCard>>() {
    override fun fromString(value: String): ArrayList<NoteCard> {
        return gson.fromJson(value, NoteList::class.java)
    }

    override fun toString(value: MutableList<NoteCard>): String {
        return gson.toJson(value)
    }
}