// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation
import Security

struct SyncCredentials: Codable, Equatable, CustomStringConvertible, Sendable {
    let username: String
    let password: String

    var description: String {
        "SyncCredentials(username: <redacted>, password: <redacted>)"
    }
}

struct SyncCredentialsStore: Sendable {
    private let service: String
    private let account = "anki-sync"

    init(service: String = "com.techmexdev.BrainliftMobile") {
        self.service = service
    }

    func save(_ credentials: SyncCredentials) throws {
        let data = try JSONEncoder().encode(credentials)
        let query = baseQuery.merging([
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]) { _, new in new }
        SecItemDelete(baseQuery as CFDictionary)
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError(status)
        }
    }

    func load() throws -> SyncCredentials? {
        let query = baseQuery.merging([
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]) { _, new in new }
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess, let data = result as? Data else {
            throw KeychainError(status)
        }
        return try JSONDecoder().decode(SyncCredentials.self, from: data)
    }

    func delete() throws {
        let status = SecItemDelete(baseQuery as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError(status)
        }
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}

struct KeychainError: Error, Equatable {
    let status: OSStatus

    init(_ status: OSStatus) {
        self.status = status
    }
}
