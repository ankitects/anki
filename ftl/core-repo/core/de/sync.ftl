### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = Hinzugefügt: { $up }↑ { $down }↓
sync-media-removed-count = Entfernt: { $up }↑ { $down }↓
sync-media-checked-count = Überprüft: { $count }
sync-media-starting = Mediensynchronisierung startet …
sync-media-complete = Die Mediensynchronisierung ist abgeschlossen.
sync-media-failed = Die Mediensynchronisierung ist fehlgeschlagen.
sync-media-aborting = Mediensychronisierung wird abgebrochen …
sync-media-aborted = Die Mediensynchronisierung wurde abgebrochen.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = Die Mediensychronisierung wurde deaktiviert.
# Title of the screen that shows syncing progress history
sync-media-log-title = Mediensychronisations-Logfile

## Error messages / dialogs

sync-conflict = Es kann sich ausschließlich ein Client von Anki mit ihrem Konto synchronisieren. Bitte warten Sie einige Minuten und versuchen Sie es anschließend erneut.
sync-server-error = Es ist ein Problem mit AnkiWeb aufgetreten. Bitte versuchen Sie es in ein paar Minuten erneut.
sync-client-too-old = Ihre Anki-Version ist zu alt. Bitte installieren Sie die aktuelle Anki-Version, um weiterhin sychronisieren zu können.
sync-wrong-pass = Die AnkiWeb-E-Mail oder das Passwort waren falsch; bitte versuchen Sie es nochmal.
sync-resync-required = Bitte synchronisieren Sie noch einmal. Wenn diese Meldung weiterhin erscheint, wenden Sie sich bitte an die Support-Seite.
sync-must-wait-for-end = Anki synchronisiert gerade. Bitte warten Sie, bis die Synchronisierung abgeschlossen ist und versuchen Sie es danach erneut.
sync-confirm-empty-download = Die lokale Sammlung enthält keine Karten. Möchten Sie sie von AnkiWeb herunterladen?
sync-confirm-empty-upload = Die Sammlung auf AnkiWeb enthält keine Karten. Durch lokale Sammlung ersetzen?
sync-conflict-explanation =
    Der Stapel hier und auf AnkiWeb unterscheiden sich in einer solchen Weise, dass sie nicht zusammengeführt werden können. Es ist daher notwendig, die Stapel auf einer Seite mit den Stapeln auf der anderen Seite zu überschreiben.
    
    Wenn jetzt »Herunterladen« ausgewählt wird, wird Anki die Sammlung von AnkiWeb herunterladen, und alle Änderungen, die seit der letzten Synchronisierung auf diesem Rechner gemacht wurden, gehen verloren.
    
    Wenn Sie »Hochladen« auswählen, wird Anki Ihre Stapel nach AnkiWeb hochladen, und alle Änderungen, die Sie im AnkiWeb oder Ihren anderen Geräten seit der letzten Synchronisierung gemacht haben, gehen verloren.
    
    Nachdem die Stapel auf allen Geräten synchron sind, werden zukünftige Wiederholungen (von Karten) und neu hinzugefügte Karten automatisch zusammengeführt.
sync-conflict-explanation2 =
    Es gibt einen Synchronisierungskonflikt zwischen den Stapeln auf diesem Gerät und denen auf AnkiWeb (in der Cloud). Du musst auswählen, welche Version du behalten möchtest:
    
    - Wähle **{ sync-download-from-ankiweb }**, um die Stapel auf diesem Gerät mit denen von AnkiWeb zu ersetzen. Du verlierst dadurch alle Änderungen, die du auf diesem Gerät seit der letzten Synchronisierung vorgenommen hast.
    - Wähle **{ sync-upload-to-ankiweb }**, um die Stapel auf AnkiWeb mit denen auf diesem Gerät zu überschreiben. Dadurch gehen die in AnkiWeb gespeicherten Änderungen verloren.
    
    Nachdem der Konflikt behoben ist, funktioniert die Synchronisierung wieder wie gewohnt.
sync-ankiweb-id-label = AnkiWeb-E-Mail:
sync-password-label = Passwort:
sync-account-required =
    <h1>Anmeldung erforderlich</h1>
    Um die Sammlung zu synchronisieren ist die Anmeldung zu einem kostenfreien Nutzerkonto notwendig, <a href="{ $link }">Registrierung hier</a>. Danach bitte hier Benutzernamen und Passwort eingeben.
sync-sanity-check-failed = Bitte benutzen Sie die Funktion „Datenbank überprüfen“ und synchronisieren Sie anschließend. Wenn das Problem weiterhin besteht, dann erzwingen Sie bitte eine Einweg-Synchronisierung in den Einstellungen.
sync-clock-off = Synchronisierung kann nicht durchgeführt werden - Ihre System-Uhrzeit ist nicht richtig eingestellt.
sync-upload-too-large =
    Die Sammlung ist zu groß für AnkiWeb.
    
    Sie können nicht benötigte Stapel entfernen (falls gewünscht vorher exportieren) und anschließend die Funktion „Datenbank überprüfen“ nutzen, um die Größe zu verringern.
    
    { $details } (unkomprimiert)
sync-sign-in = Anmeldung
sync-ankihub-dialog-heading = AnkiHub-Anmeldung
sync-ankihub-username-label = Benutzername oder E-Mail-Adresse:
sync-ankihub-login-failed = Die Anmeldung bei AnkiHub mit den eingegebenen Zugangsdaten ist fehlgeschlagen.
sync-ankihub-addon-installation = AnkiHub-Erweiterungs-Installation

## Buttons

sync-media-log-button = Medien-Logfile
sync-abort-button = Abbrechen
sync-download-from-ankiweb = Von AnkiWeb herunterladen
sync-upload-to-ankiweb = Zu AnkiWeb hochladen
sync-cancel-button = Abbrechen

## Normal sync progress

sync-downloading-from-ankiweb = Von AnkiWeb herunterladen …
sync-uploading-to-ankiweb = Zu AnkiWeb wird hochgeladen …
sync-syncing = Synchronisierung wird durchgeführt …
sync-checking = Überprüfung läuft …
sync-connecting = Verbindungsaufbau …
sync-added-updated-count = Hinzugefügt/geändert: { $up } ↑ { $down } ↓
sync-log-in-button = Anmelden
sync-log-out-button = Abmelden
sync-collection-complete = Synchronisierung der Sammlung abgeschlossen.
