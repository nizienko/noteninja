package notes.symbols

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.util.PsiUtilBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notes.NotesService
import notes.linkTextRegex

class LinkEditorListener : EditorMouseListener {

    override fun mouseClicked(event: EditorMouseEvent) {
        val editor = event.editor
        val element = PsiUtilBase.getElementAtCaret(editor)?.parent ?: return
        val project = editor.project ?: return

        if (isControlOrMetaDown(event)) {
            if (linkTextRegex.matches(element.text) ) {
                val offset = linkTextRegex.matchEntire(element.text)?.groupValues?.get(2) ?: return
                val parentText = element.parent.text
                val path = notes.linkRegex.matchEntire(parentText)?.groupValues?.get(3) ?: return
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
                    editors.filterIsInstance<TextEditor>().firstOrNull { it.file == file }?.editor?.let {
                        withContext(Dispatchers.EDT) {
                            it.caretModel.moveToOffset(offsetInt.coerceIn(0, it.document.textLength))
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
            withContext(Dispatchers.EDT) {
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
