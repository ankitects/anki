// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.common.destinations

import com.ichi2.anki.libanki.CardId

/** Opens the Card Info screen for [cardId], using [title] as the screen title. */
data class CardInfoDestination(
    val cardId: CardId,
    val title: String,
) : Destination()
