package notes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notes.ui.MainPanel
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderContent
import java.io.File
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
class NotesService(private val project: Project, val scope: CoroutineScope) : Disposable {
    private val filesService = service<FilesState>()
    private val _currentNoteCard = MutableStateFlow<LoadedNoteCard?>(null)
    val currentNoteCard = _currentNoteCard.asStateFlow()
    val state = MutableStateFlow(NinjaState.LOADING)
    private val _noteActions = MutableSharedFlow<NoteAction>()
    val notesActions = _noteActions.asSharedFlow()

    private val currentNoteCardJob = scope.launch {
        currentNoteCard.collect { note ->
            if (note == null) return@collect
            updateTitle(note)
            filesService.setLastFile(note.noteCard)
        }
    }
    private val stateJob = scope.launch {
        state.collect { newState -> updateTitle() }
    }

    @Deprecated("")
    private val editorPanel: MainPanel?
        get() = UIUtil.findComponentOfType(toolWindow.component, MainPanel::class.java)

    suspend fun default() {
        val note = filesService.state.lastFile?.takeIf { it.exist() }
            ?: filesService.list().firstOrNull { it.exist() }
            ?: NoteCard(defaultFile.name, defaultFile.absolutePath).apply {
                filesService.addFile(this)
                filesService.state.lastFile = this
            }
        openFile(note)
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
        val document = currentNoteCard.value?.getDocument()
        readAndWriteAction {
            writeAction {
                CommandProcessor.getInstance().executeCommand(project, {
                    document?.setText(document.text + "\n\n" + text)
                }, "Insert", null)
            }
        }
    }

    suspend fun openFile(note: NoteCard) {
        _currentNoteCard.emit(LoadedNoteCard(note))
        state.emit(NinjaState.OPENED_NOTE)
    }

    suspend fun updateTitle(note: LoadedNoteCard? = null) {
        val noteCardToUpdate: LoadedNoteCard = note ?: currentNoteCard.value ?: return
        val psiFile = noteCardToUpdate.getPsiFile(project)
        val header = readAction { PsiTreeUtil.findChildOfType(psiFile, MarkdownHeader::class.java) }
        if (header != null) {
            noteCardToUpdate.noteCard.name = readAction { header.text }.let {
                it.trim().trimStart { it == '#' }.trim()
            }
        }
    }

    suspend fun topics(): List<Topic> {
        val psiFile = currentNoteCard.value?.getPsiFile(project) ?: return emptyList()
        return readAction { PsiTreeUtil.findChildrenOfType(psiFile, MarkdownHeaderContent::class.java) }
            .map { Topic.fromMarkdownHeader(it) }
    }

    suspend fun scrollToDown() {
        if (state.value != NinjaState.OPENED_NOTE) {
            state.emit(NinjaState.OPENED_NOTE)
        }
        _noteActions.emit(NoteAction.ScrollDown())
    }

    suspend fun scrollToElement(topic: Topic) {
        if (state.value != NinjaState.OPENED_NOTE) {
            state.emit(NinjaState.OPENED_NOTE)
        }
        _noteActions.emit(NoteAction.ScrollToElement(topic))
    }

    suspend fun insertTextToCaret(text: String) {
        if (state.value != NinjaState.OPENED_NOTE) {
            state.emit(NinjaState.OPENED_NOTE)
        }
        _noteActions.emit(NoteAction.InsertText(text))
    }

    suspend fun foldLinks() {
        _noteActions.emit(NoteAction.RefoldLinks())
    }


    suspend fun goToNoteList() {
        state.emit(NinjaState.FILES)
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

    override fun dispose() {
        currentNoteCardJob.cancel()
        stateJob.cancel()
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

// todo: delete note with X
// todo: load all notes from .note