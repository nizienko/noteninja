package notes.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.launch
import notes.NotesService

class CloseFileAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val service = e.project?.service<NotesService>() ?: return
        service.scope.launch {
            service.updateTitle()
            service.showFileList()
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project ?: return
        e.presentation.isEnabledAndVisible = project.service<NotesService>().showEditor.get()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}