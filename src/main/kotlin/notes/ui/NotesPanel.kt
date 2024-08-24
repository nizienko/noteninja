package notes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.*
import com.intellij.openapi.observable.util.bindVisible
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notes.NotesService
import notes.Topic
import notes.file.NotesFileType
import notes.folding.LinksFoldingBuilder.Companion.LINK_PLACEHOLDER
import notes.symbols.LinkEditorListener
import java.awt.CardLayout
import java.awt.Color
import javax.swing.JPanel
import kotlin.math.max


class NotesPanel(private val project: Project, private val disposable: Disposable) : BorderLayoutPanel() {
    private lateinit var editor: Editor

    private val service = project.service<NotesService>()
    private val fileList = ChooseFilePanel(project)
        .bindVisible(service.showCustomPanel)
    private val panel = JPanel(CardLayout())

    init {
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
                border = JBUI.Borders.customLine(Color.decode(service.note.color), 0, 1, 0, 0)
            }
            panel.removeAll()
            panel.add(editorPanel)
            panel.add(fileList)
            addToCenter(panel)
            revalidate()
            repaint()
            editorPanel.requestFocus()
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
        writeAction {
            CommandProcessor.getInstance().executeCommand(project, {
                editor.document.insertString(offset, text)
            }, "Insert", null)
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
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

    fun foldHeader(element: PsiElement) {
        val project = editor.project ?: return
        val foldingModel = editor.foldingModel
        project.service<NotesService>().scope.launch {
            writeAction {
                foldingModel.runBatchFoldingOperation {
                    val foldRegions = editor.foldingModel.allFoldRegions.filter {
                        it.startOffset <= element.startOffset && it.endOffset >= element.endOffset
                    }
                    foldRegions.maxByOrNull { it.startOffset }?.let {
                        if (it.isExpanded) {
                            it.isExpanded = false
                        }
                    }
                }
            }
        }
    }

    suspend fun scrollToElement(topic: Topic) {
        writeAction {
            editor.caretModel.moveToOffset(topic.offset)
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
    }


    fun loadFileList() {
        fileList.reLoadFileList()
    }
}