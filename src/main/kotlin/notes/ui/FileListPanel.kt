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
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.launch
import notes.*
import notes.file.NotesFileType
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseMotionAdapter
import javax.swing.DefaultListModel
import javax.swing.JOptionPane
import javax.swing.JPanel


class ChooseFilePanel(project: Project) : BorderLayoutPanel() {
    private val model: DefaultListModel<NoteCard> = DefaultListModel()
    private val fileList = JBList(model)
    private val notesService = project.service<NotesService>()
    private val filesState = service<FilesState>()
    private val colorWidth = 8

    init {
        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && e.button == BUTTON1) {
                    val selectedFile = fileList.selectedValue
                    openFile(project, selectedFile)
                }
                if (e.clickCount == 1 && e.button == BUTTON1 && e.point.x < colorWidth) {

                    val index = fileList.locationToIndex(e.getPoint())
                    fileList.model.getElementAt(index)?.let {
                        showPopup(e, it)
                    }
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
            return@setCellRenderer BorderLayoutPanel().apply {
                addToLeft(ColorGroupComponent(colorWidth, value))
                border = Borders.compound(
                    Borders.empty(1, 2),
                )
                addToCenter(BorderLayoutPanel().apply {
                    border = Borders.compound(
                        Borders.empty(5, 8),
                    )
                    background = if (index == hoveredIndex) {
                        UIUtil.getListSelectionBackground(false)
                    } else {
                        UIUtil.getPanelBackground()
                    }
                    if (cellHasFocus) {
                        background = UIUtil.getListSelectionBackground(true)
                    }
                    addToCenter(JBLabel(value.name))
                })
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
            .setMoveDownAction { moveFile(1) }
            .setMoveUpAction { moveFile(-1) }
            .createPanel()

        addToCenter(decoratedList)
    }

    private fun moveFile(n: Int) {
        val selectedIndex: Int = fileList.selectedIndex
        val selectedValue = fileList.selectedValue
        if (selectedIndex != -1) {
            model.remove(selectedIndex)
            model.add(selectedIndex + n, selectedValue)
            val files = model.elements().toList()
            filesState.setFileList(files)
            fileList.setSelectedValue(selectedValue, true)
        }
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

    fun showPopup(event: MouseEvent, note: NoteCard) {
        val model = DefaultListModel<Color>()
        colors.forEach { model.addElement(it) }
        val list = JBList(model).apply {
            setCellRenderer { _, value, _, _, focus ->
                return@setCellRenderer JPanel().apply {
                    background = if (focus) {
                        value
                    } else {
                        value.darker()
                    }
                }
            }
        }
        val popup = PopupChooserBuilder(list)
            .setItemChosenCallback(Runnable {
                val chosenColor = list.selectedValue.toHex()
                if (note.color == chosenColor) {
                    note.color = null
                } else {
                    note.color = list.selectedValue.toHex()
                }
            }).createPopup()
        popup.show(RelativePoint(event))

    }
}

private val colors = listOf(
    parseColor("#e81416"),
    parseColor("#ffa500"),
    parseColor("#faeb36"),
    parseColor("#79c314"),
    parseColor("#487de7"),
    parseColor("#4b369d"),
    parseColor("#70369d"),
)

class ColorGroupComponent(private val colorWidth: Int, private val note: NoteCard) : JPanel(true) {
    init {
        bounds.width = colorWidth
        preferredSize.width = colorWidth
        size.width = colorWidth
        minimumSize.width = colorWidth
    }

    override fun paintComponent(g: java.awt.Graphics) {
        super.paintComponent(g)
        note.color?.let {
            g.color = parseColor(it)
            g.fillRect(0, 0, colorWidth, height)
        }
    }
}