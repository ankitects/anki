addons-possibly-involved = Add-ons possibly involved: { $addons }
addons-failed-to-load =
    An add-on you installed failed to load. If problems persist, please go to the Tools>Add-ons menu, and disable or delete the add-on.
    
    When loading '{ $name }':
    { $traceback }
addons-failed-to-load2 =
    The following add-ons failed to load:
    { $addons }

    They may need to be updated to support this version of Anki. Click the { addons-check-for-updates } button
    to see if any updates are available.

    You can use the { about-copy-debug-info } button to get information that you can paste in a report to
    the add-on author.

    For add-ons that don't have an update available, you can disable or delete the add-on to prevent this
    message from appearing.
addons-startup-failed = Add-on Startup Failed
# Shown in the add-on configuration screen (Tools>Add-ons>Config), in the title bar
addons-config-window-title = Configure '{ $name }'
addons-config-validation-error = There was a problem with the provided configuration: { $problem }, at path { $path }, against schema { $schema }.
addons-window-title = Add-ons
addons-addon-has-no-configuration = Add-on has no configuration.
addons-addon-installation-error = Add-on installation error
addons-browse-addons = Browse Add-ons
addons-changes-will-take-effect-when-anki = Changes will take effect when Anki is restarted.
addons-check-for-updates = Check for Updates
addons-checking = Checking...
addons-code = Code:
addons-config = Config
addons-configuration = Configuration
addons-corrupt-addon-file = Corrupt add-on file.
addons-disabled = (disabled)
addons-disabled2 = (disabled)
addons-download-complete-please-restart-anki-to = Download complete. Please restart Anki to apply changes.
addons-downloaded-fnames = Downloaded { $fname }
addons-downloading-adbd-kb02fkb = Downloading { $part }/{ $total } ({ $kilobytes }KB)...
addons-error-downloading-ids-errors = Error downloading <i>{ $id }</i>: { $error }
addons-error-installing-bases-errors = Error installing <i>{ $base }</i>: { $error }
addons-get-addons = Get Add-ons...
addons-important-as-addons-are-programs-downloaded = <b>Important</b>: As add-ons are programs downloaded from the internet, they are potentially malicious.<b>You should only install add-ons you trust.</b><br><br>Are you sure you want to proceed with the installation of the following Anki add-on(s)?<br><br>%(names)s
addons-install-addon = Install Add-on
addons-install-addons = Install Add-on(s)
addons-install-anki-addon = Install Anki add-on
addons-install-from-file = Install from file...
addons-installation-complete = Installation complete
addons-installed-names = Installed { $name }
addons-installed-successfully = Installed successfully.
addons-invalid-addon-manifest = Invalid add-on manifest.
addons-invalid-code = Invalid code.
addons-invalid-code-or-addon-not-available = Invalid code, or add-on not available for your version of Anki.
addons-invalid-configuration = Invalid configuration:
addons-invalid-configuration-top-level-object-must = Invalid configuration: top level object must be a map
addons-no-updates-available = No updates available.
addons-one-or-more-errors-occurred = One or more errors occurred:
addons-packaged-anki-addon = Packaged Anki Add-on
addons-please-check-your-internet-connection = Please check your internet connection.
addons-please-report-this-to-the-respective = Please report this to the respective add-on author(s).
addons-please-restart-anki-to-complete-the = <b>Please restart Anki to complete the installation.</b>
addons-please-select-a-single-addon-first = Please select a single add-on first.
addons-requires = (requires { $val })
addons-restored-defaults = Restored defaults
addons-the-following-addons-are-incompatible-with = The following add-ons are incompatible with { $name } and have been disabled: { $found }
addons-the-following-addons-have-updates-available = The following add-ons have updates available. Install them now?
addons-the-following-conflicting-addons-were-disabled = The following conflicting add-ons were disabled:
addons-this-addon-is-not-compatible-with = This add-on is not compatible with your version of Anki.
addons-to-browse-addons-please-click-the = To browse add-ons, please click the browse button below.<br><br>When you've found an add-on you like, please paste its code below. You can paste multiple codes, separated by spaces.
addons-toggle-enabled = Toggle Enabled
addons-unable-to-update-or-delete-addon = Unable to update or delete add-on. Please start Anki while holding down the shift key to temporarily disable add-ons, then try again.  Debug info: { $val }
addons-unknown-error = Unknown error: { $val }
addons-view-addon-page = View Add-on Page
addons-view-files = View Files
addons-delete-the-numd-selected-addon =
    { $count ->
        [one] Delete the { $count } selected add-on?
       *[other] Delete the { $count } selected add-ons?
    }
addons-choose-update-window-title = Update Add-ons
addons-choose-update-update-all = Update All
