//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.annotation.WorkerThread
import androidx.core.database.sqlite.transaction
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.WhiteboardPenColor
import com.ichi2.anki.model.WhiteboardPenColor.Companion.default
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.widget.SmallWidgetStatus
import timber.log.Timber

/**
 * Used to store additional information besides what is stored in the deck itself.
 *
 *
 * Currently it used to store:
 *
 *  * The languages associated with questions and answers.
 *  * The state of the whiteboard.
 *  * The cached state of the widget.
 *
 */
@KotlinCleanup("see about lateinit")
@WorkerThread
object MetaDB {
    /** The name of the file storing the meta-db.  */
    private const val DATABASE_NAME = "ankidroid.db"

    /** The Database Version, increase if you want updates to happen on next upgrade.  */
    private const val DATABASE_VERSION = 8

    /** The database object used by the meta-db.  */
    private var metaDb: SQLiteDatabase? = null

    /** Open the meta-db  */
    @KotlinCleanup("scope function or lateinit db")
    private fun openDB(context: Context) {
        try {
            metaDb =
                context.openOrCreateDatabase(DATABASE_NAME, 0, null).let {
                    if (it.needUpgrade(DATABASE_VERSION)) {
                        upgradeDB(it, DATABASE_VERSION)
                    } else {
                        it
                    }
                }
            Timber.v("Opening MetaDB")
        } catch (e: Exception) {
            Timber.e(e, "Error opening MetaDB ")
        }
    }

    /** Creating any table that missing and upgrading necessary tables.  */
    private fun upgradeDB(
        metaDb: SQLiteDatabase,
        @Suppress("SameParameterValue") databaseVersion: Int,
    ): SQLiteDatabase {
        Timber.i("MetaDB:: Upgrading Internal Database..")
        // if (mMetaDb.getVersion() == 0) {
        Timber.i("MetaDB:: Applying changes for version: 0")
        if (metaDb.version < 4) {
            metaDb.execSQL("DROP TABLE IF EXISTS languages;")
            metaDb.execSQL("DROP TABLE IF EXISTS whiteboardState;")
        }

        // Create tables if not exist
        metaDb.execSQL(
            """CREATE TABLE IF NOT EXISTS languages (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            did INTEGER NOT NULL,
            ord INTEGER,
            qa INTEGER,
            language TEXT
            )""",
        )
        metaDb.execSQL(
            """CREATE TABLE IF NOT EXISTS smallWidgetStatus (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            due INTEGER NOT NULL,
            eta INTEGER NOT NULL
            )""",
        )
        metaDb.execSQL(
            """CREATE TABLE IF NOT EXISTS micToolbarState (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            did INTEGER NOT NULL,
            state INTEGER NOT NULL
            )""",
        )

        updateWidgetStatus(metaDb)
        updateWhiteboardState(metaDb)
        metaDb.version = databaseVersion
        Timber.i("MetaDB:: Upgrading Internal Database finished. New version: %d", databaseVersion)
        return metaDb
    }

    private fun updateWhiteboardState(metaDb: SQLiteDatabase) {
        val columnCount = DatabaseUtil.getTableColumnCount(metaDb, "whiteboardState")
        if (columnCount <= 0) {
            metaDb.execSQL(
                """CREATE TABLE IF NOT EXISTS whiteboardState (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                did INTEGER NOT NULL, state INTEGER,
                visible INTEGER,
                lightpencolor INTEGER,
                darkpencolor INTEGER,
                stylus INTEGER
                )""",
            )
            return
        }
        if (columnCount < 4) {
            // Default to 1
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN visible INTEGER NOT NULL DEFAULT '1'")
            Timber.i("Added 'visible' column to whiteboardState")
        }
        if (columnCount < 5) {
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN lightpencolor INTEGER DEFAULT NULL")
            Timber.i("Added 'lightpencolor' column to whiteboardState")
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN darkpencolor INTEGER DEFAULT NULL")
            Timber.i("Added 'darkpencolor' column to whiteboardState")
        }
        if (columnCount < 7) {
            metaDb.execSQL("ALTER TABLE whiteboardState ADD COLUMN stylus INTEGER")
            Timber.i("Added 'stylus mode' column to whiteboardState")
        }
    }

    private fun updateWidgetStatus(metaDb: SQLiteDatabase) {
        val columnCount = DatabaseUtil.getTableColumnCount(metaDb, "widgetStatus")
        if (columnCount > 0) {
            if (columnCount < 7) {
                metaDb.execSQL("ALTER TABLE widgetStatus ADD COLUMN eta INTEGER NOT NULL DEFAULT '0'")
                metaDb.execSQL("ALTER TABLE widgetStatus ADD COLUMN time INTEGER NOT NULL DEFAULT '0'")
            }
        } else {
            metaDb.execSQL(
                """CREATE TABLE IF NOT EXISTS widgetStatus (
                deckId INTEGER NOT NULL PRIMARY KEY,
                deckName TEXT NOT NULL,
                newCards INTEGER NOT NULL,
                lrnCards INTEGER NOT NULL,
                dueCards INTEGER NOT NULL,
                progress INTEGER NOT NULL,
                eta INTEGER NOT NULL
                )""",
            )
        }
    }

    /** Open the meta-db but only if it currently closed.  */
    private fun openDBIfClosed(context: Context) {
        if (!isDBOpen()) {
            openDB(context)
        }
    }

    /** Close the meta-db.  */
    fun closeDB() {
        if (isDBOpen()) {
            metaDb!!.close()
            metaDb = null
            Timber.d("Closing MetaDB")
        }
    }

    /** Reset the content of the meta-db, erasing all its content.  */
    fun resetDB(context: Context): Boolean {
        openDBIfClosed(context)
        try {
            metaDb!!.run {
                execSQL("DROP TABLE IF EXISTS languages;")
                Timber.i("MetaDB:: Resetting all language assignment")
                execSQL("DROP TABLE IF EXISTS whiteboardState;")
                Timber.i("MetaDB:: Resetting whiteboard state")
                execSQL("DROP TABLE IF EXISTS micToolbarState;")
                Timber.i("MetaDB:: Resetting mic toolbar state")
                execSQL("DROP TABLE IF EXISTS widgetStatus;")
                Timber.i("MetaDB:: Resetting widget status")
                execSQL("DROP TABLE IF EXISTS smallWidgetStatus;")
                Timber.i("MetaDB:: Resetting small widget status")
                execSQL("DROP TABLE IF EXISTS intentInformation;")
                Timber.i("MetaDB:: Resetting intentInformation")
                upgradeDB(this, DATABASE_VERSION)
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "Error resetting MetaDB ")
        }
        return false
    }

    /** Reset the language associations for all the decks and card models.  */
    fun resetLanguages(context: Context): Boolean {
        openDBIfClosed(context)
        try {
            Timber.i("MetaDB:: Resetting all language assignments")
            metaDb!!.run {
                execSQL("DROP TABLE IF EXISTS languages;")
                upgradeDB(this, DATABASE_VERSION)
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "Error resetting MetaDB ")
        }
        return false
    }

    /** Reset the widget status.  */
    private fun resetWidget(context: Context): Boolean {
        openDBIfClosed(context)
        try {
            Timber.i("MetaDB:: Resetting widget status")
            metaDb!!.run {
                execSQL("DROP TABLE IF EXISTS widgetStatus;")
                execSQL("DROP TABLE IF EXISTS smallWidgetStatus;")
                upgradeDB(this, DATABASE_VERSION)
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "Error resetting widgetStatus and smallWidgetStatus")
        }
        return false
    }

    /**
     * Associates a language to a deck, model, and card model for a given type.
     *
     * @param qa the part of the card for which to store the association, [.LANGUAGES_QA_QUESTION],
     * [.LANGUAGES_QA_ANSWER], or [.LANGUAGES_QA_UNDEFINED]
     * @param language the language to associate, as a two-characters, lowercase string
     */
    fun storeLanguage(
        context: Context,
        did: DeckId,
        ord: Int,
        qa: CardSide,
        language: String,
    ) {
        openDBIfClosed(context)
        try {
            if ("" == getLanguage(context, did, ord, qa)) {
                metaDb!!.execSQL(
                    "INSERT INTO languages (did, ord, qa, language)  VALUES (?, ?, ?, ?);",
                    arrayOf<Any>(
                        did,
                        ord,
                        qa.int,
                        language,
                    ),
                )
                Timber.v("Store language for deck %d", did)
            } else {
                metaDb!!.execSQL(
                    "UPDATE languages SET language = ? WHERE did = ? AND ord = ? AND qa = ?;",
                    arrayOf<Any>(
                        language,
                        did,
                        ord,
                        qa.int,
                    ),
                )
                Timber.v("Update language for deck %d", did)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error storing language in MetaDB ")
        }
    }

    /**
     * Returns the language associated with the given deck, model and card model, for the given type.
     *
     * @param qa the part of the card for which to store the association, [.LANGUAGES_QA_QUESTION],
     * [.LANGUAGES_QA_ANSWER], or [.LANGUAGES_QA_UNDEFINED] return the language associate with
     * the type, as a two-characters, lowercase string, or the empty string if no association is defined
     */
    fun getLanguage(
        context: Context,
        did: DeckId,
        ord: Int,
        qa: CardSide,
    ): String {
        openDBIfClosed(context)
        var language = ""
        val query = "SELECT language FROM languages WHERE did = ? AND ord = ? AND qa = ? LIMIT 1"
        try {
            metaDb!!
                .rawQuery(
                    query,
                    arrayOf(
                        did.toString(),
                        ord.toString(),
                        qa.int.toString(),
                    ),
                ).use { cur ->
                    Timber.v("getLanguage: %s", query)
                    if (cur.moveToNext()) {
                        language = cur.getString(0)
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching language ")
        }
        return language
    }

    /**
     * Returns the state of the whiteboard for the given deck.
     *
     * @return 1 if the whiteboard should be shown, 0 otherwise
     */
    fun getWhiteboardState(
        context: Context,
        did: DeckId,
    ): Boolean {
        openDBIfClosed(context)
        try {
            metaDb!!
                .rawQuery(
                    "SELECT state FROM whiteboardState  WHERE did = ?",
                    arrayOf(did.toString()),
                ).use { cur -> return DatabaseUtil.getScalarBoolean(cur) }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving whiteboard state from MetaDB ")
            return false
        }
    }

    /**
     * Stores the state of the whiteboard for a given deck.
     *
     * @param did deck id to store whiteboard state for
     * @param whiteboardState `true` if the whiteboard should be shown, `false` otherwise
     */
    fun storeWhiteboardState(
        context: Context,
        did: DeckId,
        whiteboardState: Boolean,
    ) {
        val state = if (whiteboardState) 1 else 0
        openDBIfClosed(context)
        try {
            val metaDb = metaDb!!
            metaDb
                .rawQuery(
                    "SELECT _id FROM whiteboardState WHERE did = ?",
                    arrayOf(did.toString()),
                ).use { cur ->
                    if (cur.moveToNext()) {
                        metaDb.execSQL(
                            "UPDATE whiteboardState SET did = ?, state=? WHERE _id=?;",
                            arrayOf<Any>(did, state, cur.getString(0)),
                        )
                        Timber.d("Store whiteboard state (%d) for deck %d", state, did)
                    } else {
                        metaDb.execSQL(
                            "INSERT INTO whiteboardState (did, state) VALUES (?, ?)",
                            arrayOf<Any>(did, state),
                        )
                        Timber.d("Store whiteboard state (%d) for deck %d", state, did)
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Error storing whiteboard state in MetaDB ")
        }
    }

    /**
     * Returns the state of the whiteboard stylus mode for the given deck.
     *
     * @return true if the whiteboard stylus mode should be enabled, false otherwise
     */
    fun getWhiteboardStylusState(
        context: Context,
        did: DeckId,
    ): Boolean {
        openDBIfClosed(context)
        try {
            metaDb!!
                .rawQuery(
                    "SELECT stylus FROM whiteboardState WHERE did = ?",
                    arrayOf(did.toString()),
                ).use { cur -> return DatabaseUtil.getScalarBoolean(cur) }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving whiteboard stylus mode state from MetaDB ")
            return false
        }
    }

    /**
     * Stores the state of the whiteboard stylus mode for a given deck.
     *
     * @param did deck id to store whiteboard stylus mode state for
     * @param whiteboardStylusState true if the whiteboard stylus mode should be enabled, false otherwise
     */
    fun storeWhiteboardStylusState(
        context: Context,
        did: DeckId,
        whiteboardStylusState: Boolean,
    ) {
        val state = if (whiteboardStylusState) 1 else 0
        openDBIfClosed(context)
        try {
            val metaDb = metaDb!!
            metaDb
                .rawQuery(
                    "SELECT _id FROM whiteboardState WHERE did = ?",
                    arrayOf(did.toString()),
                ).use { cur ->
                    if (cur.moveToNext()) {
                        metaDb.execSQL(
                            "UPDATE whiteboardState SET did = ?, stylus=? WHERE _id=?;",
                            arrayOf<Any>(did, state, cur.getString(0)),
                        )
                        Timber.d("Store whiteboard stylus mode state (%d) for deck %d", state, did)
                    } else {
                        metaDb.execSQL(
                            "INSERT INTO whiteboardState (did, stylus) VALUES (?, ?)",
                            arrayOf<Any>(did, state),
                        )
                        Timber.d("Store whiteboard stylus mode state (%d) for deck %d", state, did)
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Error storing whiteboard stylus mode state in MetaDB ")
        }
    }

    /**
     * Returns the state of the whiteboard for the given deck.
     *
     * @return 1 if the whiteboard should be shown, 0 otherwise
     */
    fun getWhiteboardVisibility(
        context: Context,
        did: DeckId,
    ): Boolean {
        openDBIfClosed(context)
        try {
            metaDb!!
                .rawQuery(
                    "SELECT visible FROM whiteboardState WHERE did = ?",
                    arrayOf(did.toString()),
                ).use { cur -> return DatabaseUtil.getScalarBoolean(cur) }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving whiteboard state from MetaDB ")
            return false
        }
    }

    /**
     * Stores the state of the whiteboard for a given deck.
     *
     * @param did deck id to store whiteboard state for
     * @param isVisible `true` if the whiteboard should be shown, `false` otherwise
     */
    fun storeWhiteboardVisibility(
        context: Context,
        did: DeckId,
        isVisible: Boolean,
    ) {
        val isVisibleState = if (isVisible) 1 else 0
        openDBIfClosed(context)
        try {
            val metaDb = metaDb!!
            metaDb
                .rawQuery(
                    "SELECT _id FROM whiteboardState WHERE did  = ?",
                    arrayOf(did.toString()),
                ).use { cur ->
                    if (cur.moveToNext()) {
                        metaDb.execSQL(
                            "UPDATE whiteboardState SET did = ?, visible= ?  WHERE _id=?;",
                            arrayOf<Any>(did, isVisibleState, cur.getString(0)),
                        )
                        Timber.d("Store whiteboard visibility (%d) for deck %d", isVisibleState, did)
                    } else {
                        metaDb.execSQL(
                            "INSERT INTO whiteboardState (did, visible) VALUES (?, ?)",
                            arrayOf<Any>(did, isVisibleState),
                        )
                        Timber.d("Store whiteboard visibility (%d) for deck %d", isVisibleState, did)
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "Error storing whiteboard visibility in MetaDB ")
        }
    }

    /**
     * Returns the pen color of the whiteboard for the given deck.
     */
    fun getWhiteboardPenColor(
        context: Context,
        did: DeckId,
    ): WhiteboardPenColor {
        openDBIfClosed(context)
        try {
            metaDb!!
                .rawQuery(
                    "SELECT lightpencolor, darkpencolor FROM whiteboardState WHERE did = ?",
                    arrayOf(did.toString()),
                ).use { cur ->
                    cur.moveToFirst()
                    val light = DatabaseUtil.getInteger(cur, 0)
                    val dark = DatabaseUtil.getInteger(cur, 1)
                    return WhiteboardPenColor(light, dark)
                }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving whiteboard pen color from MetaDB ")
            return default
        }
    }

    /**
     * Stores the pen color of the whiteboard for a given deck.
     *
     * @param did deck id to store whiteboard state for
     * @param isLight if dark mode is disabled
     * @param value The new color code to store
     */
    fun storeWhiteboardPenColor(
        context: Context,
        did: DeckId,
        isLight: Boolean,
        value: Int?,
    ) {
        openDBIfClosed(context)
        val columnName = if (isLight) "lightpencolor" else "darkpencolor"
        try {
            val metaDb = metaDb!!
            metaDb
                .rawQuery(
                    "SELECT _id FROM whiteboardState WHERE did  = ?",
                    arrayOf(did.toString()),
                ).use { cur ->
                    if (cur.moveToNext()) {
                        metaDb.execSQL(
                            "UPDATE whiteboardState SET did = ?, $columnName= ?  WHERE _id=?;",
                            arrayOf<Any?>(did, value, cur.getString(0)),
                        )
                    } else {
                        val sql = "INSERT INTO whiteboardState (did, $columnName) VALUES (?, ?)"
                        metaDb.execSQL(sql, arrayOf<Any?>(did, value))
                    }
                    Timber.d("Store whiteboard %s (%d) for deck %d", columnName, value, did)
                }
        } catch (e: Exception) {
            Timber.w(e, "Error storing whiteboard color in MetaDB")
        }
    }

    /**
     * Returns the state of the mic toolbar for the given deck.
     *
     * @return `true` if the toolbar should be shown, `false` otherwise
     */
    fun getMicToolbarState(
        context: Context,
        did: DeckId,
    ): Boolean {
        openDBIfClosed(context)
        try {
            metaDb!!
                .rawQuery(
                    "SELECT state FROM micToolbarState  WHERE did = ?",
                    arrayOf(did.toString()),
                ).use { cur -> return DatabaseUtil.getScalarBoolean(cur) }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving micToolbar state from MetaDB ")
            return false
        }
    }

    /**
     * Stores the state of the mic toolbar for a given deck.
     *
     * @param did deck id to store mic toolbar state for
     * @param isEnabled `true` if the toolbar should be shown, `false` otherwise
     */
    fun storeMicToolbarState(
        context: Context,
        did: DeckId,
        isEnabled: Boolean,
    ) {
        val state = if (isEnabled) 1 else 0
        openDBIfClosed(context)
        try {
            val metaDb = metaDb!!
            metaDb
                .rawQuery(
                    "SELECT _id FROM micToolbarState WHERE did = ?",
                    arrayOf(did.toString()),
                ).use { cur ->
                    if (cur.moveToNext()) {
                        metaDb.execSQL(
                            "UPDATE micToolbarState SET did = ?, state = ? WHERE _id = ?;",
                            arrayOf<Any>(did, state, cur.getString(0)),
                        )
                    } else {
                        metaDb.execSQL(
                            "INSERT INTO micToolbarState (did, state) VALUES (?, ?)",
                            arrayOf<Any>(did, state),
                        )
                    }
                    Timber.d("Store mic toolbar state (%d) for deck %d", state, did)
                }
        } catch (e: Exception) {
            Timber.e(e, "Error storing mic toolbar state in MetaDB ")
        }
    }

    /**
     * Return the current status of the widget.
     *
     * @return [due, eta] ([SmallWidgetStatus])
     */
    fun getWidgetSmallStatus(context: Context): SmallWidgetStatus {
        openDBIfClosed(context)
        try {
            metaDb!!
                .query(
                    "smallWidgetStatus",
                    arrayOf("due", "eta"),
                    null,
                    null,
                    null,
                    null,
                    null,
                ).use { cursor ->
                    if (cursor.moveToNext()) {
                        return SmallWidgetStatus(
                            dueCardsCount = cursor.getInt(0),
                            eta = cursor.getInt(1),
                        )
                    }
                }
        } catch (e: SQLiteException) {
            Timber.e(e, "Error while querying widgetStatus")
        }
        return SmallWidgetStatus(dueCardsCount = 0, eta = 0)
    }

    fun getNotificationStatus(context: Context): Int {
        openDBIfClosed(context)
        val due = 0
        try {
            metaDb!!.query("smallWidgetStatus", arrayOf("due"), null, null, null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0)
                }
            }
        } catch (e: SQLiteException) {
            Timber.e(e, "Error while querying widgetStatus")
        }
        return due
    }

    fun storeSmallWidgetStatus(
        context: Context,
        status: SmallWidgetStatus,
    ) {
        openDBIfClosed(context)
        try {
            val metaDb = metaDb!!
            metaDb.transaction {
                // First clear all the existing content.
                metaDb.execSQL("DELETE FROM smallWidgetStatus")
                metaDb.execSQL(
                    "INSERT INTO smallWidgetStatus(due, eta) VALUES (?, ?)",
                    arrayOf<Any>(status.dueCardsCount, status.eta),
                )
            }
            Timber.d("MetaDB:: Cached small widget status")
        } catch (e: IllegalStateException) {
            Timber.e(e, "MetaDB.storeSmallWidgetStatus: failed")
        } catch (e: SQLiteException) {
            Timber.e(e, "MetaDB.storeSmallWidgetStatus: failed")
            closeDB()
            Timber.i("MetaDB:: Trying to reset Widget: %b", resetWidget(context))
        }
    }

    fun close() {
        metaDb?.run {
            try {
                close()
            } catch (e: Exception) {
                Timber.w(e, "Failed to close MetaDB")
            }
        }
    }

    private object DatabaseUtil {
        fun getScalarBoolean(cur: Cursor): Boolean =
            if (cur.moveToNext()) {
                cur.getInt(0) > 0
            } else {
                false
            }

        // API LEVEL
        fun getTableColumnCount(
            metaDb: SQLiteDatabase,
            tableName: String,
        ) = metaDb.rawQuery("PRAGMA table_info($tableName)", null).use { c ->
            c.count
        }

        fun getInteger(
            cur: Cursor,
            columnIndex: Int,
        ): Int? = if (cur.isNull(columnIndex)) null else cur.getInt(columnIndex)
    }

    private fun isDBOpen() = metaDb?.isOpen == true
}
