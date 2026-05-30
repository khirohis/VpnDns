import Foundation

/// DNSリクエストの履歴および統計データを管理するリポジトリのインターフェース
public protocol DnsRepository {
    /// 記録されているすべてのDNS解決履歴のリスト
    var history: [DnsEntity] { get }
    
    /// 新しいDNS解決結果を履歴に追加、または既存の記録をインクリメントする
    func add(_ entity: DnsEntity)
    
    /// すべての解決履歴をクリアする
    func clear()
    
    /// 登録されているすべてのブラックリストルールのリスト
    var blacklist: [BlacklistEntity] { get }
    
    /// ブラックリストに新しいルールを追加する
    func add(_ entity: BlacklistEntity)
    
    /// ブラックリストから特定のルールを削除する
    func remove(_ entity: BlacklistEntity)
    
    /// すべてのブラックリストルールをクリアする
    func clearBlacklist()
}

