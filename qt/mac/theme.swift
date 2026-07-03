// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import AppKit
import Foundation

/// Force our app to be either light or dark mode.
@_cdecl("set_darkmode_enabled")
public func setDarkmodeEnabled(_ enabled: Bool) {
    NSApplication.shared.appearance = NSAppearance(named: enabled ? .darkAqua : .aqua)
}

/// True if the system is set to dark mode.
@_cdecl("system_is_dark")
public func systemIsDark() -> Bool {
    let styleSet = UserDefaults.standard.object(forKey: "AppleInterfaceStyle") != nil
    return styleSet
}
