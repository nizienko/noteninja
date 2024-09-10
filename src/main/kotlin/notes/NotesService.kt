package notes

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notes.ui.NotesPanel
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderContent
import java.io.File
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class NotesService(private val project: Project, val scope: CoroutineScope) {
    private val filesService = service<FilesState>()
    lateinit var notesFile: File
    private lateinit var virtualFile: VirtualFile
    private lateinit var psiFile: PsiFile
    private lateinit var currentNote: NoteCard
    var document: Document? = null
        private set
    val showEditor = AtomicBooleanProperty(false)
    val showCustomPanel = AtomicBooleanProperty(true)

    private val editorPanel: NotesPanel?
        get() = UIUtil.findComponentOfType(toolWindow.component, NotesPanel::class.java)

    val note: NoteCard
        get() = currentNote

    suspend fun loadDefault() {
        val note = filesService.state.lastFile?.takeIf { it.exist() }
            ?: filesService.list().firstOrNull { it.exist() }
            ?: NoteCard(defaultFile.name, defaultFile.absolutePath).apply {
                filesService.addFile(note)
                filesService.state.lastFile = note
            }
        loadFile(note)
        reloadCurrentDocument()
    }

    val toolWindow
        get() = ToolWindowManager.getInstance(project).getToolWindow("Notes")
            ?: throw IllegalStateException("Can't find Notes toolwindow")

    private val defaultDir = Path(System.getProperty("user.home"))
        .normalize()
        .resolve(".notes") ?: throw IllegalStateException()

    private val defaultFile: File
        get() {
            val notesFile = defaultDir
                .apply { toFile().mkdirs() }
                .resolve("default.notes")
                .toFile()
            if (notesFile.exists().not()) {
                notesFile.writeText("# Notes")
            }
            return notesFile
        }

    suspend fun writeText(text: String) {
        readAndWriteAction {
            writeAction {
                document?.setText(document?.text + "\n\n" + text)
            }
        }
    }

    suspend fun loadFile(note: NoteCard) {
        notesFile = File(note.path)
        virtualFile = VfsUtil.findFileByIoFile(notesFile, true) ?: throw IllegalStateException("Can't find notes file")
        psiFile = readAction { PsiManager.getInstance(project).findFile(virtualFile) }
            ?: throw IllegalStateException("Can't find notes psi file")
        currentNote = note
        updateTitle()
        document = readAction { virtualFile.findDocument() ?: throw IllegalStateException("Can't find document") }

        filesService.setLastFile(note)
    }

    suspend fun updateTitle() {
        val header = readAction { PsiTreeUtil.findChildOfType(psiFile, MarkdownHeader::class.java) }
        if (header != null) {
            currentNote.name = readAction { header.text }.let {
                it.trim().trimStart { it == '#' }.trim()
            }
        }
    }

    suspend fun reloadCurrentDocument() {
        editorPanel?.createUI()
        showEditor()
    }

    val topics: List<Topic>
        get() = PsiTreeUtil.findChildrenOfType(psiFile, MarkdownHeaderContent::class.java)
            .map { Topic.fromMarkdownHeader(it) }

    fun scrollToDown() {
        editorPanel?.scrollToDown()
    }

    suspend fun scrollToElement(topic: Topic) {
        editorPanel?.scrollToElement(topic)
    }

    suspend fun insertTextToCaret(text: String) {
        editorPanel?.insertToCaret(text)
    }

    fun foldLinks() = editorPanel?.refoldLinks()
    fun hasUnfoldedLinks(): Boolean = editorPanel?.hasUnfoldedLinks() ?: false

    private suspend fun showEditor() = withContext(Dispatchers.EDT) {
        showCustomPanel.getAndSet(false)
        showEditor.getAndSet(true)
        editorPanel?.requestFocusOnEditor()
    }

    suspend fun showFileList() = withContext(Dispatchers.EDT) {
        showEditor.getAndSet(false)
        editorPanel?.loadFileList()
        showCustomPanel.getAndSet(true)
        editorPanel?.fileList?.setFocus()
    }

    fun createNewFile(name: String): NoteCard {
        fun assumeFile(n: Int): File = defaultDir
            .apply { toFile().mkdirs() }
            .resolve("$name${n.takeIf { it > 0 } ?: ""}.notes")
            .toFile()

        var n = 0
        var newFile: File = assumeFile(n)
        while (newFile.exists()) {
            n++
            newFile = assumeFile(n)
        }
        newFile.writeText("# $name")

        return NoteCard(newFile.name, newFile.path)
    }
}

data class Topic(val name: String, val offset: Int) {
    companion object {
        fun fromMarkdownHeader(header: MarkdownHeaderContent): Topic {
            return Topic(header.text, header.startOffset)
        }
    }
}

val linkTextRegex = """\[([A-z.]*):(\d+)]""".toRegex()
val linkRegex = Regex("""\[([A-z.]*):(\d+)]\(.*\)""")
