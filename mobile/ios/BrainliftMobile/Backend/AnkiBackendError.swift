// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

enum AnkiBackendError: Error, Equatable, LocalizedError {
    case notOpen
    case alreadyOpen
    case native(kind: Int, message: String)
    case invalidNativeBuffer

    var errorDescription: String? {
        switch self {
        case .notOpen:
            "The Anki collection is not open."
        case .alreadyOpen:
            "The Anki backend is already open."
        case let .native(_, message):
            message
        case .invalidNativeBuffer:
            "The Anki backend returned an invalid native buffer."
        }
    }
}
