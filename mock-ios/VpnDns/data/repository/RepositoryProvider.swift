import Foundation

/// 依存関係の注入 (Dependency Injection) またはサービスロケーターパターンを用いて
/// ドメイン層（リプレタ）に対し具体的なリポジトリ実装クラスを注入・提供するプロバイダークラス
public final class RepositoryProvider {
    public static let shared = RepositoryProvider()
    
    /// 具現化された DNS 解決履歴管理リポジトリ（プロトコル経由で公開）
    public let dnsRepository: DnsRepository
    
    private init() {
        // インメモリの DnsRepository を実体化して保持
        self.dnsRepository = InMemoryDnsRepository.shared
    }
}
