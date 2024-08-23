@file:Suppress("UnstableApiUsage")

package notes.symbols

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLink
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkText


private val regex = """\[([A-z.]*):(\d+)]""".toRegex()
fun MarkdownLinkText.isFileLink(): Boolean {
    if ((parent as? MarkdownLink)?.linkDestination?.text?.isEmpty() != false) return false
    if (regex.matches(text).not()) return false
    return true
}

class LinkPsiSymbolDeclarationProvider: PsiSymbolDeclarationProvider {
    override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
        if (element is MarkdownLinkText && element.isFileLink()) {
            return listOf(LinkPsiSymbolDeclaration(element))
        }
        return emptyList()
    }
}

fun createPointer(element: MarkdownLinkText): Pointer<LinkTextSymbol> {
    return Pointer.fileRangePointer(element.containingFile, element.textRange) { file, range ->
        LinkTextSymbol(file, range, element.text)
    }
}

class LinkPsiSymbolDeclaration(private val element: MarkdownLinkText): PsiSymbolDeclaration {
    override fun getDeclaringElement(): PsiElement {
        return element
    }

    override fun getRangeInDeclaringElement(): TextRange {
        return TextRange(0, element.textLength)
    }

    override fun getSymbol(): Symbol {
        return createPointer(element).dereference() ?: error("Failed to create symbol")
    }
}

data class LinkTextSymbol(val file: PsiFile, val range: TextRange, val text: String): Symbol {
    override fun createPointer(): Pointer<out Symbol?> {
        return Pointer.fileRangePointer(file, range) { file, range ->
            LinkTextSymbol(file, range, text)
        }
    }
}