// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.anki.libanki

import anki.sync.SyncAuth
import anki.sync.fullUploadOrDownloadRequest

fun Collection.fullUploadOrDownload(
    auth: SyncAuth,
    upload: Boolean,
    serverUsn: Int?,
) = backend.fullUploadOrDownload(
    fullUploadOrDownloadRequest {
        this.auth = auth
        if (serverUsn != null) {
            this.serverUsn = serverUsn
        }
        this.upload = upload
    },
)
