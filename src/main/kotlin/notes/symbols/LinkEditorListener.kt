package notes.symbols

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.util.PsiUtilBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notes.NotesService
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderContent
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLink
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkText

class LinkEditorListener : EditorMouseListener {

    override fun mouseClicked(event: EditorMouseEvent) {
        val editor = event.editor
        val element = PsiUtilBase.getElementAtCaret(editor)?.parent ?: return
        val project = editor.project ?: return

        if (isControlOrMetaDown(event)) {
            if (element is MarkdownLinkText) {
                val (_, offset) = element.text.substringAfter("[").substringBefore("]").split(":")
                    .takeIf { it.size == 2 }
                    ?.let { it[0] to it[1] }
                    ?: return
                val path = (element.parent as? MarkdownLink)?.linkDestination?.text ?: return
                project.service<NotesService>().scope.launch {
                    val file = LocalFileSystem.getInstance().findFileByPath(path)
                    if (file == null) {
                        showFileNotFoundError(editor)
                        return@launch
                    }
                    val editors = withContext(Dispatchers.EDT) {
                        FileEditorManager.getInstance(project).openFile(file, true)
                    }
                    val offsetInt = offset.toIntOrNull() ?: return@launch
                    editors.firstOrNull { it.file == file }?.let { it as? TextEditorImpl }?.editor?.let {
                        withContext(Dispatchers.EDT) {
                            it.caretModel.moveToOffset(offsetInt)
                            it.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                        }
                    }
                }
            }
        }
    }

    private fun showFileNotFoundError(editor: Editor) {
        val project = editor.project ?: return
        project.service<NotesService>().scope.launch {
            writeAction {
                HintManager.getInstance().showErrorHint(editor, "Can't find file")
            }
        }
    }

    private fun isControlOrMetaDown(event: EditorMouseEvent): Boolean {
        return if (System.getProperty("os.name").lowercase().contains("mac")) {
            event.mouseEvent.isMetaDown
        } else {
            event.mouseEvent.isControlDown
        }
    }
}