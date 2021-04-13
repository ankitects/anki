// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

enum SyncState {
    NoChanges = 0,
    Normal,
    Full,
}

function updateSyncColor(state: SyncState) {
    const elem = document.getElementById("sync");
    switch (state) {
        case SyncState.NoChanges:
            elem.classList.remove("full-sync", "normal-sync");
            break;
        case SyncState.Normal:
            elem.classList.add("normal-sync");
            elem.classList.remove("full-sync");
            break;
        case SyncState.Full:
            elem.classList.add("full-sync");
            elem.classList.remove("normal-sync");
            break;
    }
}
