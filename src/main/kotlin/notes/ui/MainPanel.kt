package notes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notes.NinjaState
import notes.NotesService
import java.awt.CardLayout
import javax.swing.JPanel


class MainPanel(project: Project, disposable: Disposable) : BorderLayoutPanel(), Disposable {
    private val service = project.service<NotesService>()

    init {
        Disposer.register(disposable, this)
    }


    val fileList = ChooseFilePanel(project)
    val openedEditor = OpenedNoteEditor(project)
    val searchPanel = SearchPanel(project)
    val ninjaLabel = JBLabel("Note ninja")

    private val stateJob = service.scope.launch {
        service.stateFlow.collect { state ->
            withContext(Dispatchers.EDT) {
                mainPanel.components.forEach {
                    it.isVisible = false
                }
                when (state) {
                    NinjaState.FILES -> fileList.isVisible = true
                    NinjaState.OPENED_NOTE -> openedEditor.isVisible = true
                    NinjaState.LOADING -> ninjaLabel.isVisible = true
                    NinjaState.SEARCH -> {
                        searchPanel.isVisible = true
                        searchPanel.focusOnSearchField()
                    }
                }
            }
        }
    }
    private val mainPanel = JPanel(CardLayout())
    init {
        border = Borders.empty()
        mainPanel.apply {
            border = Borders.empty()
            add(openedEditor)
            add(fileList)
            add(ninjaLabel)
            add(searchPanel)
        }
        addToCenter(mainPanel)

        service.scope.launch {
            service.default()
        }
    }

    override fun dispose() {
        openedEditor.dispose()
        fileList.dispose()
        stateJob.cancel()
        searchPanel.dispose()
    }
}