import Foundation

/// DNSリクエストの履歴統計データを表すドメインエンティティ
public struct DnsEntity: Codable, Identifiable, Hashable {
    
    // MARK: - Enums
    
    /// DNSリクエストのブロック状態を表すタイプ
    public enum BlockType: String, Codable, Hashable {
        /// ブロックされていない通常のリクエスト
        case none = "NONE"
        
        /// ドメイン名の完全一致によってブロックされた状態
        case exact = "EXACT"
        
        /// パターン（ワイルドカードや部分一致など）によってブロックされた状態
        case patternMatched = "PATTERN_MATCHED"
    }
    
    // MARK: - Properties
    
    /// Identifiable への適合用（ドメイン名を一意のIDとする）
    public var id: String { hostName }
    
    /// ホスト名（例: "example.com"）
    public let hostName: String
    
    /// 最初にこのホストへのリクエストを検知した日時
    public var firstSeen: Date
    
    /// 最後にこのホストへのリクエストを検知した日時
    public var lastSeen: Date
    
    /// このホストに対して送信されたクエリの累計回数
    public var requestCount: Int
    
    /// ブロック状態を表すタイプ
    public var blockType: BlockType
    
    // MARK: - Initializer
    
    public init(
        hostName: String,
        firstSeen: Date = Date(),
        lastSeen: Date = Date(),
        requestCount: Int = 1,
        blockType: BlockType = .none
    ) {
        self.hostName = hostName
        self.firstSeen = firstSeen
        self.lastSeen = lastSeen
        self.requestCount = requestCount
        self.blockType = blockType
    }
    
    // MARK: - Helpers
    
    /// クエリ数を1インクリメントし、最終検知日時およびブロック状態を更新した新しいエンティティを返す（不変性を維持するためのヘルパー）
    public func incremented(at date: Date = Date(), with blockType: BlockType? = nil) -> DnsEntity {
        return DnsEntity(
            hostName: self.hostName,
            firstSeen: self.firstSeen,
            lastSeen: date,
            requestCount: self.requestCount + 1,
            blockType: blockType ?? self.blockType
        )
    }
}
