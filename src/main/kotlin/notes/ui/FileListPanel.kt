package notes.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.launch
import notes.*
import notes.file.NotesFileType
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.event.*
import java.awt.event.MouseEvent.BUTTON1
import javax.swing.*


class ChooseFilePanel(project: Project) : BorderLayoutPanel() {
    private val model: DefaultListModel<NoteCard> = DefaultListModel()
    private val fileList = JBList(model)
    private val notesService = project.service<NotesService>()
    private val filesState = service<FilesState>()
    private val colorWidth = 8

    init {
        border = Borders.empty()
        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && e.button == BUTTON1) {
                    val selectedFile = fileList.selectedValue
                    openFile(project, selectedFile)
                }
                if (e.clickCount == 1 && e.button == BUTTON1 && e.point.x < colorWidth) {

                    val index = fileList.locationToIndex(e.getPoint())
                    fileList.model.getElementAt(index)?.let {
                        popupColor(it).show(RelativePoint(e))
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
                    val descriptor = FileChooserDescriptor(true, false, false, false, false, true)
                        .withFileFilter { it.fileType == NotesFileType.INSTANCE }

                    val files =
                        FileChooser.chooseFiles(
                            descriptor, project, null
                        )
                    files.forEach { file ->
                        val path = file.path
                        val note = NoteCard(file.name, path)
                        model.addElement(note)
                        filesState.addFile(note)

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
            .addExtraAction(object : DumbAwareAction(AllIcons.Actions.Colors) {
                override fun actionPerformed(e: AnActionEvent) {
                    val note = fileList.selectedValue ?: return
                    val component = e.inputEvent?.source as? JComponent ?: return
                    val point = component.locationOnScreen.let {
                        Point(it.x + component.width / 2, it.y + component.height / 2)
                    }
                    popupColor(note).show(RelativePoint(point))
                }

                override fun update(e: AnActionEvent) {
                    super.update(e)
                    e.presentation.text = "Set Color"
                    e.presentation.isEnabledAndVisible = fileList.selectedValue != null
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
            })
            .setAddActionName("Create New Note")
            .setAddAction { a ->
                val popup = newNotePopup {
                    try {
                        val note = notesService.createNewFile(it)
                        model.addElement(note)
                        filesState.addFile(note)
                        openFile(project, note)

                    } catch (e: Throwable) {
                        NotificationsManager.getNotificationsManager().showNotification(
                            Notification("noteninja", "Can't create new file", NotificationType.ERROR), project
                        )
                    }
                }
                val actionComponent = a.contextComponent ?: return@setAddAction
                popup.show(RelativePoint(actionComponent, Point(0, 0)))

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

        decoratedList.border = Borders.empty()
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
                    Notification("noteninja", "Can't open file ${noteCard.path}", NotificationType.ERROR), project
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

fun popupColor(note: NoteCard): JBPopup {
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
    return PopupChooserBuilder(list)
        .setItemChosenCallback(Runnable {
            val chosenColor = list.selectedValue.toHex()
            if (note.color == chosenColor) {
                note.color = null
            } else {
                note.color = list.selectedValue.toHex()
            }
        }).createPopup()
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

fun newNotePopup(action: (String) -> Unit): JBPopup {
    val textField = JBTextField()
    val panel = BorderLayoutPanel()
    panel.addToCenter(textField)
    val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, textField)
        .setRequestFocus(true)
        .setMinSize(Dimension(200, 0))
        .createPopup()

    textField.addKeyListener(object : KeyAdapter() {
        override fun keyTyped(e: KeyEvent?) {
            if (e == null) return
            if (e.extendedKeyCode == KeyEvent.VK_ENTER) {
                popup.closeOk(null)
                action(textField.text)
            }
        }
    })
    return popup
}

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