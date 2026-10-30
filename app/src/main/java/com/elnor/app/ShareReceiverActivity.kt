package com.elnor.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives text from other apps and saves it as a new note, without showing any screen:
 *  - "Share" > Elnor (Intent.ACTION_SEND with text/plain). A subject, if there is one, is the title.
 *  - Select text > "Save to Elnor" in the text selection menu (Intent.ACTION_PROCESS_TEXT).
 *
 * The note is only written, never shown, so it works while the App lock is on: nothing of the
 * existing notes is revealed. The save runs in [ApplicationScope], so it finishes even though this
 * activity closes at once.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity (the system restored it) must not save the same text a second time
        if (savedInstanceState != null) {
            finish()
            return
        }

        val incoming = when (intent?.action) {
            Intent.ACTION_SEND -> buildIncomingNote(
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            )
            Intent.ACTION_PROCESS_TEXT -> buildIncomingNote(
                subject = null,
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            )
            else -> null
        }

        val appContext = applicationContext
        if (incoming == null) {
            Toast.makeText(appContext, "Nothing to save", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val repository = RoomNoteRepository.get(appContext)
        val note = Note(
            id = 0L, // ignored by insert: the database gives the id
            title = incoming.title,
            body = incoming.body,
            updatedAt = System.currentTimeMillis()
        )
        ApplicationScope.scope.launch {
            val saved = try {
                repository.insert(note)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    if (saved) "Saved to Elnor" else "Could not save the note",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        finish()
    }
}
