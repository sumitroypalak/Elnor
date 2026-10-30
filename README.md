# Elnor 📝

A simple Android note-taking app.

Features

- Create notes
- Edit notes
- Undo and Redo buttons at the top of the note page (a word at a time)
- "Last edited" date and time under the title of a note
- Word and character count under the text of a note, e.g. "12 words · 68 characters" (works for Bengali too)
- Share a note (share button at the top of the note page: send the title and text to WhatsApp, e-mail, messages or any other app)
- Basic formatting (Markdown): while you write, buttons above the keyboard make text **bold** (B), a heading (H: press again for a smaller heading, again to remove it), a bullet list (•) or a numbered list (1.). Select text first to format it, or put the cursor in a word or on a line. Enter inside a list starts the next item, Enter on an empty item ends the list. The formatting is plain text in the note (`**bold**`, `# Heading`, `- item`, `1. item`), so you can also type it yourself; the marks stay visible but dimmed while you edit, and the notes list shows the text without them
- Links in a note are found by themselves: web links (http://, https://, www.), e-mail addresses and phone numbers (also Bengali digits) are shown in colour with an underline. Put the cursor in one and a button above the keyboard appears: Open, Email or Call (Call opens the dialer with the number filled in; you press call yourself). Nothing is stored, the links follow the text
- Copy the text of a note (copy button at the top of the note page: title and text go to the clipboard)
- Tasks (Task tab at the bottom): a to-do list with a check box for each task. The round "+" button adds a task; tap a task to change its title or deadline, or to delete it. A deadline is a whole day (Today, Tomorrow, or any date from the date picker) and can be removed again. Open tasks are listed first, the nearest deadline on top; tasks without a deadline come after them; finished tasks are at the bottom with a line through them. A deadline that has passed is shown in red as "Overdue". The filter chips All / Active / Completed show how many tasks each one has, and "Clear completed" deletes all finished tasks after you confirm. Tasks are not part of Backup & restore yet
- Save text from other apps: use "Share" > Elnor in any app, or select text and choose "Save to Elnor" in the menu that appears. The text becomes a new note (a shared subject, like a web page title, becomes its title) and a short message "Saved to Elnor" appears. Elnor does not open, so you stay in the app you were reading. It also works while the App lock is on, because nothing of your notes is shown
- Side menu (hamburger button): large app name and outlined icon rows for Settings and Trash, with a rounded highlight on the pressed row
- Sort notes (sort button next to "Your notes", remembered): last edited newest or oldest first, or by title A to Z or Z to A. Pinned notes always stay first
- Select many notes: touch and hold a note, then tap more notes. The bar at the top then has Pin / Unpin, Archive and Delete for all selected notes at once, and "Select all" in its three-dot menu. Back or the X ends the selection
- Archive: keep a note without seeing it on the home screen (three-dot menu of a note, or the selection bar). Archived notes are not in the home list, but the search finds them (marked "Archived"). Open them from the side menu > Archive: search there, tap a note to read and edit it (it stays archived), unarchive it or delete it. "Undo" appears right after archiving
- Pin notes: "Pin" in the three-dot menu of a note keeps it at the top of the list ("Unpin" to undo)
- Show notes as a list or as a two-column grid (button next to "Your notes", remembered)
- Delete notes (they go to the Trash first)
- Trash: restore a note, delete it for good, or empty the Trash
- Notes in the Trash are deleted automatically after 7, 30 (the default) or 90 days, counted from the day they were moved there. Choose it in Settings > Trash > "Keep deleted notes for". Choosing a shorter time asks first, because notes that have been in the Trash longer than that are deleted for good at once. A longer time only keeps notes longer
- Backup & restore (Settings > Backup & restore): save all notes (also the ones in the Trash and the Archive) into one `.json` file in a place you choose, and add the notes of such a file back. Restoring only adds notes: nothing that exists is changed, and importing the same file twice makes no copies. The file is not encrypted.
- App lock: unlock with fingerprint, face or the phone's own screen-lock PIN (Settings > App lock). It locks immediately, after 1 minute or after 30 minutes. Elnor has no PIN of its own. While the App lock is on, screenshots and screen recording are blocked and the Recent Apps preview of Elnor is blank, so notes cannot be seen there. The notes database is also kept out of Google cloud backup and adb backup: use Backup & restore to move notes to another phone.

About

Elnor is designed to make note-taking simple and convenient.

License

No license has been specified yet.
