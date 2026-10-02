import Foundation
import CryptoKit
import CommonCrypto

// Same portable authenticated envelope as Android BackupCrypto.
enum BackupCipher {
    static let limit = 32 * 1024 * 1024
    static let magic = Data("ADHSBK01".utf8)
    static func random(_ count: Int) throws -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        guard SecRandomCopyBytes(kSecRandomDefault, count, &bytes) == errSecSuccess else { throw AppError.invalid }
        return Data(bytes)
    }
    static func key(_ password: String, salt: Data) throws -> SymmetricKey {
        guard password.utf16.count >= 10 else { throw AppError.invalid }
        var output = [UInt8](repeating: 0, count: 32)
        let input = Array(password.utf8)
        let result = input.withUnsafeBytes { p in salt.withUnsafeBytes { s in
            CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), p.baseAddress?.assumingMemoryBound(to: Int8.self), input.count,
                                s.baseAddress?.assumingMemoryBound(to: UInt8.self), salt.count, CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 600000, &output, 32)
        } }
        guard result == kCCSuccess else { throw AppError.invalid }
        defer { output.withUnsafeMutableBytes { $0.initializeMemory(as: UInt8.self, repeating: 0) } }
        return SymmetricKey(data: output)
    }
    static func encrypt(_ document: Document, password: String) throws -> Data {
        let plain = try JSONEncoder().encode(document.validated())
        guard plain.count <= limit - 52 else { throw AppError.invalid }
        let salt = try random(16), nonce = try random(12), header = magic + salt + nonce
        let box = try AES.GCM.seal(plain, using: key(password, salt: salt), nonce: AES.GCM.Nonce(data: nonce), authenticating: header)
        return header + box.ciphertext + box.tag
    }
    static func decrypt(_ data: Data, password: String) throws -> Document {
        guard data.count >= 52 && data.count <= limit && data.prefix(8) == magic else { throw AppError.invalid }
        let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: data[24..<36]), ciphertext: data[36..<(data.count-16)], tag: data.suffix(16))
        let plain = try AES.GCM.open(box, using: key(password, salt: data[8..<24]), authenticating: data.prefix(36))
        return try JSONDecoder().decode(Document.self, from: plain).validated()
    }
}
