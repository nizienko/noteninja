package notes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.command.CommandProcessor
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

    private val fileJob = service.scope.launch {
        service.currentNoteCard.collect { note ->
            withContext(Dispatchers.EDT) {
                removeAll()
            }
            if (note == null) return@collect
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
                            service.scope.launch {
                                service.goToNoteList()
                            }
                        }
                    }
                })
                currentEditor = editor
                val editorPanel = DisposableEditorPanel(editor)
                Disposer.register(this@OpenedNoteEditor, editorPanel)
                editorPanel.border = Borders.customLine(parseColor(note.noteCard.color), 0, 1, 0, 0)

                addToCenter(editorPanel)
                editor.contentComponent.requestFocusInWindow()
                revalidate()
                repaint()
            }
        }
    }

    private val noteActionsJob = service.scope.launch {
        service.notesActions.collect { action ->
            val editor = currentEditor ?: return@collect
            when (action) {
                is NoteAction.InsertText -> {
                    val offset = readAction { editor.caretModel.primaryCaret.offset }
                    readAndWriteAction {
                        writeAction {
                            CommandProcessor.getInstance().executeCommand(project, {
                                editor.document.insertString(offset, action.text)
                            }, "Insert", null)
                            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                        }
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

                is NoteAction.ScrollToElement -> readAndWriteAction {
                    writeAction {
                        editor.caretModel.moveToOffset(action.topic.offset)
                        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                    }
                }
            }
        }
    }

    fun refoldLinks(editor: Editor) {
        val project = editor.project ?: return
        val foldingModel = editor.foldingModel
        project.service<NotesService>().scope.launch {
            readAndWriteAction {
                writeAction {
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
    }

    private fun linksFoldingRegions(editor: Editor): List<FoldRegion> {
        return editor.foldingModel.allFoldRegions.filter { it.placeholderText == LINK_PLACEHOLDER }
    }

    override fun dispose() {
        fileJob.cancel()
        noteActionsJob.cancel()
    }
}