# Elnor 📝

A simple Android note-taking app.

Features

- Create notes
- Edit notes
- Undo and Redo buttons at the top of the note page (a word at a time)
- "Last edited" date and time under the title of a note
- Word and character count under the text of a note, e.g. "12 words · 68 characters" (works for Bengali too)
- Share a note (share button at the top of the note page: send the title and text to WhatsApp, e-mail, messages or any other app)
- Links in a note are found by themselves: web links (http://, https://, www.), e-mail addresses and phone numbers (also Bengali digits) are shown in colour with an underline. Put the cursor in one and a button above the keyboard appears: Open, Email or Call (Call opens the dialer with the number filled in; you press call yourself). Nothing is stored, the links follow the text
- Copy the text of a note (copy button at the top of the note page: title and text go to the clipboard)
- Side menu (hamburger button): large app name and outlined icon rows for Settings and Trash, with a rounded highlight on the pressed row
- Sort notes (sort button next to "Your notes", remembered): last edited newest or oldest first, or by title A to Z or Z to A. Pinned notes always stay first
- Select many notes: touch and hold a note, then tap more notes. The bar at the top then has Pin / Unpin, Archive and Delete for all selected notes at once, and "Select all" in its three-dot menu. Back or the X ends the selection
- Archive: keep a note without seeing it on the home screen (three-dot menu of a note, or the selection bar). Archived notes are not in the home list, but the search finds them (marked "Archived"). Open them from the side menu > Archive: search there, tap a note to read and edit it (it stays archived), unarchive it or delete it. "Undo" appears right after archiving
- Pin notes: "Pin" in the three-dot menu of a note keeps it at the top of the list ("Unpin" to undo)
- Show notes as a list or as a two-column grid (button next to "Your notes", remembered)
- Delete notes (they go to the Trash first)
- Trash: restore a note, delete it for good, or empty the Trash
- Notes in the Trash are deleted automatically 30 days after they were moved there
- Backup & restore (Settings > Backup & restore): save all notes (also the ones in the Trash and the Archive) into one `.json` file in a place you choose, and add the notes of such a file back. Restoring only adds notes: nothing that exists is changed, and importing the same file twice makes no copies. The file is not encrypted.
- App lock: unlock with fingerprint, face or the phone's own screen-lock PIN (Settings > App lock). It locks immediately, after 1 minute or after 30 minutes. Elnor has no PIN of its own. While the App lock is on, screenshots and screen recording are blocked and the Recent Apps preview of Elnor is blank, so notes cannot be seen there. The notes database is also kept out of Google cloud backup and adb backup: use Backup & restore to move notes to another phone.

About

Elnor is designed to make note-taking simple and convenient.

License

No license has been specified yet.
