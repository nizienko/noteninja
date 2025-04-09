package notes

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.PsiManager
import java.io.File


class LoadedNoteCard(val noteCard: NoteCard) {
    val file: File by lazy { File(noteCard.path) }
    val virtualFile by lazy {
        VfsUtil.findFileByIoFile(file, true) ?: throw IllegalStateException("Can't find notes file")
    }

    suspend fun getPsiFile(project: Project) =
        readAction { PsiManager.getInstance(project).findFile(virtualFile) }
            ?: throw IllegalStateException("Can't find notes psi file")

    suspend fun getDocument() =
        readAction { virtualFile.findDocument() ?: throw IllegalStateException("Can't find document") }
}

enum class NinjaState {
    LOADING,
    FILES,
    OPENED_NOTE
}