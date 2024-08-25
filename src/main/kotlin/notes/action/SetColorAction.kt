package notes.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.awt.RelativePoint
import notes.NotesService
import notes.ui.popupColor
import java.awt.Point
import javax.swing.JComponent

class SetColorAction: DumbAwareAction(AllIcons.Actions.Colors) {
    override fun actionPerformed(e: AnActionEvent) {
        val note = e.project?.service<NotesService>()?.note ?: return
        val component = e.inputEvent?.source as? JComponent ?: return
        val point = component.locationOnScreen.let {
            Point(it.x + component.width / 2, it.y + component.height / 2)
        }
        popupColor(note).show(RelativePoint(point))
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = "Set Color"
        e.presentation.isEnabledAndVisible = e.project?.service<NotesService>()?.showEditor?.get() ?: false
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}