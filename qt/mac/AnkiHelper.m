// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

@import Foundation;
@import AppKit;

void set_darkmode_enabled(BOOL enabled) {
    NSAppearance *appearance;
    if (enabled) {
        appearance = [NSAppearance appearanceNamed:NSAppearanceNameDarkAqua];
    } else {
        appearance = [NSAppearance appearanceNamed:NSAppearanceNameAqua];
    }
    
    [NSApplication sharedApplication].appearance = appearance;
}
