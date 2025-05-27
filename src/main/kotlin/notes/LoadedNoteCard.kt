package notes

import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.io.File


class LoadedNoteCard(val noteCard: NoteCard) {
    val file: File = File(noteCard.path)
    fun virtualFile() : VirtualFile{
        return VfsUtil.findFileByIoFile(file, true) ?: throw IllegalStateException("Can't find notes ${file.path}")
    }

    suspend fun getPsiFile(project: Project): PsiFile {
        val virtualFile = virtualFile()
        return readAction { PsiManager.getInstance(project).findFile(virtualFile) }
            ?: throw IllegalStateException("Can't find notes psi file")
    }

    suspend fun getDocument() : Document {
        val virtualFile = virtualFile()
        return readAction { virtualFile.findDocument() ?: throw IllegalStateException("Can't find document") }
    }
}

enum class NinjaState {
    LOADING,
    FILES,
    SEARCH,
    OPENED_NOTE
}