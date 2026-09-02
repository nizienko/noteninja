package notes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notes.NoteAction
import notes.NotesService
import notes.file.NotesFileType
import notes.folding.LinksFoldingBuilder.Companion.LINK_PLACEHOLDER
import notes.parseColor
import notes.symbols.LinkEditorListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import kotlin.math.max


class OpenedNoteEditor(private val project: Project) : BorderLayoutPanel(), Disposable {
    private val service = project.service<NotesService>()

    private var currentEditor: Editor? = null
    private var currentEditorPanel: DisposableEditorPanel? = null

    private val fileJob = service.scope.launch {
        service.currentNoteCard.collect { note ->
            withContext(Dispatchers.EDT) {
                currentEditorPanel?.let(Disposer::dispose)
                currentEditorPanel = null
                currentEditor = null
                removeAll()
            }
            if (note == null) return@collect
            try {
                val document = note.getDocument()
                withContext(Dispatchers.EDT) {
                    val editor = EditorFactory.getInstance()
                        .createEditor(document, project, NotesFileType.INSTANCE, false)
                    editor.addEditorMouseListener(LinkEditorListener())
                    val editorSettings = editor.settings
                    editorSettings.isLineNumbersShown = false
                    editorSettings.setGutterIconsShown(false)
                    editorSettings.isLineMarkerAreaShown = false
                    editorSettings.isFoldingOutlineShown = true
                    editor.contentComponent.addKeyListener(object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) {
                            if (KeyEvent.VK_ESCAPE == e.keyCode) {
                                service.scope.launch { service.back() }
                            }
                        }
                    })
                    currentEditor = editor
                    val editorPanel = DisposableEditorPanel(editor)
                    currentEditorPanel = editorPanel
                    Disposer.register(this@OpenedNoteEditor, editorPanel)
                    editorPanel.border = Borders.customLine(note.noteCard.color?.parseColor(), 0, 1, 0, 0)

                    addToCenter(editorPanel)
                    editor.contentComponent.requestFocusInWindow()
                    revalidate()
                    repaint()
                    service.notifyEditorReady()
                }
            } catch (_: Exception) {
                // openFile reports load errors; keep this collector alive for the next note.
            }
        }
    }

    private val noteActionsJob = service.scope.launch {
        service.notesActions.collect { action ->
            val editor = currentEditor ?: return@collect
            when (action) {
                is NoteAction.InsertText -> {
                    val offset = readAction { editor.caretModel.primaryCaret.offset }
                    withContext(Dispatchers.EDT) {
                        WriteCommandAction.runWriteCommandAction(project) {
                            editor.document.insertString(offset, action.text)
                        }
                        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                    }
                    refoldLinks(editor)
                }

                is NoteAction.RefoldLinks -> refoldLinks(editor)
                is NoteAction.RequestFocusOnEditor -> editor.contentComponent.requestFocusInWindow()
                is NoteAction.ScrollDown -> {
                    val editor = currentEditor ?: return@collect
                    withContext(Dispatchers.EDT) {
                        editor.scrollingModel.scrollTo(
                            LogicalPosition(max(0, editor.document.lineCount - 1), 0),
                            ScrollType.MAKE_VISIBLE
                        )
                    }
                }

                is NoteAction.ScrollToElement -> withContext(Dispatchers.EDT) {
                        editor.caretModel.moveToOffset(action.topic.offset.coerceIn(0, editor.document.textLength))
                        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                }

                is NoteAction.FindKeyword -> {
                    val offset = readAction { editor.document.text.indexOf(action.text, ignoreCase = true) }
                    if (offset >= 0) {
                        withContext(Dispatchers.EDT) {
                            editor.caretModel.moveToOffset(offset)
                            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                        }
                    }
                }
            }
        }
    }

    fun refoldLinks(editor: Editor) {
        val project = editor.project ?: return
        val foldingModel = editor.foldingModel
        project.service<NotesService>().scope.launch {
            withContext(Dispatchers.EDT) {
                foldingModel.runBatchFoldingOperation {
                    val foldRegions = linksFoldingRegions(editor)
                    for (foldRegion in foldRegions) {
                        if (foldRegion.isExpanded) {
                            foldRegion.isExpanded = false
                        }
                    }
                }
            }
        }
    }

    private fun linksFoldingRegions(editor: Editor): List<FoldRegion> {
        return editor.foldingModel.allFoldRegions.filter { it.placeholderText == LINK_PLACEHOLDER }
    }

    override fun dispose() {
        fileJob.cancel()
        noteActionsJob.cancel()
        currentEditorPanel?.let(Disposer::dispose)
        currentEditorPanel = null
        currentEditor = null
    }
}
