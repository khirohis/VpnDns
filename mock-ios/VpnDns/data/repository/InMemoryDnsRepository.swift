import Foundation

/// DnsRepository のインメモリでの実装クラス
public class InMemoryDnsRepository: DnsRepository {
    public static let shared = InMemoryDnsRepository()
    
    // スレッドセーフティのための同期キュー
    private let queue = DispatchQueue(label: "com.khirohis.VpnDns.InMemoryDnsRepositoryQueue", attributes: .concurrent)
    
    // 内部ストレージ（ホスト名キーの辞書で重複を排除）
    private var _history = [String: DnsEntity]()
    
    // 内部ストレージ（ブラックリスト、ホスト名キーの辞書で重複を排除）
    private var _blacklist = [String: BlacklistEntity]()
    
    public init() {
        // デフォルトのブラックリストルールをプリセット
        let defaultDomains = [
            "ads.doubleclick.net",
            "analytics.google.com",
            "telemetry.apple.com",
            "trackers.facebook.com"
        ]
        for domain in defaultDomains {
            _blacklist[domain] = BlacklistEntity(hostName: domain)
        }
    }
    
    /// 記録されているすべてのDNS解決履歴のリスト（最終アクセス日時の新しい順でソート）
    public var history: [DnsEntity] {
        var result = [DnsEntity]()
        queue.sync {
            result = Array(_history.values).sorted(by: { $0.lastSeen > $1.lastSeen })
        }
        return result
    }
    
    /// 新しいDNS解決結果を履歴に追加、または既存の記録をインクリメントする
    public func add(_ entity: DnsEntity) {
        queue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            if let existing = self._history[entity.hostName] {
                // すでに登録されているホスト名の場合は、リクエストカウントを増やし最終検知時刻を更新
                self._history[entity.hostName] = existing.incremented(at: entity.lastSeen, with: entity.blockType)
            } else {
                // 新規登録
                self._history[entity.hostName] = entity
            }
        }
    }
    
    /// すべての解決履歴をクリアする
    public func clear() {
        queue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            self._history.removeAll()
        }
    }
    
    /// 登録されているすべてのブラックリストルールのリスト（追加日時の古い順でソート）
    public var blacklist: [BlacklistEntity] {
        var result = [BlacklistEntity]()
        queue.sync {
            result = Array(_blacklist.values).sorted(by: { $0.addedAt < $1.addedAt })
        }
        return result
    }
    
    /// ブラックリストに新しいルールを追加する
    public func add(_ entity: BlacklistEntity) {
        queue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            self._blacklist[entity.hostName] = entity
        }
    }
    
    /// ブラックリストから特定のルールを削除する
    public func remove(_ entity: BlacklistEntity) {
        queue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            self._blacklist.removeValue(forKey: entity.hostName)
        }
    }
    
    /// すべてのブラックリストルールをクリアする
    public func clearBlacklist() {
        queue.async(flags: .barrier) { [weak self] in
            guard let self = self else { return }
            self._blacklist.removeAll()
        }
    }
}

