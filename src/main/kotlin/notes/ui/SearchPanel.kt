package notes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.components.JBList
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.launch
import notes.NoteIndexService
import notes.NotesService
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseMotionAdapter
import javax.swing.DefaultListModel
import javax.swing.JButton

class SearchPanel(project: Project) : BorderLayoutPanel(), Disposable {
    class SearchCompletionProvider(project: Project) :
        TextFieldWithAutoCompletion.StringsCompletionProvider(emptyList(), null) {
    }

    private val index = service<NoteIndexService>()
    private val completionProvider = SearchCompletionProvider(project)
    private val backButton = JButton("<-").apply {
        addActionListener { service.back() }
    }
    private val searchTextField = TextFieldWithAutoCompletion(project, completionProvider, true, "").apply {
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val completions = index.autocomplete(text).map { it.suggestion }
                completionProvider.setItems(completions)
                val searchResults = index.search(text)
                showResults(searchResults)
            }
        })
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (KeyEvent.VK_ESCAPE == e.keyCode) {
                    service.back()
                }
            }
        })
    }

    fun focusOnSearchField() {
        searchTextField.requestFocus()
    }

    private val service = project.service<NotesService>()
    private val model: DefaultListModel<NoteIndexService.Result> = DefaultListModel()

    private val resultsList = JBList(model).apply {
        var hoveredIndex = -1
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1 && e.button == BUTTON1) {
                    val fileToOpen = selectedValue
                    if (fileToOpen == null) return
                    service.scope.launch {
                        service.openFileAndFindKeyword(fileToOpen.note, selectedValue.searchResult.matchTerm)
                    }
                }
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val index = locationToIndex(e.getPoint())
                if (index != hoveredIndex) {
                    hoveredIndex = index
                    repaint()
                }
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                hoveredIndex = -1
                repaint()
            }
        })
        setCellRenderer { list, value, index, isSelected, cellHasFocus ->
            createNoteItem(value.note, index, hoveredIndex, false)
        }
    }

    init {
        addToTop(BorderLayoutPanel().apply {
//            addToLeft(backButton)
            addToCenter(searchTextField)
        })
        addToCenter(resultsList)
    }

    private fun showResults(results: List<NoteIndexService.Result>) {
        model.clear()
        results.forEach { model.addElement(it) }
        resultsList.revalidate()
        resultsList.repaint()
    }

    override fun dispose() {

    }
}