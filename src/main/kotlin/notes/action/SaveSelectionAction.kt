package notes.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.TextRange
import kotlinx.coroutines.launch
import notes.NotesService

class SaveSelectionAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val text = createText(editor)
        val service = project.service<NotesService>()
        service.scope.launch {
            service.writeText(text)
            service.scrollToDown()
        }
        service.toolWindow.activate(null)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = (project != null && editor != null && editor.selectionModel.hasSelection())
    }

    private fun createText(editor: Editor): String {
        val document = editor.document
        val caret = editor.caretModel.primaryCaret
        val start = caret.selectionStart
        val end = caret.selectionEnd
        val fileTYpe = editor.virtualFile.fileType.name.lowercase()
        val selection = document.getText(TextRange(start, end))
        val reference = createReference(editor)
        return "$reference\n```$fileTYpe\n$selection\n```"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}