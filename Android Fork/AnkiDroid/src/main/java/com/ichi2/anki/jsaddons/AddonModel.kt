// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mani <infinyte01@gmail.com>

package com.ichi2.anki.jsaddons

data class AddonModel(
    val name: String,
    val addonTitle: String,
    val icon: String,
    val version: String,
    val description: String,
    val main: String,
    val ankidroidJsApi: String,
    val addonType: String,
    val keywords: List<String>,
    val author: Map<String, String>,
    val license: String,
    val homepage: String,
    /** Tarball location from the npm registry API; null for locally installed addons */
    val dist: DistInfo?,
)
