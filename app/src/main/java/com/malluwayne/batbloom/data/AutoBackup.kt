package com.malluwayne.batbloom.data

import androidx.core.content.edit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.malluwayne.batbloom.BatbloomApp
import java.time.LocalDate

/**
 * Weekly backup into a folder you pick once (on the phone or a cloud drive). Each run writes
 * batbloom-auto-YYYY-MM-DD.json and keeps the newest [KEEP]. Runs from a weekly alarm, and also when the app opens
 * if a week has passed (so a missed alarm catches up).
 */
object AutoBackup {
    const val KEEP = 4
    const val PREFIX = "batbloom-auto-"
    const val WEEK_MS = 7 * 24 * 3600_000L

    // --- pure parts (unit tested) ---

    fun fileName(day: LocalDate) = "$PREFIX$day.json"

    /** A backup is due when it's on, a folder is set, and the last one is a week old (or there is none). */
    fun isDue(enabled: Boolean, hasFolder: Boolean, lastMs: Long, nowMs: Long) =
        enabled && hasFolder && (lastMs <= 0 || nowMs - lastMs >= WEEK_MS)

    /** Which of the auto-backup files in the folder to delete so only the newest [keep] remain. */
    fun toDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { it.startsWith(PREFIX) && it.endsWith(".json") }.sortedDescending().drop(keep)

    // --- Android parts ---

    private fun prefs(c: Context) = c.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun folder(c: Context): Uri? = prefs(c).getString("tree", null)?.let(Uri::parse)
    fun enabled(c: Context) = prefs(c).getBoolean("on", false)
    fun lastMs(c: Context) = prefs(c).getLong("last", 0L)
    fun lastError(c: Context): String? = prefs(c).getString("error", null)

    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit { putBoolean("on", on) }

    /** Saves the picked folder and keeps permission to write there after restarts. */
    fun setFolder(c: Context, tree: Uri) {
        runCatching {
            c.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs(c).edit { putString("tree", tree.toString()).putBoolean("on", true) }
    }

    fun folderName(c: Context): String? {
        val tree = folder(c) ?: return null
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return runCatching {
            c.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
    }

    /** Runs a backup if one is due; returns true if it wrote one. */
    suspend fun runIfDue(c: Context): Boolean =
        if (isDue(enabled(c), folder(c) != null, lastMs(c), System.currentTimeMillis())) runNow(c) else false

    /** Writes a backup into the folder now and prunes old ones. Returns false (and remembers why) on failure. */
    suspend fun runNow(c: Context): Boolean {
        val tree = folder(c) ?: return false
        val app = c.applicationContext as BatbloomApp
        return runCatching {
            val resolver = c.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val existing = mutableMapOf<String, String>() // name -> document id
            resolver.query(
                children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null,
            )?.use { cur -> while (cur.moveToNext()) existing[cur.getString(0)] = cur.getString(1) }

            val name = fileName(LocalDate.now())
            // The data first, then a new file; the old same-day file goes only once the new one is written.
            val json = Backup.toJson(Backup.collect(c, app.db))
            val file = DocumentsContract.createDocument(resolver, parent, "application/json", "$name.tmp")
                ?: error("Couldn't create the backup file")
            try {
                resolver.openOutputStream(file, "wt")!!.use { it.write(json.toByteArray()) }
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, file) }
                throw e
            }
            existing[name]?.let { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it)) }
            // Some storage (cloud drives) can't rename: then write the final file directly and drop the temporary one.
            if (runCatching { DocumentsContract.renameDocument(resolver, file, name) }.getOrNull() == null) {
                val final = DocumentsContract.createDocument(resolver, parent, "application/json", name) ?: error("Couldn't create the backup file")
                resolver.openOutputStream(final, "wt")!!.use { it.write(json.toByteArray()) }
                runCatching { DocumentsContract.deleteDocument(resolver, file) }
            }

            val names = existing.keys - name + name
            toDelete(names.toList()).forEach { old ->
                existing[old]?.let { id -> runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) } }
            }
            prefs(c).edit { putLong("last", System.currentTimeMillis()).remove("error") }
            true
        }.getOrElse {
            prefs(c).edit { putString("error", it.message ?: "Backup failed") }
            false
        }
    }
}
