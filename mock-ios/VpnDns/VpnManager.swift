import Foundation
import NetworkExtension
import Combine
#if canImport(UIKit)
import UIKit
#endif

@MainActor
public class VpnManager: ObservableObject {
    public static let shared = VpnManager()
    
    @Published public var isEnabled: Bool = false
    @Published public var status: NEVPNStatus = .disconnected
    @Published public var isPending: Bool = false
    
    /// DnsRepository が保持する履歴情報を直接参照する算出プロパティ
    public var history: [DnsEntity] {
        RepositoryProvider.shared.dnsRepository.history
    }
    
    /// DnsRepository が保持するブラックリスト情報を直接参照する算出プロピティ
    public var blacklist: [BlacklistEntity] {
        RepositoryProvider.shared.dnsRepository.blacklist
    }

    
    private var manager: NETunnelProviderManager?
    private var cancellables = Set<AnyCancellable>()
    private var pollingTimer: Timer?
    
    // アプリのグループIDやExtensionのBundle IDを設定
    private let providerBundleIdentifier = "com.khirohis.VpnDns.PacketTunnel"
    
    private init() {
        // VPNの接続状態の変更通知を監視
        NotificationCenter.default.publisher(for: .NEVPNStatusDidChange)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] notification in
                guard let self = self else { return }
                self.updateStatus()
            }
            .store(in: &cancellables)
            
        #if canImport(UIKit)
        NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self = self else { return }
                if self.status == .connected {
                    self.startPolling()
                }
            }
            .store(in: &cancellables)
            
        NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.stopPolling()
            }
            .store(in: &cancellables)
        #endif
            
        Task {
            await loadManager()
        }
    }
    
    /// システムのVPN設定からManagerをロードまたは作成する
    public func loadManager() async {
        isPending = true
        defer { isPending = false }
        
        do {
            let managers = try await NETunnelProviderManager.loadAllFromPreferences()
            if let existingManager = managers.first(where: {
                let proto = $0.protocolConfiguration as? NETunnelProviderProtocol
                return proto?.providerBundleIdentifier == self.providerBundleIdentifier
            }) {
                self.manager = existingManager
            } else {
                // 新しいマネージャの作成
                let newManager = NETunnelProviderManager()
                let protocolConfiguration = NETunnelProviderProtocol()
                protocolConfiguration.providerBundleIdentifier = providerBundleIdentifier
                protocolConfiguration.serverAddress = "VpnDns Local Loopback"
                
                newManager.protocolConfiguration = protocolConfiguration
                newManager.localizedDescription = "VpnDns Proxy"
                
                try await newManager.saveToPreferences()
                // ロードし直して設定を固定する
                try await newManager.loadFromPreferences()
                self.manager = newManager
            }
            
            self.updateStatus()
        } catch {
            print("Failed to load VPN manager: \(error.localizedDescription)")
        }
    }
    
    /// VPNの現在のステータスを更新する
    private func updateStatus() {
        guard let manager = manager else {
            self.isEnabled = false
            self.status = .disconnected
            stopPolling()
            self.objectWillChange.send()
            return
        }
        
        self.status = manager.connection.status
        self.isEnabled = manager.isEnabled
        
        if self.status == .connected {
            startPolling()
        } else {
            stopPolling()
        }
        
        self.objectWillChange.send()
    }
    
    /// VPN（DNSフィルタ）の有効化・開始を行う
    public func startVpn() async {
        isPending = true
        defer { isPending = false }
        
        guard let manager = manager else {
            await loadManager()
            guard self.manager != nil else { return }
            return
        }
        
        do {
            // 一度環境設定から読み込み直す
            try await manager.loadFromPreferences()
            manager.isEnabled = true
            
            try await manager.saveToPreferences()
            
            // トンネルの開始
            try manager.connection.startVPNTunnel()
            print("VPN Tunnel started successfully")
        } catch {
            print("Failed to start VPN: \(error.localizedDescription)")
        }
        
        updateStatus()
    }
    
    /// VPN（DNSフィルタ）の無効化・停止を行う
    public func stopVpn() {
        guard let manager = manager else { return }
        
        manager.connection.stopVPNTunnel()
        print("VPN Tunnel stopped")
        
        // isEnabled を false に設定して保存（システム環境設定で無効化）
        Task {
            do {
                try await manager.loadFromPreferences()
                manager.isEnabled = false
                try await manager.saveToPreferences()
            } catch {
                print("Failed to disable VPN preference: \(error.localizedDescription)")
            }
            updateStatus()
        }
    }
    
    /// ステータス文字列の取得ヘルパー
    public var statusDescription: String {
        switch status {
        case .disconnected: return "接続されていません (Off)"
        case .connecting: return "接続中..."
        case .connected: return "DNS保護中 (On)"
        case .disconnecting: return "切断中..."
        case .invalid: return "無効な設定"
        case .reasserting: return "再接続中..."
        @unknown default: return "未知のステータス"
        }
    }
    
    // MARK: - History Polling & IPC
    
    private func startPolling() {
        guard pollingTimer == nil else { return }
        pollingTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                await self?.fetchHistoryFromExtension()
            }
        }
    }
    
    private func stopPolling() {
        pollingTimer?.invalidate()
        pollingTimer = nil
    }
    
    /// ExtensionからDNS履歴データをIPCで取得し、アプリ側のリポジトリに同期・マージする
    public func fetchHistoryFromExtension() async {
        guard let manager = manager,
              let session = manager.connection as? NETunnelProviderSession,
              status == .connected else { return }
        
        guard let messageData = "fetch_history".data(using: .utf8) else { return }
        
        do {
            try session.sendProviderMessage(messageData) { [weak self] responseData in
                guard let self = self, let responseData = responseData else { return }
                
                do {
                    let newQueries = try JSONDecoder().decode([DnsEntity].self, from: responseData)
                    guard !newQueries.isEmpty else { return }
                    
                    // アプリ側のリポジトリにマージして追加
                    for query in newQueries {
                        RepositoryProvider.shared.dnsRepository.add(query)
                    }
                    
                    // リプライから取得するためにオブザーバーに通知
                    self.objectWillChange.send()
                } catch {
                    print("Failed to decode queries from extension: \(error.localizedDescription)")
                }
            }
        } catch {
            print("Failed to send provider message: \(error.localizedDescription)")
        }
    }
    
    /// 履歴のクリア
    public func clearHistory() {
        RepositoryProvider.shared.dnsRepository.clear()
        self.objectWillChange.send()
        
        guard let manager = manager,
              let session = manager.connection as? NETunnelProviderSession,
              status == .connected else { return }
        
        guard let messageData = "fetch_history".data(using: .utf8) else { return }
        // Extensionバッファもクリア
        try? session.sendProviderMessage(messageData) { _ in }
    }
    
    // MARK: - Blacklist Operations
    
    /// ブラックリストに新しいドメインを追加する
    public func addBlacklist(hostName: String) {
        let cleanHost = hostName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanHost.isEmpty else { return }
        let entity = BlacklistEntity(hostName: cleanHost)
        RepositoryProvider.shared.dnsRepository.add(entity)
        self.objectWillChange.send()
    }
    
    /// ブラックリストから特定のエンティティを削除する
    public func removeBlacklist(_ entity: BlacklistEntity) {
        RepositoryProvider.shared.dnsRepository.remove(entity)
        self.objectWillChange.send()
    }
    
    /// すべてのブラックリストルールをクリアする
    public func clearBlacklist() {
        RepositoryProvider.shared.dnsRepository.clearBlacklist()
        self.objectWillChange.send()
    }
}
