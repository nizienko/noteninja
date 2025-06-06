package notes.toolWindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.components.BorderLayoutPanel
import notes.ui.MainPanel

class NotesToolwindow : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(createUI(project, toolWindow), "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createUI(project: Project, toolWindow: ToolWindow): BorderLayoutPanel {
        val actions = listOf(
            "notes.actions.file.BackAction",
            "notes.actions.file.ShowHeadersAction",
            "notes.actions.file.FoldLinksAction",
        ).mapNotNull { ActionManager.getInstance().getAction(it) }
        toolWindow.setTitleActions(actions)
        return MainPanel(project, toolWindow.disposable)
    }
}