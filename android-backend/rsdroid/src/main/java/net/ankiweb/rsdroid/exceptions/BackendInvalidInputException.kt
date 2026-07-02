/*
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.ankiweb.rsdroid.exceptions

import anki.backend.BackendError
import net.ankiweb.rsdroid.BackendException

/**
 * A lot of exceptions get converted to Invalid Input when returned:
 *
 * CollectionNotOpen
 * CollectionAlreadyOpen
 * SearchError
 */
open class BackendInvalidInputException(
    error: BackendError?,
) : BackendException(error!!) {
    class BackendCollectionAlreadyOpenException(
        error: BackendError?,
    ) : BackendInvalidInputException(error)

    class BackendCollectionNotOpenException(
        error: BackendError?,
    ) : BackendInvalidInputException(error)

    companion object {
        fun fromInvalidInputError(error: BackendError): BackendInvalidInputException {
            when (error.message) {
                "CollectionAlreadyOpen" -> return BackendCollectionAlreadyOpenException(error)
                "CollectionNotOpen" -> return BackendCollectionNotOpenException(error)
            }
            return BackendInvalidInputException(error)
        }
    }
}
