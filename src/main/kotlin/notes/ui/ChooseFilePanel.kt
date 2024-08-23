package notes.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.launch
import notes.FilesState
import notes.NoteCard
import notes.NotesService
import notes.file.NotesFileType
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseMotionAdapter
import java.io.File
import javax.swing.DefaultListModel
import javax.swing.JOptionPane


class ChooseFilePanel(project: Project) : BorderLayoutPanel() {
    private val model: DefaultListModel<NoteCard> = DefaultListModel()
    private val fileList = JBList(model)
    private val notesService = project.service<NotesService>()
    private val filesState = service<FilesState>()

    init {
        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && e.button == BUTTON1) {
                    val selectedFile = fileList.selectedValue
                    openFile(project, selectedFile)
                }
            }
        })
        var hoveredIndex = -1
        val userHome = System.getProperty("user.home")
        fileList.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val index = fileList.locationToIndex(e.getPoint())
                if (index != hoveredIndex) {
                    hoveredIndex = index
                    fileList.repaint()
                }
                fileList.toolTipText = fileList.model.getElementAt(index)?.path?.replace(userHome, "~")
            }
        })
        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                hoveredIndex = -1;
                fileList.repaint();
            }
        })
        fileList.setCellRenderer { list, value, index, isSelected, cellHasFocus ->
            val name = value.name
            val html = buildString {
                append("<html>")
//                if (index == hoveredIndex) append("<u>")
//                append("<h3>")
                append(name)
//                append("</h3>")
//                if (index == hoveredIndex) {
//                    append(" ")
//                    append(value)
//                }
//                if (index == hoveredIndex) append("</u>")
                append("</html>")
            }
            return@setCellRenderer BorderLayoutPanel().apply {
                border = Borders.compound(
                    Borders.empty(10, 5),
                )

                background = if (index == hoveredIndex) {
                    UIUtil.getListSelectionBackground(false)
                } else {
                    UIUtil.getPanelBackground()
                }
                if (cellHasFocus) {
                    background = UIUtil.getListSelectionBackground(true)
                }
                addToCenter(JBLabel(html))
            }
        }
        val decoratedList = ToolbarDecorator.createDecorator(fileList)
            .addExtraAction(object : DumbAwareAction(AllIcons.General.OpenDisk) {
                override fun actionPerformed(e: AnActionEvent) {
                    AllIcons.General.ArrowDown
                    val file =
                        FileChooser.chooseFile(
                            FileChooserDescriptorFactory
                                .createSingleFileDescriptor(NotesFileType.INSTANCE), project, null
                        )
                    if (file != null) {
                        val path = file.path
                        val note = NoteCard(file.name, path)
                        model.addElement(note)
                        filesState.addFile(note)
                        openFile(project, note)
                    }
                }

                override fun update(e: AnActionEvent) {
                    super.update(e)
                    e.presentation.text = "Open File"
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
            })
            .setAddActionName("Create New Note")
            .setAddAction { _ ->
                try {
                    val noteName = JOptionPane.showInputDialog("Enter Note name:")
                    if (noteName != null && noteName.trim { it <= ' ' }.isNotEmpty()) {
                        val note = notesService.createNewFile(noteName)
                        model.addElement(note)
                        filesState.addFile(note)
                        openFile(project, note)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    NotificationsManager.getNotificationsManager().showNotification(
                        Notification("", "Can't create new file", NotificationType.ERROR), project
                    )
                }
            }
            .setRemoveAction {
                val selectedIndex: Int = fileList.selectedIndex
                val selectedValue = fileList.selectedValue
                if (selectedIndex != -1) {
                    model.remove(selectedIndex)
                    filesState.removeFile(selectedValue)
                }
            }
            .disableUpAction()
            .disableDownAction()
            .createPanel()

        addToCenter(decoratedList)
    }

    private fun openFile(project: Project, noteCard: NoteCard) {
        notesService.scope.launch {
            try {
                notesService.loadFile(noteCard)
                notesService.reloadCurrentDocument()
            } catch (e: Throwable) {
                e.printStackTrace()
                NotificationsManager.getNotificationsManager().showNotification(
                    Notification("", "Can't open file ${noteCard.path}", NotificationType.ERROR), project
                )
            }
        }
    }

    fun reLoadFileList() {
        model.clear()
        filesState.list().forEach {
            model.addElement(it)
        }
    }
}