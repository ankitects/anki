// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

private var currentActivity: NSObjectProtocol?

@_cdecl("disable_appnap")
public func disableAppNap() {
    // No-op if already assigned
    guard currentActivity == nil else { return }
    
    currentActivity = ProcessInfo.processInfo.beginActivity(
        options: .userInitiatedAllowingIdleSystemSleep,
        reason: "AppNap is disabled"
    )
}

@_cdecl("enable_appnap")
public func enableAppNap() {
    guard let activity = currentActivity else { return }
    
    ProcessInfo.processInfo.endActivity(activity)
    currentActivity = nil
}