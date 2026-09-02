package notes.link

import com.intellij.patterns.PlatformPatterns
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import notes.file.NotesFileType
import notes.linkRegex
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination

class BookmarkReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(mdLink, BookmarkPsiReferenceProvider())
    }

    private val mdLink = PlatformPatterns.psiElement()
}

class BookmarkPsiReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element is MarkdownLinkDestination &&
            element.containingFile.fileType == NotesFileType.INSTANCE &&
            linkRegex.matches(element.parent.text)
        ) {
            return arrayOf(BookmarkReference(element))
        }
        return arrayOf()
    }
}

class BookmarkReference(private val element: MarkdownLinkDestination) : PsiReferenceBase<PsiElement>(element) {
    override fun resolve(): PsiElement? {
        val path = linkRegex.matchEntire(element.parent.text)?.groupValues?.get(3) ?: return null
        val target = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
        return PsiManager.getInstance(element.project).findFile(target)
    }
}
