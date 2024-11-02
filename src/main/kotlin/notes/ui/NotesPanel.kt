package notes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.*
import com.intellij.openapi.observable.util.bindVisible
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notes.NotesService
import notes.Topic
import notes.file.NotesFileType
import notes.folding.LinksFoldingBuilder.Companion.LINK_PLACEHOLDER
import notes.parseColor
import notes.symbols.LinkEditorListener
import java.awt.CardLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel
import kotlin.math.max


class NotesPanel(private val project: Project, private val disposable: Disposable) : BorderLayoutPanel() {
    private lateinit var editor: Editor

    private val service = project.service<NotesService>()
    val fileList = ChooseFilePanel(project)
        .bindVisible(service.showCustomPanel)
    private val panel = JPanel(CardLayout()).apply {
        border = Borders.empty()
    }

    init {
        border = Borders.empty()
        service.scope.launch {
            service.loadDefault()
            createUI()
        }
    }

    suspend fun createUI() {
        val document = service.document ?: return
        withContext(Dispatchers.EDT) {
            editor = EditorFactory.getInstance()
                .createEditor(document, project, NotesFileType.INSTANCE, false)
                .apply {
                    addEditorMouseListener(LinkEditorListener())
                }
            val editorSettings = editor.settings
            editorSettings.isLineNumbersShown = false
            editorSettings.setGutterIconsShown(false)
            editorSettings.isLineMarkerAreaShown = false
            editorSettings.isFoldingOutlineShown = true

            val editorPanel = DisposableEditorPanel(editor).apply {
                Disposer.register(disposable, this)
                bindVisible(service.showEditor)
                service.note.color?.let { color ->
                    border = Borders.customLine(
                        parseColor(color), 0, 1, 0, 0
                    )
                }
            }
            editor.contentComponent.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (KeyEvent.VK_ESCAPE == e.keyCode) {
                        service.scope.launch {
                            service.showFileList()
                        }
                    }
                }
            })
            panel.removeAll()
            panel.add(editorPanel)
            panel.add(fileList)
            addToCenter(panel)
            revalidate()
            repaint()
        }
    }

    fun scrollToDown() {
        editor.scrollingModel.scrollTo(
            LogicalPosition(max(0, editor.document.lineCount - 1), 0),
            ScrollType.MAKE_VISIBLE
        )
    }

    suspend fun insertToCaret(text: String) {
        val offset = readAction { editor.caretModel.primaryCaret.offset }
        readAndWriteAction {
            writeAction {
                CommandProcessor.getInstance().executeCommand(project, {
                    editor.document.insertString(offset, text)
                }, "Insert", null)
                editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
            }
        }
        refoldLinks()
    }

    fun hasUnfoldedLinks(): Boolean {
        return linksFoldingRegions().any { it.isExpanded }
    }

    private fun linksFoldingRegions(): List<FoldRegion> {
        if (::editor.isInitialized.not()) return emptyList()
        return editor.foldingModel.allFoldRegions.filter { it.placeholderText == LINK_PLACEHOLDER }
    }

    fun refoldLinks() {
        val project = editor.project ?: return
        val foldingModel = editor.foldingModel
        project.service<NotesService>().scope.launch {
            readAndWriteAction {
                writeAction {
                    foldingModel.runBatchFoldingOperation {
                        val foldRegions = linksFoldingRegions()
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

    fun requestFocusOnEditor() {
        editor.contentComponent.requestFocusInWindow()
    }

    suspend fun scrollToElement(topic: Topic) {
        readAndWriteAction {
            writeAction {
                editor.caretModel.moveToOffset(topic.offset)
                editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
            }
        }
    }

    fun loadFileList() {
        fileList.reLoadFileList()
    }
}