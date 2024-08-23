package notes.file

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import javax.swing.Icon

const val NOTES_FILE_EXTENSION = "notes"

class NotesFileType : LanguageFileType(MarkdownLanguage.INSTANCE) {
    companion object {
        @JvmStatic
        val INSTANCE = NotesFileType()
    }

    override fun getName(): String {
        return "Notes"
    }

    override fun getDescription(): String {
        return "Notes"
    }

    override fun getDefaultExtension(): String {
        return NOTES_FILE_EXTENSION
    }

    override fun getIcon(): Icon {
        return AllIcons.Actions.Edit
    }
}