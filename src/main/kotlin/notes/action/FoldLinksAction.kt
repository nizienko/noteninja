package notes.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.launch
import notes.NinjaState
import notes.NotesService

class FoldLinksAction: DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<NotesService>()?.apply { scope.launch { foldLinks() } }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.project?.service<NotesService>()?.state?.value == NinjaState.OPENED_NOTE
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}