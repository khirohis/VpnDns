import Foundation

/// ブラックリストに登録されたブロック対象ドメインを表すドメインエンティティ
public struct BlacklistEntity: Codable, Identifiable, Hashable {
    
    // MARK: - Properties
    
    /// Identifiable への適合用（ドメイン名を一意のIDとする）
    public var id: String { hostName }
    
    /// ブロック対象のホスト名（例: "ads.doubleclick.net"）
    public let hostName: String
    
    /// ブラックリストに追加された日時（タイムスタンプ）
    public let addedAt: Date
    
    // MARK: - Initializer
    
    public init(hostName: String, addedAt: Date = Date()) {
        self.hostName = hostName
        self.addedAt = addedAt
    }
}
