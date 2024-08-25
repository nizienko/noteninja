package notes.toolWindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.components.BorderLayoutPanel
import notes.ui.NotesPanel

class NotesToolwindow : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(createUI(project, toolWindow), "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createUI(project: Project, toolWindow: ToolWindow): BorderLayoutPanel {
        val actions = listOf(
//            "notes.action.SetColorAction",
            "notes.action.ShowHeadersAction",
            "notes.action.FoldLinksAction",
            "notes.action.CloseFileAction",
        ).mapNotNull { ActionManager.getInstance().getAction(it) }
        toolWindow.setTitleActions(actions)
        return NotesPanel(project, toolWindow.disposable)
    }
}