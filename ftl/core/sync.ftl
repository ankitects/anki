### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Added: { $up }↑ { $down }↓
sync-media-removed-count = Removed: { $up }↑ { $down }↓
sync-media-checked-count = Checked: { $count }
sync-media-starting = Media sync starting...
sync-media-complete = Media sync complete.
sync-media-failed = Media sync failed.
sync-media-aborting = Media sync aborting...
sync-media-aborted = Media sync aborted.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Media sync disabled.
# Title of the screen that shows syncing progress history
sync-media-log-title = Media Sync Log

## Error messages / dialogs

sync-conflict = Only one copy of Anki can sync to your account at once. Please wait a few minutes, then try again.
sync-server-error = AnkiWeb encountered a problem. Please try again in a few minutes.
sync-client-too-old = Your Anki version is too old. Please update to the latest version to continue syncing.
sync-wrong-pass = Email or password was incorrect; please try again.
sync-resync-required = Please sync again. If this message keeps appearing, please post on the support site.
sync-must-wait-for-end = Anki is currently syncing. Please wait for the sync to complete, then try again.
sync-confirm-empty-download = Local collection has no cards. Download from AnkiWeb?
sync-confirm-empty-upload = AnkiWeb collection has no cards. Replace it with local collection?
sync-conflict-explanation =
    Your decks here and on AnkiWeb differ in such a way that they can't be merged together, so it's necessary to overwrite the decks on one side with the decks from the other.
    
    If you choose download, Anki will fetch the collection from AnkiWeb, and any changes you have made on this device since the last sync will be lost.
    
    If you choose upload, Anki will send this device's data to AnkiWeb, and any changes that are waiting on AnkiWeb will be lost.
    
    After all devices are in sync, future reviews and added cards can be merged automatically.
sync-conflict-explanation2 =
    There is a conflict between decks on this device and AnkiWeb. You must choose which version to keep:

    - Select **{ sync-download-from-ankiweb }** to replace decks here with AnkiWeb’s version. You will lose any changes you made on this device since your last sync.
    - Select **{ sync-upload-to-ankiweb }** to overwrite AnkiWeb’s versions with decks from this device, and delete any changes on AnkiWeb.

    Once the conflict is resolved, syncing will work as usual.

sync-ankiweb-id-label = Email:
sync-password-label = Password:
sync-account-required =
    <h1>Account Required</h1>
    A free account is required to keep your collection synchronized. Please <a href="{ $link }">sign up</a> for an account, then enter your details below.
sync-sanity-check-failed = Please use the Check Database function, then sync again. If problems persist, please force a one-way sync in the preferences screen.
sync-clock-off = Unable to sync - your clock is not set to the correct time.
sync-upload-too-large =
    Your collection file is too large to send to AnkiWeb. You can reduce its size by removing any unwanted decks (optionally exporting them first), and then using Check Database to shrink the file size down.
    
    ({ $details })
sync-sign-in = Sign in
sync-ankihub-dialog-heading = AnkiHub Login
sync-ankihub-username-label = Username or Email:
sync-ankihub-login-failed = Unable to log in to AnkiHub with the provided credentials.
sync-ankihub-addon-installation = AnkiHub Add-on Installation

## Buttons

sync-media-log-button = Media Log
sync-abort-button = Abort
sync-download-from-ankiweb = Download from AnkiWeb
sync-upload-to-ankiweb = Upload to AnkiWeb
sync-cancel-button = Cancel

## Normal sync progress

sync-downloading-from-ankiweb = Downloading from AnkiWeb...
sync-uploading-to-ankiweb = Uploading to AnkiWeb...
sync-syncing = Syncing...
sync-checking = Checking...
sync-connecting = Connecting...
sync-added-updated-count = Added/modified: { $up }↑ { $down }↓
sync-log-in-button = Log In
sync-log-out-button = Log Out
sync-collection-complete = Collection sync complete.
