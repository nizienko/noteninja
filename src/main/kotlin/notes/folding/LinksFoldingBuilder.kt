package notes.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import notes.file.NotesFileType
import notes.NoteReferences

class LinksFoldingBuilder : FoldingBuilderEx() {
    companion object {
        const val LINK_PLACEHOLDER = "]"
    }
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (root.containingFile.virtualFile.fileType != NotesFileType.INSTANCE) return emptyArray()
        val descriptors = mutableListOf<FoldingDescriptor>()
        val node = root.node
        if (node != null) {
            addDescriptors(node, descriptors)
        }
        return descriptors.toTypedArray()
    }

    private fun addDescriptors(node: ASTNode, descriptors: MutableList<FoldingDescriptor>) {
        val element = node.psi
        val reference = NoteReferences.parse(element.text ?: "")
        if (reference != null) {
            val showNameLength = reference.suffixStart
            descriptors.add(FoldingDescriptor(element, element.textRange.let { TextRange.create(it.startOffset + showNameLength, it.endOffset) }))
            return
        }
        for (child in node.getChildren(null)) {
            addDescriptors(child, descriptors)
        }
    }

    override fun getPlaceholderText(node: ASTNode): String {
        return LINK_PLACEHOLDER
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return true
    }
}
