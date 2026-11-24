// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.database.AbstractCursor
import android.database.MatrixCursor

/**
 * A cursor that loads rows on demand and retains at most one loaded row.
 *
 * Creating the cursor, reading its count, and moving between positions do not load any rows.
 * Reading a column calls [loadRow], which must add exactly one row to the supplied [MatrixCursor].
 * Further reads at the same position reuse that row. Moving discards it, so reading it again after
 * moving away calls [loadRow] again and may yield updated values. Android can copy rows into a
 * cursor window without this cursor retaining all the results.
 *
 * For example, defer fetching card details until a result is read:
 * ```kotlin
 * val cursor = LazyCursor(arrayOf("_id", "reps"), cardIds.size) { position, row ->
 *     val card = col.getCardOrNull(cardIds[position])
 *     row.newRow().add(card.id).add(card.reps)
 * }
 * ```
 */
internal class LazyCursor(
    private val columns: Array<String>,
    private val rowCount: Int,
    loadRow: (Int, MatrixCursor) -> Unit,
) : AbstractCursor() {
    private var loadRow: ((Int, MatrixCursor) -> Unit)? = loadRow
    private var row: MatrixCursor? = null

    override fun getCount(): Int = rowCount

    override fun getColumnNames(): Array<String> = columns

    override fun onMove(
        oldPosition: Int,
        newPosition: Int,
    ): Boolean {
        check(!isClosed)
        row?.close()
        row = null
        return true
    }

    private fun currentRow(): MatrixCursor {
        check(!isClosed)
        checkPosition()
        row?.let { return it }
        val next = MatrixCursor(columns, 1)
        try {
            loadRow!!(position, next)
            check(next.count == 1)
            next.moveToFirst()
            row = next
        } catch (e: Exception) {
            next.close()
            throw e
        }
        return next
    }

    override fun getString(column: Int): String? = currentRow().getString(column)

    override fun getShort(column: Int): Short = currentRow().getShort(column)

    override fun getInt(column: Int): Int = currentRow().getInt(column)

    override fun getLong(column: Int): Long = currentRow().getLong(column)

    override fun getFloat(column: Int): Float = currentRow().getFloat(column)

    override fun getDouble(column: Int): Double = currentRow().getDouble(column)

    override fun getBlob(column: Int): ByteArray? = currentRow().getBlob(column)

    override fun getType(column: Int): Int = currentRow().getType(column)

    override fun isNull(column: Int): Boolean = currentRow().isNull(column)

    override fun close() {
        row?.close()
        row = null
        loadRow = null
        super.close()
    }
}
