package notes.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import kotlinx.coroutines.launch
import notes.NotesService
import javax.swing.JComponent

class ShowHeadersAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val button = e.inputEvent?.source as? JComponent ?: return
        val service = project.service<NotesService>()
        val topics = service.topics
        val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(topics)
        popup.setItemChosenCallback {
            service.scope.launch { service.scrollToElement(it) }
        }
        popup.setRenderer { _, value, _, _, _ ->
            JBLabel(value.name)
        }
        popup.setTitle("Go To")
        popup.createPopup().showUnderneathOf(button)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project ?: return
        e.presentation.isEnabledAndVisible = project.service<NotesService>().showEditor.get()
    }
}