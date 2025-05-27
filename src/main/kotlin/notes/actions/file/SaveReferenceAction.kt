package notes.actions.file

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.launch
import notes.NotesService
import notes.linkTextRegex

class SaveReferenceAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val text = createText(editor)
        val service = project.service<NotesService>()
        service.scope.launch {
            service.insertTextToCaret(text)
        }
        service.toolWindow?.activate(null)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = (project != null
                && editor != null
                && project.service<NotesService>().toolWindow != null
                )
    }

    private fun createText(editor: Editor): String {
        return createReference(editor)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

fun createReference(editor: Editor): String {
    val defaultName = editor.virtualFile.presentableName
    val project = editor.project ?: return defaultName
    val caret = editor.caretModel.primaryCaret
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return defaultName
    val element = psiFile.findElementAt(caret.offset)
    val name = element?.text ?: defaultName
    val path = editor.virtualFile.path
    val linkName = createLinkName(name, caret.offset)
    if (linkTextRegex.matches(linkName)) return createLink(linkName, path)
    return createLink(createLinkName(defaultName, caret.offset), path)
}

private fun createLinkName(name: String, offset: Int): String = "[$name:${offset}]"
private fun createLink(name: String, path: String): String = "$name($path)"