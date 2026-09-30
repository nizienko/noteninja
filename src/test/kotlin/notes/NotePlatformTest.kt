package notes

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.components.JBList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import notes.file.NotesFileType
import notes.folding.LinksFoldingBuilder
import notes.symbols.LinkEditorListener
import notes.ui.ChooseFilePanel
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownInlineLink
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import java.awt.Container
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.SwingUtilities
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

class NotePlatformTest {
    @Test fun `unsaved document search uses IDE text`() {
        Checks().apply { name = "testUnsavedDocumentWinsOverDiskDuringRefresh" }.runBare()
    }

    @Test fun `Markdown PSI recognizes generated references and folding ranges`() {
        Checks().apply { name = "testGeneratedReferencesAreMarkdownLinksAndFoldAtOffsetSuffix" }.runBare()
    }

    @Test fun `list refresh applies on EDT preserves selection and stops after disposal`() {
        Checks().apply { name = "testListRefreshLifecycle" }.runBare()
    }

    @Test fun `modified click navigates to escaped filename and saved offset`() {
        Checks().apply { name = "testModifiedClickNavigatesToReference" }.runBare()
    }

    class Checks : BasePlatformTestCase() {
        override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

        fun testModifiedClickNavigatesToReference() {
            val file = myFixture.tempDirFixture.createFile("my [日本語](file):2.txt", "target content")
            val text = NoteReferences.format(file.name, file.path, 3)
            myFixture.configureByText(NotesFileType.INSTANCE, text)
            myFixture.editor.caretModel.moveToOffset(3)
            val mask = if (System.getProperty("os.name").lowercase().contains("mac")) InputEvent.META_DOWN_MASK
                else InputEvent.CTRL_DOWN_MASK
            val event = MouseEvent(myFixture.editor.contentComponent, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), mask, 1, 1, 1, false, MouseEvent.BUTTON1)
            LinkEditorListener().mouseClicked(EditorMouseEvent(myFixture.editor, event, EditorMouseEventArea.EDITING_AREA))
            val manager = FileEditorManager.getInstance(project)
            PlatformTestUtil.waitWithEventsDispatching("reference navigation", {
                manager.selectedTextEditor?.virtualFile?.path == file.path
                    && manager.selectedTextEditor?.caretModel?.offset == 3
            }, 10)
            manager.openFiles.forEach { manager.closeFile(it) }
        }
        fun testListRefreshLifecycle() {
            val calls = AtomicInteger()
            val staleGate = CompletableDeferred<Unit>()
            val disposalGate = CompletableDeferred<Unit>()
            val staleFinished = CompletableDeferred<Unit>()
            val disposalFinished = CompletableDeferred<Unit>()
            val first = NoteCard("First", "/test/first.notes")
            val second = NoteCard("Second", "/test/second.notes")
            val panel = ChooseFilePanel(project) {
                assertFalse(SwingUtilities.isEventDispatchThread())
                when (calls.incrementAndGet()) {
                    1 -> {
                        withContext(NonCancellable) { staleGate.await() }
                        staleFinished.complete(Unit)
                        setOf(first)
                    }
                    2 -> setOf(second)
                    3 -> setOf(first, second)
                    else -> {
                        withContext(NonCancellable) { disposalGate.await() }
                        disposalFinished.complete(Unit)
                        emptySet()
                    }
                }
            }
            fun findList(container: Container): JBList<*>? {
                container.components.forEach { child ->
                    if (child is JBList<*>) return child
                    if (child is Container) findList(child)?.let { return it }
                }
                return null
            }
            val list = findList(panel)!!
            var changes = 0
            list.model.addListDataListener(object : ListDataListener {
                private fun changed() { assertTrue(SwingUtilities.isEventDispatchThread()); changes++ }
                override fun intervalAdded(e: ListDataEvent) = changed()
                override fun intervalRemoved(e: ListDataEvent) = changed()
                override fun contentsChanged(e: ListDataEvent) = changed()
            })
            fun waitFor(condition: () -> Boolean) = PlatformTestUtil.waitWithEventsDispatching("list refresh", condition, 10)
            val notesService = project.service<NotesService>()
            try {
                waitFor { calls.get() == 1 }
                notesService.goto(NinjaState.FILES)
                waitFor { list.model.size == 1 && list.model.getElementAt(0) == second }
                list.selectedIndex = 0
                notesService.goto(NinjaState.OPENED_NOTE)
                PlatformTestUtil.waitForAlarm(50)
                runBlocking { notesService.back() }
                waitFor { list.model.size == 2 }
                assertEquals(second, list.selectedValue)
                staleGate.complete(Unit)
                waitFor { staleFinished.isCompleted }
                assertEquals(2, list.model.size)
                notesService.goto(NinjaState.OPENED_NOTE)
                PlatformTestUtil.waitForAlarm(50)
                runBlocking { notesService.back() }
                waitFor { calls.get() == 4 }
                panel.dispose()
                val before = changes
                disposalGate.complete(Unit)
                waitFor { disposalFinished.isCompleted }
                assertEquals(before, changes)
                assertEquals(2, list.model.size)
            } finally {
                staleGate.complete(Unit)
                disposalGate.complete(Unit)
                panel.dispose()
            }
        }
        fun testUnsavedDocumentWinsOverDiskDuringRefresh() {
            val file = createTempDirectory("unsaved-note").resolve("note.notes").toFile()
            file.writeText("bravo")
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
            val note = NoteCard("Note", file.path)
            val index = NoteIndexService()
            runBlocking { index.refresh { setOf(note) } }
            assertEquals(note.path, index.search("bravo").single().note.path)
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!
            WriteAction.run<RuntimeException> { document.setText("delta") }
            assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
            runBlocking { index.refresh { setOf(note) } }
            assertTrue(index.search("bravo").isEmpty())
            assertEquals(note.path, index.search("delta").single().note.path)
            assertEquals("bravo", file.readText())
            // Restore before fixture teardown so the test never leaves an unsaved external document.
            WriteAction.run<RuntimeException> { document.setText("bravo") }
            FileDocumentManager.getInstance().saveDocument(document)
        }

        fun testGeneratedReferencesAreMarkdownLinksAndFoldAtOffsetSuffix() {
            val names = listOf("foo-bar.kt", "test2.kt", "my file.kt", "日本語.kt", "a[b](c):d.kt")
            val references = names.map { NoteReferences.format(it, "/project/$it", 42) }
            myFixture.configureByText(NotesFileType.INSTANCE, references.joinToString("\n\n"))
            val links = PsiTreeUtil.findChildrenOfType(myFixture.file, MarkdownInlineLink::class.java).toList()
            assertEquals(names.size, links.size)
            links.forEach { assertNotNull(NoteReferences.parse(it.text)) }
            val folds = LinksFoldingBuilder().buildFoldRegions(myFixture.file, myFixture.editor.document, false)
            assertEquals(names.size, folds.size)
            folds.forEach { fold ->
                val folded = myFixture.editor.document.getText(fold.range)
                assertTrue(folded.startsWith(":42]"))
            }
        }
    }
}
