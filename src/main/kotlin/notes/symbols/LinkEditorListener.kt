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
import notes.NoteReferences

class LinkEditorListener : EditorMouseListener {

    override fun mouseClicked(event: EditorMouseEvent) {
        val editor = event.editor
        val element = PsiUtilBase.getElementAtCaret(editor) ?: return
        val project = editor.project ?: return

        if (isControlOrMetaDown(event)) {
            val reference = generateSequence(element) { it.parent }
                .mapNotNull { NoteReferences.parse(it.text) }.firstOrNull() ?: return
            project.service<NotesService>().scope.launch {
                val file = LocalFileSystem.getInstance().findFileByPath(reference.path)
                if (file == null) {
                    showFileNotFoundError(editor)
                    return@launch
                }
                val editors = withContext(Dispatchers.EDT) {
                    FileEditorManager.getInstance(project).openFile(file, true)
                }
                editors.firstOrNull { it.file == file }?.let { it as? TextEditor }?.editor?.let {
                    withContext(Dispatchers.EDT) {
                        it.caretModel.moveToOffset(reference.offset.coerceAtMost(it.document.textLength))
                        it.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                    }
                }
            }
        }
    }

    private suspend fun showFileNotFoundError(editor: Editor) {
        withContext(Dispatchers.EDT) {
            HintManager.getInstance().showErrorHint(editor, "Can't find file")
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
