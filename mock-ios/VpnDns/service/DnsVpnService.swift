import Foundation
import NetworkExtension

public class DnsVpnService: NEPacketTunnelProvider {
    
    // クライアントのルーティング情報を保存するテーブル
    private struct ClientInfo {
        let address: String
        let port: UInt16
        let protocolFamily: NSNumber
    }
    
    private var activeQueries = [UInt16: ClientInfo]()
    private var upstreamSession: NWUDPSession?
    private var isRunning = false
    
    private let dummyDnsIP = "198.18.0.1"
    private let tunnelLocalIP = "198.18.0.2"
    private let dummyDnsIPv6 = "fd00::53"
    private let tunnelLocalIPv6 = "fd00::54"
    
    // IPC 用の一時バッファとロック (15MB メモリ制限対策)
    private let bufferLock = NSRecursiveLock()
    private var pendingQueries = [DnsEntity]()
    private let maxPendingQueries = 500
    
    // MARK: - Lifecycle
    
    public override func startTunnel(options: [String : NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        NSLog("[VpnDns] Starting packet tunnel provider...")
        
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: tunnelLocalIP)
        
        // 1. DNS 設定 (すべてのDNS解決をダミーIPに強制)
        let dnsSettings = NEDNSSettings(servers: [dummyDnsIP, dummyDnsIPv6])
        dnsSettings.matchDomains = [""] // すべてのドメインのクエリを強制ルーティング
        settings.dnsSettings = dnsSettings
        
        // 2. IPv4 ルーティング設定 (ダミーIP宛の通信のみトンネルに入れる)
        let ipv4Settings = NEIPv4Settings(addresses: [tunnelLocalIP], subnetMasks: ["255.255.255.0"])
        ipv4Settings.includedRoutes = [
            NEIPv4Route(destinationAddress: dummyDnsIP, subnetMask: "255.255.255.255")
        ]
        settings.ipv4Settings = ipv4Settings
        
        // 3. IPv6 ルーティング設定 (ダミーIPv6宛のDNS通信のみトンネルに入れる)
        let ipv6Settings = NEIPv6Settings(addresses: [tunnelLocalIPv6], networkPrefixLengths: [64])
        ipv6Settings.includedRoutes = [
            NEIPv6Route(destinationAddress: dummyDnsIPv6, networkPrefixLength: 128)
        ]
        settings.ipv6Settings = ipv6Settings
        
        // 4. 設定を適用してトンネルを開始
        setTunnelNetworkSettings(settings) { [weak self] error in
            guard let self = self else { return }
            
            if let error = error {
                NSLog("[VpnDns] Failed to set network settings: \(error.localizedDescription)")
                completionHandler(error)
                return
            }
            
            // アップストリーム用UDPセッション (Google Public DNS) の初期化
            let endpoint = NWHostEndpoint(hostname: "8.8.8.8", port: "53")
            self.upstreamSession = self.createUDPSession(to: endpoint, from: nil)
            
            // アップストリームからの応答ループを開始
            self.startUpstreamReadLoop()
            
            // クライアント（ローカル）からのパケットループを開始
            self.isRunning = true
            self.startPacketLoop()
            
            NSLog("[VpnDns] Tunnel started successfully with split-route DNS (IPv4/IPv6).")
            completionHandler(nil)
        }
    }
    
    public override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        NSLog("[VpnDns] Stopping tunnel...")
        isRunning = false
        upstreamSession = nil
        activeQueries.removeAll()
        completionHandler()
    }
    
    // MARK: - Packet Loops
    
    /// クライアント端末からの送信パケットを監視するループ
    private func startPacketLoop() {
        guard isRunning else { return }
        
        packetFlow.readPackets { [weak self] packets, protocols in
            guard let self = self else { return }
            
            for (index, packet) in packets.enumerated() {
                let proto = protocols[index]
                
                // IP/UDP/DNSクエリとしてパースを試みる
                if let query = DnsPacketParser.parse(rawPacket: packet) {
                    self.handleDnsQuery(query, protocolFamily: proto)
                } else {
                    // DNS 以外のパケットは基本来ない想定（ルートを絞っているため）だが、
                    // 安全のため何もしない、または破棄します
                }
            }
            
            // 再帰的にループを継続
            self.startPacketLoop()
        }
    }
    
    /// アップストリーム（8.8.8.8）からのDNS応答を監視するループ
    private func startUpstreamReadLoop() {
        guard let session = upstreamSession else { return }
        
        session.setReadHandler({ [weak self] datagrams, error in
            guard let self = self else { return }
            
            if let error = error {
                NSLog("[VpnDns] Upstream session read error: \(error.localizedDescription)")
                return
            }
            
            guard let datagrams = datagrams else { return }
            
            for dnsResponse in datagrams {
                self.handleUpstreamDnsResponse(dnsResponse)
            }
        }, maxDatagrams: 32)
    }
    
    // MARK: - DNS Processing
    
    /// クライアントからのクエリ処理
    private func handleDnsQuery(_ query: DnsPacketParser.ParsedDNSQuery, protocolFamily: NSNumber) {
        NSLog("[VpnDns] Capture DNS Query: [\(query.domainName)] from \(query.sourceAddress):\(query.sourcePort)")
        
        // テストのため一時的に常に false とする
        let isBlocked = false
        
        // 一時バッファにスレッドセーフに履歴を追加 (メインアプリ側で管理するため、直接リポジトリには保存しない)
        let dnsEntity = DnsEntity(
            hostName: query.domainName,
            firstSeen: Date(),
            lastSeen: Date(),
            requestCount: 1,
            blockType: isBlocked ? .exact : .none
        )
        
        bufferLock.lock()
        pendingQueries.append(dnsEntity)
        if pendingQueries.count > maxPendingQueries {
            pendingQueries.removeFirst()
        }
        bufferLock.unlock()
        
        if isBlocked {
            NSLog("[VpnDns] 🚫 Blocked domain: \(query.domainName)")
            // ブロック応答（0.0.0.0 を返すパケット）を即座に作成してシステムに返却
            let blockPacket = DnsPacketParser.createBlockResponsePacket(query: query)
            packetFlow.writePackets([blockPacket], withProtocols: [protocolFamily])
            return
        }
        
        // 応答のルーティング用にクライアント情報を保存
        activeQueries[query.transactionID] = ClientInfo(
            address: query.sourceAddress,
            port: query.sourcePort,
            protocolFamily: protocolFamily
        )
        
        // アップストリームに生のDNSメッセージをそのまま転送
        if let session = upstreamSession {
            session.writeMultipleDatagrams([query.dnsData]) { error in
                if let error = error {
                    NSLog("[VpnDns] Failed to write datagrams to upstream: \(error.localizedDescription)")
                }
            }
        } else {
            NSLog("[VpnDns] Upstream session is nil, query discarded.")
        }
    }
    
    /// アップストリームから返ってきた応答の処理
    private func handleUpstreamDnsResponse(_ dnsResponse: Data) {
        guard dnsResponse.count >= 2 else { return }
        
        // Transaction ID を抽出して送信元クライアントを特定
        let transactionID = dnsResponse.subdata(in: 0..<2).withUnsafeBytes {
            $0.load(as: UInt16.self).bigEndian
        }
        
        guard let clientInfo = activeQueries[transactionID] else {
            // マッチするクエリがない（またはタイムアウト等で削除済み）
            return
        }
        
        // 保存テーブルから削除（メモリリーク防止）
        activeQueries.removeValue(forKey: transactionID)
        
        // クライアント向けの IP/UDP レスポンスパケットを構築
        let dnsIP = clientInfo.address.contains(":") ? dummyDnsIPv6 : dummyDnsIP
        // 送信元はダミーIP (Port 53) とし、クライアントの元のアドレス/ポートに送り返す
        let responsePacket = DnsPacketParser.createResponsePacket(query: DnsPacketParser.ParsedDNSQuery(
            sourceAddress: clientInfo.address,
            destinationAddress: dnsIP,
            sourcePort: clientInfo.port,
            destinationPort: 53,
            transactionID: transactionID,
            flags: 0,
            domainName: "",
            qType: 0,
            qClass: 0,
            rawPacket: Data(),
            ipHeader: Data(),
            udpHeader: Data(),
            dnsData: Data()
        ), rawDnsResponse: dnsResponse)
        
        // パケットをシステムに書き戻す
        packetFlow.writePackets([responsePacket], withProtocols: [clientInfo.protocolFamily])
        NSLog("[VpnDns] Resolved and wrote DNS response back for Transaction ID: \(transactionID)")
    }
    
    // MARK: - IPC Communication
    
    public override func handleAppMessage(_ messageData: Data, completionHandler: ((Data?) -> Void)? = nil) {
        guard let messageString = String(data: messageData, encoding: .utf8) else {
            completionHandler?(nil)
            return
        }
        
        if messageString == "fetch_history" {
            bufferLock.lock()
            let queries = pendingQueries
            pendingQueries.removeAll()
            bufferLock.unlock()
            
            do {
                let jsonData = try JSONEncoder().encode(queries)
                completionHandler?(jsonData)
            } catch {
                NSLog("[VpnDns] Failed to encode pending queries: \(error.localizedDescription)")
                completionHandler?(nil)
            }
        } else {
            completionHandler?(nil)
        }
    }
}
