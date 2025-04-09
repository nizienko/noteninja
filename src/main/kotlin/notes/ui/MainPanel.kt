package notes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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

    private val stateJob = service.scope.launch {
        service.state.collect {
            withContext(Dispatchers.EDT) {
                fileList.isVisible = false
                openedEditor.isVisible = false
                when (it) {
                    NinjaState.FILES -> fileList.isVisible = true
                    NinjaState.OPENED_NOTE -> openedEditor.isVisible = true
                    NinjaState.LOADING -> {}
                }
            }
        }
    }

    init {
        border = Borders.empty()
        val panel = JPanel(CardLayout()).apply {
            border = Borders.empty()
            add(openedEditor)
            add(fileList)
        }
        addToCenter(panel)

        service.scope.launch {
            service.default()
        }
    }

    override fun dispose() {
        openedEditor.dispose()
        fileList.dispose()
        stateJob.cancel()
    }
}