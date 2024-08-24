@file:Suppress("UnstableApiUsage")

package notes.symbols

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import notes.linkRegex


fun PsiElement.isFileLink(): Boolean {
    return !linkRegex.matches(text).not()
}

class LinkPsiSymbolDeclarationProvider : PsiSymbolDeclarationProvider {
    override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
        if (element.isFileLink()) {
            return listOf(LinkPsiSymbolDeclaration(element))
        }
        return emptyList()
    }
}

class LinkPsiSymbolDeclaration(private val element: PsiElement) : PsiSymbolDeclaration {
    override fun getDeclaringElement(): PsiElement {
        return element
    }

    override fun getRangeInDeclaringElement(): TextRange {
        return TextRange(0, element.textLength)
    }

    override fun getSymbol(): Symbol {
        return Pointer.fileRangePointer(element.containingFile, element.textRange) { file, range ->
            LinkTextSymbol(file, range, element.text)
        }.dereference() ?: error("Failed to create symbol")
    }
}

data class LinkTextSymbol(val file: PsiFile, val range: TextRange, val text: String) : Symbol {
    override fun createPointer(): Pointer<out Symbol?> {
        return Pointer.fileRangePointer(file, range) { file, range ->
            LinkTextSymbol(file, range, text)
        }
    }
}