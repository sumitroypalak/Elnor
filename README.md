# Elnor 📝

A simple Android note-taking app.

Features

- Create notes
- Edit notes. The keyboard opens at once only for a new note; an existing note opens without the keyboard, so it can be read in peace. Tap the text to start typing
- Undo and Redo buttons at the top of the note page (a word at a time)
- "Last edited" date and time under the title of a note
- Word and character count under the text of a note, e.g. "12 words · 68 characters" (works for Bengali too)
- Share a note (share button at the top of the note page: send the title and text to WhatsApp, e-mail, messages or any other app)
- Basic formatting (Markdown): while you write, buttons above the keyboard make text **bold** (B), a heading (H: press again for a smaller heading, again to remove it), a bullet list (•) or a numbered list (1.). Select text first to format it, or put the cursor in a word or on a line. Enter inside a list starts the next item, Enter on an empty item ends the list. The formatting is plain text in the note (`**bold**`, `# Heading`, `- item`, `1. item`), so you can also type it yourself; the marks stay visible but dimmed while you edit, and the notes list shows the text without them
- Links in a note are found by themselves: web links (http://, https://, www.), e-mail addresses and phone numbers (also Bengali digits) are shown in colour with an underline. Put the cursor in one and a button above the keyboard appears: Open, Email or Call (Call opens the dialer with the number filled in; you press call yourself). Nothing is stored, the links follow the text
- Find in a note (search button at the top of the note page): type a word and every place of it in the text is highlighted in yellow, the chosen one in orange, with a counter like "2/5". The up and down buttons (or the Search key of the keyboard) move to the previous and the next place and put the cursor there. Upper and lower case do not matter, and Bengali works (also the two ways of typing য়). Only the text of the note is searched, not the title. Back or the X closes the bar. Nothing is stored or changed in the note
- Save a note as a file (share button at the top of the note page > "Save as .txt" or "Save as .md"): Android's Save screen asks where to keep it. The file name is made from the title (or the first line), and the file is UTF-8, so Bengali works. A .txt file has the title and the text without the formatting marks; a .md file has the note exactly as written, with the title as a "#" heading on top. Nothing in the note changes. The file is not encrypted, so it is outside the App lock
- Copy the text of a note (copy button at the top of the note page: title and text go to the clipboard)
- Tasks (Task tab at the bottom): a to-do list with a check box for each task. The round "+" button adds a task; tap a task to change its title or deadline, or to delete it. A deadline is a whole day (Today, Tomorrow, or any date from the date picker) and can be removed again. Open tasks are listed first, the nearest deadline on top; tasks without a deadline come after them; finished tasks are at the bottom with a line through them. Checking a task off gives a light vibration (when the phone's touch feedback is on) and a message with "Undo" at the bottom, so a task checked by mistake can be opened again with one tap; unchecking a task needs neither. A deadline that has passed is shown in red as "Overdue". The filter chips All / Active / Completed show how many tasks each one has, and "Clear completed" deletes all finished tasks after you confirm. Tasks are not part of Backup & restore yet
- Save text from other apps: use "Share" > Elnor in any app, or select text and choose "Save to Elnor" in the menu that appears. The text becomes a new note (a shared subject, like a web page title, becomes its title) and a short message "Saved to Elnor" appears. Elnor does not open, so you stay in the app you were reading. It also works while the App lock is on, because nothing of your notes is shown
- Font size (Settings > Appearance > Font size): Small, Medium (the default) or Large for all text in the app, remembered. Each name is shown in its own size in the dialog, and the change is seen at once. Headings in notes and the space between lines grow with it, which helps when reading Bengali. It is added on top of the phone's own font size setting
- Dynamic color (Settings > Appearance > Dynamic color, Android 12 and newer): the colors of the app follow the colors of the phone's wallpaper, in the light and in the dark theme. The note cards and the bottom bar take their colors from it too, and the text on them stays easy to read on any wallpaper. It is off by default, so an update never changes the look by itself. On older Android versions the switch is not shown and the Elnor colors are always used
- Side menu (hamburger button): large app name and outlined icon rows for Settings and Trash, with a rounded highlight on the pressed row
- Sort notes (sort button next to "Your notes", remembered): last edited newest or oldest first, or by title A to Z or Z to A. Pinned notes always stay first
- Select many notes: touch and hold a note, then tap more notes. The bar at the top then has Pin / Unpin, Archive and Delete for all selected notes at once, and "Select all" in its three-dot menu. Back or the X ends the selection
- Archive: keep a note without seeing it on the home screen (three-dot menu of a note, or the selection bar). Archived notes are not in the home list, but the search finds them (marked "Archived"). Open them from the side menu > Archive: search there, tap a note to read and edit it (it stays archived), unarchive it or delete it. "Undo" appears right after archiving
- Pin notes: "Pin" in the three-dot menu of a note keeps it at the top of the list ("Unpin" to undo)
- Show notes as a list or as a two-column grid (button next to "Your notes", remembered)
- Smooth screen changes: Settings, Archive, Trash, Backup and App lock open with a small slide to the left and a fade (and go back the other way). A note opens with a small rise and fade, and the Note / Task / Collection tabs, the top bar of the selection, the empty and loading states and the list / grid switch fade into each other. The Find bar, the formatting buttons, the Open / Email / Call button and the recent searches slide or fade in and out, and a task that is checked off moves to its new place. All of it is 180 to 250 milliseconds, set in one file (Motion.kt), and it follows the phone's "Remove animations" setting. The lock screen only fades away when the app is unlocked, it never fades in, so the notes are never seen for a moment when the app locks
- Smooth list animation: when a note is added, deleted, archived, pinned or the sort order or the search changes, the cards fade in and out and slide to their new place instead of jumping (both in the list and in the grid)
- Delete notes (they go to the Trash first)
- Trash: restore a note, delete it for good, or empty the Trash
- Each note in the Trash shows how long is left before it is deleted for good ("Deleted for good in 12 days", "tomorrow" or "today"). With 3 days or less left the line is red, so a note can still be restored in time
- Notes in the Trash are deleted automatically after 7, 30 (the default) or 90 days, counted from the day they were moved there. Choose it in Settings > Trash > "Keep deleted notes for". Choosing a shorter time asks first, because notes that have been in the Trash longer than that are deleted for good at once. A longer time only keeps notes longer
- Backup & restore (Settings > Backup & restore): save all notes (also the ones in the Trash and the Archive) into one `.json` file in a place you choose, and add the notes of such a file back. Restoring only adds notes: nothing that exists is changed, and importing the same file twice makes no copies. The file is not encrypted.
- App lock: unlock with fingerprint, face or the phone's own screen-lock PIN (Settings > App lock). It locks immediately, after 1 minute or after 30 minutes. Elnor has no PIN of its own. While the App lock is on, screenshots and screen recording are blocked and the Recent Apps preview of Elnor is blank, so notes cannot be seen there. The notes database is also kept out of Google cloud backup and adb backup: use Backup & restore to move notes to another phone.

About

Elnor is designed to make note-taking simple and convenient.

License

No license has been specified yet.
