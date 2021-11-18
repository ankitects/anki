// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

@import Foundation;
@import AppKit;

/// Force our app to be either light or dark mode.
void set_darkmode_enabled(BOOL enabled) {
    NSAppearance *appearance;
    if (enabled) {
        appearance = [NSAppearance appearanceNamed:NSAppearanceNameDarkAqua];
    } else {
        appearance = [NSAppearance appearanceNamed:NSAppearanceNameAqua];
    }
    
    [NSApplication sharedApplication].appearance = appearance;
}

/// True if the system is set to dark mode.
BOOL system_is_dark(void) {
    BOOL styleSet = [[NSUserDefaults standardUserDefaults] objectForKey:@"AppleInterfaceStyle"] != nil;
    return styleSet;
    // FIXME: confirm whether this is required on 10.15/16 (it
    // does not appear to be on 11)
    
    //    BOOL autoSwitch = [[NSUserDefaults standardUserDefaults] boolForKey:@"AppleInterfaceStyleSwitchesAutomatically"];
    //
    //    if (@available(macOS 10.15, *)) {
    //        return autoSwitch ? !styleSet : styleSet;
    //    } else {
    //        return styleSet;
    //    }

}
