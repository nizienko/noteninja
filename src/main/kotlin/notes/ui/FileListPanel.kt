package notes.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.launch
import notes.*
import notes.file.NotesFileType
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.*
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseEvent.BUTTON3
import javax.swing.*


internal val colorWidth: Int
    get() = JBUI.scale(12)

internal fun createNoteItem(note: NoteCard, index: Int, hoveredIndex: Int, isSelected: Boolean): JComponent {
    return BorderLayoutPanel().apply {
        addToLeft(ColorGroupComponent(colorWidth, note))
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
            if (isSelected) {
                background = UIUtil.getListSelectionBackground(true)
            }
            addToCenter(JBLabel(note.name))
        })
    }
}

class ChooseFilePanel(project: Project) : BorderLayoutPanel(), Disposable {
    private val service = project.service<NotesService>()
    private val model: DefaultListModel<NoteCard> = DefaultListModel()
    private val fileList = JBList(model)
    private val notesService = project.service<NotesService>()

    init {
        border = Borders.empty()
        fileList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        fileList.dragEnabled = true
        fileList.dropMode = DropMode.INSERT
        fileList.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean {
                if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) return false

                // Get the drop location
                val dropLocation = support.dropLocation as? JList.DropLocation ?: return false
                val dropIndex = dropLocation.index

                // Don't allow dropping beyond the last item
                if (dropIndex > model.size()) return false

                // Don't allow dropping on the same index
                val sourceIndex = fileList.selectedIndex
                if (sourceIndex == dropIndex || sourceIndex + 1 == dropIndex) return false

                return true
            }

            override fun getSourceActions(c: JComponent): Int {
                return TransferHandler.MOVE
            }

            override fun createTransferable(c: JComponent): Transferable? {
                val list = c as JBList<*>
                val value = list.selectedValue ?: return null
                return StringSelection(value.toString())
            }

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) return false

                val dl = support.dropLocation as? JList.DropLocation ?: return false
                val targetIndex = dl.index

                val sourceIndex = fileList.selectedIndex
                if (sourceIndex < 0) return false

                try {
                    val noteCard = model.getElementAt(sourceIndex)
                    model.remove(sourceIndex)

                    // Calculate the correct insert index
                    val adjustedIndex = if (targetIndex > sourceIndex) targetIndex - 1 else targetIndex
                    model.add(adjustedIndex, noteCard)

                    // Update selection
                    fileList.selectedIndex = adjustedIndex

                    // Update the service with the new order
                    val files = model.elements().toList()
                    service.saveFileList(files)

                    return true
                } catch (e: Exception) {
                    return false
                }
            }

            override fun exportDone(source: JComponent?, data: Transferable?, action: Int) {
                super.exportDone(source, data, action)
                // Ensure the list is repainted after the drag operation
                source?.repaint()
            }
        }


        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && e.button == BUTTON1) {
                    val selectedFile = fileList.selectedValue
                    openFile(project, selectedFile)
                }
                if (e.clickCount == 1 && (e.button == BUTTON1 && e.point.x < colorWidth) || e.button == BUTTON3) {

                    val index = fileList.locationToIndex(e.getPoint())
                    fileList.model.getElementAt(index)?.let {
                        popupColor(listOf(it)).show(RelativePoint(e))
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

                fileList.toolTipText = try {
                    fileList.model.getElementAt(index)?.path?.replace(userHome, "~")
                } catch (_: Throwable) {
                    ""
                }
            }
        })
        fileList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (KeyEvent.VK_ENTER == e.keyCode) {
                    val selectedFile = fileList.selectedValue
                    openFile(project, selectedFile)
                }
                if (KeyEvent.VK_DELETE == e.keyCode) {
                    fileList.selectedValuesList.toList().forEach { selectedValue ->
                        service.removeNote(selectedValue)
                        reLoadFileList()
                    }
                }
                if (KeyEvent.VK_SPACE == e.keyCode) {
                    val selectedFile = fileList.selectedValue
                    val color = colors.next()?.toHex()
                    selectedFile.color = if (selectedFile.color == color) {
                        colors.next()?.toHex()
                    } else {
                        color
                    }
                    repaint()
                }
            }
        })
        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                hoveredIndex = -1
                fileList.repaint()
            }
        })
        fileList.addListSelectionListener {
            lastColor = 0
        }
        fileList.setCellRenderer { list, value, index, isSelected, cellHasFocus ->
            return@setCellRenderer createNoteItem(value, index, hoveredIndex, isSelected)
        }
        val popupActions = mutableListOf<AnAction>()
        val decoratedList = ToolbarDecorator.createDecorator(fileList)
            .addExtraAction(object : DumbAwareAction(AllIcons.Actions.Colors) {
                override fun actionPerformed(e: AnActionEvent) {
                    val notes = fileList.selectedValuesList
                    val component = e.inputEvent?.source as? JComponent ?: return
                    val point = component.locationOnScreen.let {
                        Point(it.x + component.width / 2, it.y + component.height / 2)
                    }
                    popupColor(notes).show(RelativePoint(point))
                }

                override fun update(e: AnActionEvent) {
                    super.update(e)
                    e.presentation.text = "Set Color"
                    e.presentation.isEnabledAndVisible = fileList.selectedValue != null
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
            }.also { popupActions.add(it) })
            .addExtraAction(object : DumbAwareAction(AllIcons.General.Delete) {
                override fun actionPerformed(e: AnActionEvent) {
                    fileList.selectedValuesList.toList().forEach { selectedValue ->
                        service.removeNote(selectedValue)
                        reLoadFileList()
                    }
                }

                override fun update(e: AnActionEvent) {
                    super.update(e)
                    e.presentation.text = "Delete"
                    e.presentation.isEnabledAndVisible = fileList.selectedValue != null
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
            }.also { popupActions.add(it) })
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
                        service.addNote(note)
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
            .addExtraAction(object : DumbAwareAction(AllIcons.Actions.Search) {
                override fun actionPerformed(e: AnActionEvent) {
                    service.goto(NinjaState.SEARCH)
                }
                override fun update(e: AnActionEvent) {
                    super.update(e)
                    e.presentation.text = "Search"
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
            })
            .setAddActionName("Create New Note")
            .setAddAction { a ->
                val popup = textFieldPopup("new note") {
                    try {
                        val note = notesService.createNewFile(it)
                        model.addElement(note)
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
            .disableRemoveAction()
            .disableUpAction()
            .disableDownAction()
            .setEditAction {
                val selectedFile = fileList.selectedValue
                openFile(project, selectedFile)
            }
            .createPanel()

        decoratedList.border = Borders.empty()
        addToCenter(decoratedList)
        reLoadFileList()
    }

    private val stateJob = service.scope.launch {
        service.stateFlow.collect {
            if (it == NinjaState.FILES) {
                reLoadFileList()
                setFocus()
            }
        }
    }

    private fun openFile(project: Project, noteCard: NoteCard) {
        notesService.scope.launch {
            try {
                notesService.openFile(noteCard)
            } catch (e: Throwable) {
                e.printStackTrace()
                NotificationsManager.getNotificationsManager().showNotification(
                    Notification("noteninja", "Can't open file ${noteCard.path}", NotificationType.ERROR), project
                )
            }
        }
    }

    private fun reLoadFileList() {
        val lastFile = fileList.selectedValue?.path
        model.clear()
        service.noteCards().forEach {
            model.addElement(it)
            if (it.path == lastFile) {
                fileList.setSelectedValue(it, true)
            }
        }
    }

    private fun setFocus() {
        fileList.requestFocus()
    }

    override fun dispose() {
        stateJob.cancel()
    }
}

fun popupColor(notes: List<NoteCard>): JBPopup {
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
            notes.forEach { note ->
                if (note.color == chosenColor && notes.size == 1) {
                    note.color = null
                } else {
                    note.color = list.selectedValue.toHex()
                }
            }
        }).createPopup()
}

private var lastColor = 0

fun List<Color>.next(): Color? {
    lastColor = (lastColor + 1) % size + 1
    return getOrNull(lastColor)
}

private val colors = listOf(
    "#e81416",
    "#ffa500",
    "#faeb36",
    "#79c314",
    "#487de7",
    "#4b369d",
    "#70369d",
).map { it.parseColor() }

fun textFieldPopup(text: String, action: (String) -> Unit): JBPopup {
    val textField = JBTextField()
    val button = object : JButton("↵") {
        override fun getPreferredSize(): Dimension {
            val fontMetrics = getFontMetrics(font)
            val textWidth = fontMetrics.stringWidth(text) + JBUI.scale(10)
            return Dimension(textWidth, super.getPreferredSize().height)
        }
    }
    textField.text = text
    textField.selectAll()
    val panel = BorderLayoutPanel()
    panel.addToCenter(textField)
    panel.addToRight(button)
    val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, textField)
        .setRequestFocus(true)
        .setMinSize(Dimension(200, 0))
        .createPopup()
    button.addActionListener {
        action(textField.text)
        popup.closeOk(null)
    }
    textField.addKeyListener(object : KeyAdapter() {
        override fun keyTyped(e: KeyEvent?) {
            if (e == null) return
            if (e.extendedKeyCode == KeyEvent.VK_ENTER) {
                action(textField.text)
                popup.closeOk(null)
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
            g.color = it.parseColor()
            g.fillRect(0, 0, colorWidth, height)
        }
    }
}