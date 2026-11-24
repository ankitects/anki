// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.progress

/**
 * Interface for ViewModels that expose progress state to the UI.
 *
 * @param M the ViewModel's progress message type. See [ProgressManager].
 */
interface HasProgress<M : Any> {
    val progressManager: ProgressManager<M>
}
