package notes

sealed class NoteAction {
    class ScrollDown : NoteAction()
    class ScrollToElement(val topic: Topic) : NoteAction()
    class InsertText(val text: String) : NoteAction()
    class RefoldLinks : NoteAction()
    class RequestFocusOnEditor : NoteAction()
}