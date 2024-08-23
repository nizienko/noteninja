package notes.link

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination

class BookmarkReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(mdLink, BookmarkPsiReferenceProvider())
    }

    private val mdLink = PlatformPatterns.psiElement()
}

class BookmarkPsiReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        println(element.text + " " + element::class.java)
        if (element is MarkdownLinkDestination) {
            return arrayOf(BookmarkReference(element))
        }
        return arrayOf()
    }
}

class BookmarkReference(private val element: MarkdownLinkDestination) : PsiReferenceBase<PsiElement>(element) {
    override fun resolve(): PsiElement {
        return FakeBookmarkElement(element)
    }
}

class FakeBookmarkElement(private val element: MarkdownLinkDestination) : FakePsiElement() {
    override fun getParent(): PsiElement {
        return element
    }

    override fun navigate(requestFocus: Boolean) {
        println("Opening ${element.text}")
    }
}