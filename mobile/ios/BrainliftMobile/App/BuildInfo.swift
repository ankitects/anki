// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

enum BuildInfo {
    static let sourceRevision = String(cString: anki_backend_source_revision())
    static let bundleSourceRevision =
        Bundle.main.object(forInfoDictionaryKey: "AnkiBridgeSourceRevision") as? String
    static let identityIsConsistent = bundleSourceRevision == sourceRevision
    static let shortSourceRevision =
        String(sourceRevision.prefix(9)) + (sourceRevision.hasSuffix("-dirty") ? "-dirty" : "")
    static let displaySourceRevision =
        identityIsConsistent ? shortSourceRevision : "\(shortSourceRevision) (bundle mismatch)"
}
