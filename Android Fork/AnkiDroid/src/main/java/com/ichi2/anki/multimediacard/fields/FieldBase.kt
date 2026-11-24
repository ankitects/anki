// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

package com.ichi2.anki.multimediacard.fields

/**
 * Base for all field types.
 * <p>
 * Controls modifications. This is done to not to save anything, if the field has not been modified.
 */
open class FieldBase {
    var thisModified = false
}
