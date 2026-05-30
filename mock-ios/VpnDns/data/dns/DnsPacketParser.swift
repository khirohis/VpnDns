import Foundation
import Darwin

/// IPv4 / IPv6 / UDP / DNS の各パケットを解析・構築するユーティリティクラス
public struct DnsPacketParser {
    
    // MARK: - Models
    
    public struct ParsedDNSQuery {
        public let sourceAddress: String
        public let destinationAddress: String
        public let sourcePort: UInt16
        public let destinationPort: UInt16
        
        public let transactionID: UInt16
        public let flags: UInt16
        public let domainName: String
        public let qType: UInt16
        public let qClass: UInt16
        
        /// 元のパケットのIP/UDPヘッダーなどを含む全生データ
        public let rawPacket: Data
        /// IPヘッダー部分のデータ
        public let ipHeader: Data
        /// UDPヘッダー部分のデータ
        public let udpHeader: Data
        /// DNSメッセージ部分のデータ
        public let dnsData: Data
    }
    
    // MARK: - Parse Logic
    
    /// 生のIPパケットからDNSクエリをパースする
    public static func parse(rawPacket: Data) -> ParsedDNSQuery? {
        guard rawPacket.count >= 20 else { return nil } // IPヘッダー最小長 (IPv4)
        
        // 1. IPヘッダーの解析
        let versionAndHeaderLength = rawPacket[0]
        let ipVersion = versionAndHeaderLength >> 4
        
        if ipVersion == 4 {
            let ipHeaderLength = Int(versionAndHeaderLength & 0x0F) * 4
            guard rawPacket.count >= ipHeaderLength + 8 else { return nil } // IP + UDPヘッダー最小長
            
            let protocolByte = rawPacket[9]
            guard protocolByte == 17 else { return nil } // UDP (Protocol = 17) のみ対象
            
            // 送信元・送信先IPアドレスの抽出
            let srcIPBytes = rawPacket.subdata(in: 12..<16)
            let dstIPBytes = rawPacket.subdata(in: 16..<20)
            let sourceIP = ipToString(srcIPBytes)
            let destinationIP = ipToString(dstIPBytes)
            
            // 2. UDPヘッダーの解析
            let udpStartIndex = ipHeaderLength
            let srcPortBytes = rawPacket.subdata(in: udpStartIndex..<udpStartIndex+2)
            let dstPortBytes = rawPacket.subdata(in: udpStartIndex+2..<udpStartIndex+4)
            
            let sourcePort = srcPortBytes.withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            let destinationPort = dstPortBytes.withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            
            // DNSの宛先ポートは53
            guard destinationPort == 53 else { return nil }
            
            let udpHeader = rawPacket.subdata(in: udpStartIndex..<udpStartIndex+8)
            let dnsStartIndex = udpStartIndex + 8
            guard rawPacket.count >= dnsStartIndex + 12 else { return nil } // DNSヘッダー最小長 (12 bytes)
            
            let dnsData = rawPacket.subdata(in: dnsStartIndex..<rawPacket.count)
            
            // 3. DNSヘッダーの解析
            let transactionID = dnsData.subdata(in: 0..<2).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            let flags = dnsData.subdata(in: 2..<4).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            let qdCount = dnsData.subdata(in: 4..<6).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            
            guard qdCount > 0 else { return nil } // 質問数が1以上であること
            
            // 4. DNS Questionのパース（ドメイン名の抽出）
            var index = 12
            guard let (domain, nextIndex) = parseDomainName(dnsData: dnsData, startIndex: index) else { return nil }
            index = nextIndex
            
            guard dnsData.count >= index + 4 else { return nil }
            let qType = dnsData.subdata(in: index..<index+2).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            let qClass = dnsData.subdata(in: index+2..<index+4).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            
            let ipHeader = rawPacket.subdata(in: 0..<ipHeaderLength)
            
            return ParsedDNSQuery(
                sourceAddress: sourceIP,
                destinationAddress: destinationIP,
                sourcePort: sourcePort,
                destinationPort: destinationPort,
                transactionID: transactionID,
                flags: flags,
                domainName: domain,
                qType: qType,
                qClass: qClass,
                rawPacket: rawPacket,
                ipHeader: ipHeader,
                udpHeader: udpHeader,
                dnsData: dnsData
            )
        } else if ipVersion == 6 {
            // IPv6 header parsing (fixed size 40 bytes)
            guard rawPacket.count >= 40 + 8 else { return nil } // IPv6 (40) + UDP (8)
            
            let protocolByte = rawPacket[6] // Next Header byte
            guard protocolByte == 17 else { return nil } // UDP (Next Header = 17)
            
            // 送信元・送信先IPv6アドレスの抽出 (16 bytes each)
            let srcIPBytes = rawPacket.subdata(in: 8..<24)
            let dstIPBytes = rawPacket.subdata(in: 24..<40)
            let sourceIP = ipv6ToString(srcIPBytes)
            let destinationIP = ipv6ToString(dstIPBytes)
            
            // 2. UDPヘッダーの解析
            let udpStartIndex = 40
            let srcPortBytes = rawPacket.subdata(in: udpStartIndex..<udpStartIndex+2)
            let dstPortBytes = rawPacket.subdata(in: udpStartIndex+2..<udpStartIndex+4)
            
            let sourcePort = srcPortBytes.withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            let destinationPort = dstPortBytes.withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            
            // DNSの宛先ポートは53
            guard destinationPort == 53 else { return nil }
            
            let udpHeader = rawPacket.subdata(in: udpStartIndex..<udpStartIndex+8)
            let dnsStartIndex = udpStartIndex + 8
            guard rawPacket.count >= dnsStartIndex + 12 else { return nil } // DNSヘッダー最小長 (12 bytes)
            
            let dnsData = rawPacket.subdata(in: dnsStartIndex..<rawPacket.count)
            
            // 3. DNSヘッダーの解析
            let transactionID = dnsData.subdata(in: 0..<2).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            let flags = dnsData.subdata(in: 2..<4).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            let qdCount = dnsData.subdata(in: 4..<6).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            
            guard qdCount > 0 else { return nil }
            
            // 4. DNS Questionのパース（ドメイン名の抽出）
            var index = 12
            guard let (domain, nextIndex) = parseDomainName(dnsData: dnsData, startIndex: index) else { return nil }
            index = nextIndex
            
            guard dnsData.count >= index + 4 else { return nil }
            let qType = dnsData.subdata(in: index..<index+2).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            let qClass = dnsData.subdata(in: index+2..<index+4).withUnsafeBytes { $0.load(as: UInt16.self).bigEndian }
            
            let ipHeader = rawPacket.subdata(in: 0..<40)
            
            return ParsedDNSQuery(
                sourceAddress: sourceIP,
                destinationAddress: destinationIP,
                sourcePort: sourcePort,
                destinationPort: destinationPort,
                transactionID: transactionID,
                flags: flags,
                domainName: domain,
                qType: qType,
                qClass: qClass,
                rawPacket: rawPacket,
                ipHeader: ipHeader,
                udpHeader: udpHeader,
                dnsData: dnsData
            )
        } else {
            return nil
        }
    }
    
    // MARK: - Construct Logic (Responses)
    
    /// DNSクエリに対してブロック応答（NXDOMAIN）のUDPパケットを作成する
    public static func createBlockResponsePacket(query: ParsedDNSQuery) -> Data {
        // 1. DNS レスポンス部分の組み立て (NXDOMAIN - Name Error, RCODE = 3)
        var dnsResponse = Data()
        
        // Transaction ID (クエリと同じ)
        dnsResponse.append(toData(query.transactionID))
        
        // Flags: 0x8183 (Standard query response, Recursion Desired, Recursion Available, NXDOMAIN)
        let responseFlags: UInt16 = 0x8183
        dnsResponse.append(toData(responseFlags))
        
        // QDCOUNT (質問数) = 1
        let qdCount: UInt16 = 1
        dnsResponse.append(toData(qdCount))
        
        // ANCOUNT (回答数) = 0 for NXDOMAIN
        let anCount: UInt16 = 0
        dnsResponse.append(toData(anCount))
        
        // NSCOUNT / ARCOUNT = 0
        let zeroCount: UInt16 = 0
        dnsResponse.append(toData(zeroCount))
        dnsResponse.append(toData(zeroCount))
        
        // --- Question Section (クエリからコピー) ---
        let domainEndIndex = getDomainNameLength(dnsData: query.dnsData, startIndex: 12)
        let questionSectionLength = domainEndIndex + 4 // domain + QTYPE(2) + QCLASS(2)
        let questionData = query.dnsData.subdata(in: 12..<12+questionSectionLength)
        dnsResponse.append(questionData)
        
        // NXDOMAIN blocks don't need Answer records, keeping the packet super compact and protocol-agnostic.
        
        // 2. IP / UDP ヘッダーを付けてラップする
        if query.sourceAddress.contains(":") {
            return buildIPv6UDPPacket(
                payload: dnsResponse,
                srcIP: query.destinationAddress, // 送信先と送信元を入れ替える
                dstIP: query.sourceAddress,
                srcPort: query.destinationPort,
                dstPort: query.sourcePort
            )
        } else {
            return buildIPv4UDPPacket(
                payload: dnsResponse,
                srcIP: query.destinationAddress,
                dstIP: query.sourceAddress,
                srcPort: query.destinationPort,
                dstPort: query.sourcePort
            )
        }
    }
    
    /// 通常のDNS応答（パブリックDNSから受信したものなど）をフック元のクライアント向けにIP/UDPパケット化する
    public static func createResponsePacket(query: ParsedDNSQuery, rawDnsResponse: Data) -> Data {
        if query.sourceAddress.contains(":") {
            return buildIPv6UDPPacket(
                payload: rawDnsResponse,
                srcIP: query.destinationAddress,
                dstIP: query.sourceAddress,
                srcPort: query.destinationPort,
                dstPort: query.sourcePort
            )
        } else {
            return buildIPv4UDPPacket(
                payload: rawDnsResponse,
                srcIP: query.destinationAddress,
                dstIP: query.sourceAddress,
                srcPort: query.destinationPort,
                dstPort: query.sourcePort
            )
        }
    }
    
    // MARK: - Private Helpers
    
    private static func toData<T: FixedWidthInteger>(_ value: T) -> Data {
        var bigEndian = value.bigEndian
        return withUnsafeBytes(of: &bigEndian) { Data($0) }
    }
    
    /// IPv4アドレスのDataを文字列に変換する
    private static func ipToString(_ data: Data) -> String {
        return data.map { String($0) }.joined(separator: ".")
    }
    
    /// 文字列形式のIPv4アドレスを4バイトDataに変換する
    private static func stringToIP(_ ipString: String) -> Data {
        let parts = ipString.split(separator: ".").compactMap { UInt8($0) }
        guard parts.count == 4 else { return Data([0, 0, 0, 0]) }
        return Data(parts)
    }
    
    /// IPv6アドレスのDataを文字列に変換する
    private static func ipv6ToString(_ data: Data) -> String {
        var buffer = [CChar](repeating: 0, count: Int(INET6_ADDRSTRLEN))
        let rawBytes = Array(data)
        _ = rawBytes.withUnsafeBytes { ptr in
            inet_ntop(AF_INET6, ptr.baseAddress, &buffer, socklen_t(INET6_ADDRSTRLEN))
        }
        return String(cString: buffer)
    }
    
    /// 文字列形式のIPv6アドレスを16バイトDataに変換する
    private static func stringToIPv6(_ ipString: String) -> Data {
        var addr = in6_addr()
        let result = inet_pton(AF_INET6, ipString, &addr)
        if result == 1 {
            return withUnsafeBytes(of: &addr) { Data($0) }
        } else {
            return Data(repeating: 0, count: 16)
        }
    }
    
    /// DNSデータからラベル形式のドメイン名をパースする
    private static func parseDomainName(dnsData: Data, startIndex: Int) -> (String, Int)? {
        var domain = ""
        var index = startIndex
        
        while index < dnsData.count {
            let length = Int(dnsData[index])
            if length == 0 {
                index += 1
                break
            }
            
            // ポインタ圧縮形式 (0xc0) の簡易チェック（今回は簡易パースのためサポート対象外とし、失敗にする）
            if (length & 0xc0) == 0xc0 {
                return nil
            }
            
            guard dnsData.count >= index + 1 + length else { return nil }
            
            let labelData = dnsData.subdata(in: index + 1..<index + 1 + length)
            if let label = String(data: labelData, encoding: .ascii) {
                if !domain.isEmpty {
                    domain += "."
                }
                domain += label
            }
            
            index += 1 + length
        }
        
        return (domain, index)
    }
    
    /// DNSデータにおけるドメイン名部分（Question内）のバイト長を計測する
    private static func getDomainNameLength(dnsData: Data, startIndex: Int) -> Int {
        var index = startIndex
        while index < dnsData.count {
            let length = Int(dnsData[index])
            if length == 0 {
                index += 1
                break
            }
            index += 1 + length
        }
        return index - startIndex
    }
    
    /// UDPペイロードをIPv4/UDPパケットでカプセル化し、各ヘッダーとチェックサムを生成する
    public static func buildIPv4UDPPacket(payload: Data, srcIP: String, dstIP: String, srcPort: UInt16, dstPort: UInt16) -> Data {
        let ipHeaderLength = 20
        let udpHeaderLength = 8
        let totalLength = ipHeaderLength + udpHeaderLength + payload.count
        
        var packet = Data(repeating: 0, count: totalLength)
        
        // 1. IPv4 ヘッダー作成
        packet[0] = 0x45 // Version 4, Header Length 5 (20 bytes)
        packet[1] = 0x00 // TOS
        
        // Total Length
        let totalLenUInt16 = UInt16(totalLength)
        packet[2] = UInt8(totalLenUInt16 >> 8)
        packet[3] = UInt8(totalLenUInt16 & 0xFF)
        
        // Identification
        let ident = UInt16.random(in: 0...65535)
        packet[4] = UInt8(ident >> 8)
        packet[5] = UInt8(ident & 0xFF)
        
        packet[6] = 0x00 // Flags & Fragment Offset
        packet[7] = 0x00
        packet[8] = 64   // TTL
        packet[9] = 17   // Protocol: UDP (17)
        
        // IP Checksum (初期化: 0)
        packet[10] = 0x00
        packet[11] = 0x00
        
        // Source and Destination IP
        let srcIPData = stringToIP(srcIP)
        let dstIPData = stringToIP(dstIP)
        packet.replaceSubrange(12..<16, with: srcIPData)
        packet.replaceSubrange(16..<20, with: dstIPData)
        
        // IP チェックサムの計算と設定
        let ipHeaderSum = calculateChecksum(data: packet.subdata(in: 0..<20))
        packet[10] = UInt8(ipHeaderSum >> 8)
        packet[11] = UInt8(ipHeaderSum & 0xFF)
        
        // 2. UDP ヘッダー作成
        let udpStartIndex = 20
        
        // Ports
        packet[udpStartIndex] = UInt8(srcPort >> 8)
        packet[udpStartIndex+1] = UInt8(srcPort & 0xFF)
        packet[udpStartIndex+2] = UInt8(dstPort >> 8)
        packet[udpStartIndex+3] = UInt8(dstPort & 0xFF)
        
        // UDP Length
        let udpLen = UInt16(udpHeaderLength + payload.count)
        packet[udpStartIndex+4] = UInt8(udpLen >> 8)
        packet[udpStartIndex+5] = UInt8(udpLen & 0xFF)
        
        // UDP Checksum (計算して設定)
        let checksum = calculateIPv4UDPChecksum(
            payload: payload,
            srcIPData: srcIPData,
            dstIPData: dstIPData,
            srcPort: srcPort,
            dstPort: dstPort
        )
        packet[udpStartIndex+6] = UInt8(checksum >> 8)
        packet[udpStartIndex+7] = UInt8(checksum & 0xFF)
        
        // 3. ペイロード (DNS) の書き込み
        packet.replaceSubrange(28..<totalLength, with: payload)
        
        return packet
    }
    
    /// UDPペイロードをIPv6/UDPパケットでカプセル化し、各ヘッダーとチェックサムを生成する
    public static func buildIPv6UDPPacket(payload: Data, srcIP: String, dstIP: String, srcPort: UInt16, dstPort: UInt16) -> Data {
        let ipHeaderLength = 40
        let udpHeaderLength = 8
        let totalLength = ipHeaderLength + udpHeaderLength + payload.count
        
        var packet = Data(repeating: 0, count: totalLength)
        
        // 1. IPv6 ヘッダー作成
        packet[0] = 0x60 // Version 6, Traffic Class 0 (high nibble)
        packet[1] = 0x00 // Traffic Class 0 (low nibble) & Flow Label (first 4 bits)
        packet[2] = 0x00 // Flow Label (middle 8 bits)
        packet[3] = 0x00 // Flow Label (last 8 bits)
        
        // Payload Length (UDP Header + UDP Payload)
        let payloadLen = UInt16(udpHeaderLength + payload.count)
        packet[4] = UInt8(payloadLen >> 8)
        packet[5] = UInt8(payloadLen & 0xFF)
        
        packet[6] = 17   // Next Header: UDP (17)
        packet[7] = 64   // Hop Limit: 64
        
        // Source and Destination IP
        let srcIPData = stringToIPv6(srcIP)
        let dstIPData = stringToIPv6(dstIP)
        packet.replaceSubrange(8..<24, with: srcIPData)
        packet.replaceSubrange(24..<40, with: dstIPData)
        
        // 2. UDP ヘッダー作成
        let udpStartIndex = 40
        
        // Ports
        packet[udpStartIndex] = UInt8(srcPort >> 8)
        packet[udpStartIndex+1] = UInt8(srcPort & 0xFF)
        packet[udpStartIndex+2] = UInt8(dstPort >> 8)
        packet[udpStartIndex+3] = UInt8(dstPort & 0xFF)
        
        // UDP Length
        packet[udpStartIndex+4] = UInt8(payloadLen >> 8)
        packet[udpStartIndex+5] = UInt8(payloadLen & 0xFF)
        
        // UDP Checksum
        let checksum = calculateIPv6UDPChecksum(
            payload: payload,
            srcIPData: srcIPData,
            dstIPData: dstIPData,
            srcPort: srcPort,
            dstPort: dstPort
        )
        packet[udpStartIndex+6] = UInt8(checksum >> 8)
        packet[udpStartIndex+7] = UInt8(checksum & 0xFF)
        
        // 3. ペイロード (DNS) の書き込み
        packet.replaceSubrange(48..<totalLength, with: payload)
        
        return packet
    }
    
    // MARK: - Checksum Calculations
    
    /// インターネット・チェックサム (1's complement sum) の安全な計算 (アライメントエラー防止)
    private static func calculateChecksum(data: Data) -> UInt16 {
        var sum: UInt32 = 0
        let count = data.count
        
        for i in stride(from: 0, to: count - 1, by: 2) {
            let high = UInt32(data[i]) << 8
            let low = UInt32(data[i + 1])
            sum += (high | low)
        }
        
        if count % 2 == 1 {
            sum += UInt32(data[count - 1]) << 8
        }
        
        while (sum >> 16) != 0 {
            sum = (sum & 0xFFFF) + (sum >> 16)
        }
        
        return UInt16(~sum)
    }
    
    /// IPv4 UDP 擬似ヘッダーを用いたチェックサムの計算
    private static func calculateIPv4UDPChecksum(
        payload: Data,
        srcIPData: Data,
        dstIPData: Data,
        srcPort: UInt16,
        dstPort: UInt16
    ) -> UInt16 {
        var pseudoHeader = Data()
        pseudoHeader.append(srcIPData)
        pseudoHeader.append(dstIPData)
        pseudoHeader.append(Data([0, 17]))
        pseudoHeader.append(toData(UInt16(8 + payload.count)))
        
        var udpHeader = Data()
        udpHeader.append(toData(srcPort))
        udpHeader.append(toData(dstPort))
        udpHeader.append(toData(UInt16(8 + payload.count)))
        udpHeader.append(Data([0, 0]))
        
        var checkData = Data()
        checkData.append(pseudoHeader)
        checkData.append(udpHeader)
        checkData.append(payload)
        
        return calculateChecksum(data: checkData)
    }
    
    /// IPv6 UDP 擬似ヘッダーを用いたチェックサムの計算
    private static func calculateIPv6UDPChecksum(
        payload: Data,
        srcIPData: Data,
        dstIPData: Data,
        srcPort: UInt16,
        dstPort: UInt16
    ) -> UInt16 {
        let udpLen = UInt32(8 + payload.count)
        
        var pseudoHeader = Data()
        pseudoHeader.append(srcIPData)
        pseudoHeader.append(dstIPData)
        
        // UDP Length (4 bytes)
        pseudoHeader.append(toData(udpLen))
        
        // 3 bytes zero + 1 byte Next Header (17)
        pseudoHeader.append(Data([0, 0, 0, 17]))
        
        // UDP Header without checksum (8 bytes)
        var udpHeader = Data()
        udpHeader.append(toData(srcPort))
        udpHeader.append(toData(dstPort))
        udpHeader.append(toData(UInt16(8 + payload.count)))
        udpHeader.append(Data([0, 0])) // Checksum placeholder
        
        // Combine pseudoHeader + udpHeader + payload
        var checkData = Data()
        checkData.append(pseudoHeader)
        checkData.append(udpHeader)
        checkData.append(payload)
        
        let checksum = calculateChecksum(data: checkData)
        // If checksum is 0, return 0xFFFF as per RFC
        return checksum == 0 ? 0xFFFF : checksum
    }
}
