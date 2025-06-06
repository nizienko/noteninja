package notes

sealed class NoteAction {
    class ScrollDown : NoteAction()
    class ScrollToElement(val topic: Topic) : NoteAction()
    class FindKeyword(val text: String) : NoteAction()
    class InsertText(val text: String) : NoteAction()
    class RefoldLinks : NoteAction()
    class RequestFocusOnEditor : NoteAction()
}