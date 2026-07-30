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
    private let keychain: any KeychainAccess

    init(
        service: String = "com.techmexdev.BrainliftMobile",
        keychain: any KeychainAccess = SystemKeychainAccess()
    ) {
        self.service = service
        self.keychain = keychain
    }

    func save(_ credentials: SyncCredentials) throws {
        let data = try JSONEncoder().encode(credentials)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let updateStatus = keychain.update(baseQuery, attributes: attributes)
        if updateStatus == errSecSuccess {
            return
        }
        guard updateStatus == errSecItemNotFound else {
            throw KeychainError(updateStatus)
        }

        let query = baseQuery.merging(attributes) { _, new in new }
        let addStatus = keychain.add(query)
        guard addStatus == errSecSuccess else {
            throw KeychainError(addStatus)
        }
    }

    func load() throws -> SyncCredentials? {
        let query = baseQuery.merging([
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]) { _, new in new }
        let result = keychain.copyMatching(query)
        if result.status == errSecItemNotFound {
            return nil
        }
        guard result.status == errSecSuccess, let data = result.data else {
            throw KeychainError(result.status)
        }
        return try JSONDecoder().decode(SyncCredentials.self, from: data)
    }

    func delete() throws {
        let status = keychain.delete(baseQuery)
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

struct KeychainReadResult: Sendable {
    let status: OSStatus
    let data: Data?
}

protocol KeychainAccess: Sendable {
    func update(
        _ query: [String: Any],
        attributes: [String: Any]
    ) -> OSStatus
    func add(_ attributes: [String: Any]) -> OSStatus
    func copyMatching(_ query: [String: Any]) -> KeychainReadResult
    func delete(_ query: [String: Any]) -> OSStatus
}

struct SystemKeychainAccess: KeychainAccess {
    func update(
        _ query: [String: Any],
        attributes: [String: Any]
    ) -> OSStatus {
        SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
    }

    func add(_ attributes: [String: Any]) -> OSStatus {
        SecItemAdd(attributes as CFDictionary, nil)
    }

    func copyMatching(_ query: [String: Any]) -> KeychainReadResult {
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        return KeychainReadResult(status: status, data: result as? Data)
    }

    func delete(_ query: [String: Any]) -> OSStatus {
        SecItemDelete(query as CFDictionary)
    }
}

struct KeychainError: Error, Equatable {
    let status: OSStatus

    init(_ status: OSStatus) {
        self.status = status
    }
}
