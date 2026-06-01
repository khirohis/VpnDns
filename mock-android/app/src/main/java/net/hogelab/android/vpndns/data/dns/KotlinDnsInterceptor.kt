package net.hogelab.android.vpndns.data.dns

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.hogelab.android.vpndns.domain.interceptor.DnsInterceptor
import net.hogelab.android.vpndns.domain.repository.BlacklistRepository
import net.hogelab.android.vpndns.domain.repository.DnsHistoryRepository
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Kotlin による DNS インターセプターの実装
 * パケットの解析から DNS リレー、応答パケットの構築までを担当する
 */
class KotlinDnsInterceptor(
    private val dnsRepository: DnsHistoryRepository,
    private val blacklistRepository: BlacklistRepository,
    private val protectSocket: (DatagramSocket) -> Unit
) : DnsInterceptor {

    private val interceptorScope = CoroutineScope(Dispatchers.IO)
    private var interceptionJob: Job? = null
    private var isRunning = false

    companion object {
        private const val TAG = "KotlinDnsInterceptor"
        private const val TAG_PACKET = "VpnPacketFlow"
    }

    override fun start(pfd: ParcelFileDescriptor) {
        if (isRunning) return
        isRunning = true

        interceptionJob = interceptorScope.launch {
            val fileDescriptor = pfd.fileDescriptor
            FileInputStream(fileDescriptor).use { input ->
                FileOutputStream(fileDescriptor).use { output ->
                    val buffer = ByteBuffer.allocate(32767)

                    try {
                        while (isRunning) {
                            val length = input.read(buffer.array())
                            if (length > 0) {
                                buffer.limit(length)
                                buffer.rewind()
                                
                                processPacket(buffer, output)
                                
                                buffer.clear()
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error in packet loop", e)
                        }
                    }
                }
            }
        }
        Log.i(TAG, "Interception started")
    }

    override fun stop() {
        isRunning = false
        interceptionJob?.cancel()
        interceptionJob = null
        Log.i(TAG, "Interception stopped")
    }

    private fun processPacket(packet: ByteBuffer, output: FileOutputStream) {
        val buffer = packet.array()
        val offset = packet.arrayOffset()
        val limit = packet.limit()

        if (limit < 20) return

        val ipVersion = (buffer[offset].toInt() shr 4) and 0x0F

        if (ipVersion == 4) {
            handleIPv4(buffer, offset, limit, output)
        } else if (ipVersion == 6 && limit >= 40) {
            handleIPv6(buffer, offset, limit, output)
        }
    }

    private fun handleIPv4(buffer: ByteArray, offset: Int, limit: Int, output: FileOutputStream) {
        val protocol = buffer[offset + 9].toInt() and 0xFF
        if (protocol != 17) return // UDP (17) のみ

        val ihl = (buffer[offset].toInt() and 0x0F) * 4
        val udpOffset = offset + ihl
        if (limit < udpOffset + 8) return

        val srcPort = ((buffer[udpOffset].toInt() and 0xFF) shl 8) or (buffer[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((buffer[udpOffset + 2].toInt() and 0xFF) shl 8) or (buffer[udpOffset + 3].toInt() and 0xFF)

        if (dstPort != 53) return

        val udpLen = ((buffer[udpOffset + 4].toInt() and 0xFF) shl 8) or (buffer[udpOffset + 5].toInt() and 0xFF)
        val dnsLen = udpLen - 8
        if (dnsLen <= 0 || limit < udpOffset + 8 + dnsLen) return

        val dnsPayload = ByteArray(dnsLen)
        System.arraycopy(buffer, udpOffset + 8, dnsPayload, 0, dnsLen)

        val srcIp = ByteArray(4)
        System.arraycopy(buffer, offset + 12, srcIp, 0, 4)
        val dstIp = ByteArray(4)
        System.arraycopy(buffer, offset + 16, dstIp, 0, 4)

        Log.d(TAG_PACKET, "DNS Query (v4): ${formatIp(srcIp)}:$srcPort -> ${formatIp(dstIp)}:$dstPort")

        interceptorScope.launch {
            val serverAddr = InetAddress.getByAddress(dstIp)
            val responseData = handleDnsQuery(dnsPayload, serverAddr, false)
            if (responseData != null) {
                val reply = buildReplyPacketV4(dstIp, dstPort, srcIp, srcPort, responseData)
                synchronized(output) {
                    try {
                        output.write(reply)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to write reply v4", e)
                    }
                }
            }
        }
    }

    private fun handleIPv6(buffer: ByteArray, offset: Int, limit: Int, output: FileOutputStream) {
        val nextHeader = buffer[offset + 6].toInt() and 0xFF
        if (nextHeader != 17) return // UDP (17) のみ

        val udpOffset = offset + 40
        if (limit < udpOffset + 8) return

        val srcPort = ((buffer[udpOffset].toInt() and 0xFF) shl 8) or (buffer[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((buffer[udpOffset + 2].toInt() and 0xFF) shl 8) or (buffer[udpOffset + 3].toInt() and 0xFF)

        if (dstPort != 53) return

        val udpLen = ((buffer[udpOffset + 4].toInt() and 0xFF) shl 8) or (buffer[udpOffset + 5].toInt() and 0xFF)
        val dnsLen = udpLen - 8
        if (dnsLen <= 0 || limit < udpOffset + 8 + dnsLen) return

        val dnsPayload = ByteArray(dnsLen)
        System.arraycopy(buffer, udpOffset + 8, dnsPayload, 0, dnsLen)

        val srcIp = ByteArray(16)
        System.arraycopy(buffer, offset + 8, srcIp, 0, 16)
        val dstIp = ByteArray(16)
        System.arraycopy(buffer, offset + 24, dstIp, 0, 16)

        Log.d(TAG_PACKET, "DNS Query (v6): [${formatIp(srcIp)}]:$srcPort -> [${formatIp(dstIp)}]:$dstPort")

        interceptorScope.launch {
            val serverAddr = InetAddress.getByAddress(dstIp)
            val responseData = handleDnsQuery(dnsPayload, serverAddr, true)
            if (responseData != null) {
                val reply = buildReplyPacketV6(dstIp, dstPort, srcIp, srcPort, responseData)
                synchronized(output) {
                    try {
                        output.write(reply)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to write reply v6", e)
                    }
                }
            }
        }
    }

    override suspend fun handleDnsQuery(
        query: ByteArray,
        serverAddr: InetAddress,
        isIPv6: Boolean
    ): ByteArray? {
        val dnsQuery = DnsPacketParser.parseQuery(query)
        if (dnsQuery != null) {
            // Service への通知（履歴への追加は Service が行う）
            dnsRepository.notifyDnsRequest(dnsQuery.hostName)

            if (blacklistRepository.isBlocked(dnsQuery.hostName)) {
                Log.i(TAG, "Blocked DNS Query: ${dnsQuery.hostName}")
                return buildBlockReply(dnsQuery)
            }
        }

        return try {
            relayDns(query, serverAddr)
        } catch (e: Exception) {
            Log.e(TAG, "DNS Relay error: ${e.message}")
            null
        }
    }

    private fun relayDns(query: ByteArray, serverAddr: InetAddress): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            protectSocket(socket)
            socket.soTimeout = 5000

            val outPacket = DatagramPacket(query, query.size, serverAddr, 53)
            socket.send(outPacket)
            Log.d(TAG, "Relay -> ${serverAddr.hostAddress} (${query.size} bytes)")

            val responseBuffer = ByteArray(4096)
            val inPacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(inPacket)
            Log.d(TAG, "Relay <- ${serverAddr.hostAddress} (${inPacket.length} bytes)")

            inPacket.data.copyOfRange(0, inPacket.length)
        } catch (e: Exception) {
            Log.e(TAG, "Relay error: ${e.message}")
            null
        } finally {
            socket?.close()
        }
    }

    private fun buildBlockReply(query: DnsQuery): ByteArray {
        val totalLen = 12 + query.rawQuestionSection.size
        val response = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(response)
        buffer.order(ByteOrder.BIG_ENDIAN)

        // 1. Header (12 bytes)
        buffer.putShort(query.transactionId)
        
        // Flags: 0x8183 (Standard response, NXDOMAIN)
        // クライアントの RD (Recursion Desired) ビットを維持しつつ、
        // QR (Response)=1, RA (Recursion Available)=1, RCODE=3 を設定
        val rdBit = if ((query.flags.toInt() and 0x0100) != 0) 0x0100 else 0
        val flags = (0x8000 or rdBit or 0x0080 or 0x0003).toShort()
        buffer.putShort(flags)

        buffer.putShort(1.toShort()) // QDCOUNT (1)
        buffer.putShort(0.toShort()) // ANCOUNT (0)
        buffer.putShort(0.toShort()) // NSCOUNT (0)
        buffer.putShort(0.toShort()) // ARCOUNT (0)

        // 2. Question Section (解析時に保存したものをそのままコピー)
        buffer.put(query.rawQuestionSection)

        return response
    }

    private fun formatIp(ip: ByteArray): String {
        return if (ip.size == 4) {
            ip.joinToString(".") { (it.toInt() and 0xFF).toString() }
        } else {
            ip.asIterable().chunked(2).joinToString(":") {
                ((it[0].toInt() and 0xFF shl 8) or (it[1].toInt() and 0xFF)).toString(16)
            }
        }
    }

    private fun buildReplyPacketV4(
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        data: ByteArray
    ): ByteArray {
        val udpLen = 8 + data.size
        val totalLen = 20 + udpLen
        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)
        buffer.order(ByteOrder.BIG_ENDIAN)

        // IP Header (v4)
        buffer.put(0x45.toByte()) // Version 4, IHL 5
        buffer.put(0x00.toByte()) // TOS
        buffer.putShort(totalLen.toShort())
        buffer.putShort(0.toShort()) // ID
        buffer.putShort(0x4000.toShort()) // Flags: Don't Fragment
        buffer.put(64.toByte()) // TTL
        buffer.put(17.toByte()) // Protocol: UDP
        buffer.putShort(0.toShort()) // Checksum Placeholder
        buffer.put(srcIp)
        buffer.put(dstIp)

        val ipChecksum = calculateChecksum(packet, 0, 20)
        buffer.putShort(10, ipChecksum)

        // UDP Header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0.toShort()) // Checksum Placeholder

        buffer.put(data)

        // UDP Checksum (IPv4 Pseudo Header)
        val udpChecksum = calculateUdpChecksumV4(srcIp, dstIp, packet, 20, udpLen)
        buffer.putShort(20 + 6, udpChecksum)

        return packet
    }

    private fun buildReplyPacketV6(
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        data: ByteArray
    ): ByteArray {
        val udpLen = 8 + data.size
        val totalLen = 40 + udpLen
        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)
        buffer.order(ByteOrder.BIG_ENDIAN)

        // IP Header (v6)
        buffer.putInt(0x60000000) // Version 6, Traffic Class 0, Flow Label 0
        buffer.putShort(udpLen.toShort())
        buffer.put(17.toByte()) // Next Header: UDP
        buffer.put(64.toByte()) // Hop Limit
        buffer.put(srcIp)
        buffer.put(dstIp)

        // UDP Header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpLen.toShort())
        buffer.putShort(0.toShort()) // Checksum Placeholder

        buffer.put(data)

        // UDP Checksum (IPv6 Pseudo Header - Mandatory)
        val udpChecksum = calculateUdpChecksumV6(srcIp, dstIp, packet, 40, udpLen)
        buffer.putShort(40 + 6, udpChecksum)

        return packet
    }

    /**
     * IP ヘッダー用のチェックサム計算 (RFC 791)
     */
    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        var len = length
        while (len > 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            len -= 2
        }
        if (len > 0) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }

    /**
     * IPv4 疑似ヘッダーを含む UDP チェックサム計算
     */
    private fun calculateUdpChecksumV4(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpPacket: ByteArray,
        udpOffset: Int,
        udpLen: Int
    ): Short {
        var sum = 0
        // Pseudo Header
        for (i in 0 until 4 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += 17 // Protocol UDP
        sum += udpLen

        // UDP Header + Data
        return calculateCombinedChecksum(sum, udpPacket, udpOffset, udpLen)
    }

    /**
     * IPv6 疑似ヘッダーを含む UDP チェックサム計算 (必須)
     */
    private fun calculateUdpChecksumV6(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpPacket: ByteArray,
        udpOffset: Int,
        udpLen: Int
    ): Short {
        var sum = 0
        // Pseudo Header (Addresses)
        for (i in 0 until 16 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            // 32bitを超えた桁上がりを16bit幅で考慮する必要があるため、計算の過程で正規化
            if ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
            
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
            if ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        }
        // Pseudo Header (Upper-Layer Packet Length)
        sum += udpLen
        if ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        
        // Pseudo Header (Next Header)
        sum += 17 // UDP
        if ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)

        // UDP Header + Data
        return calculateCombinedChecksum(sum, udpPacket, udpOffset, udpLen)
    }

    private fun calculateCombinedChecksum(initialSum: Int, data: ByteArray, offset: Int, length: Int): Short {
        var sum = initialSum
        var i = offset
        var len = length
        while (len > 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            len -= 2
        }
        if (len > 0) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = (sum.inv() and 0xFFFF).toShort()
        // UDP checksum 0 is transmitted as 0xFFFF in IPv6 (and IPv4)
        return if (result == 0.toShort()) 0xFFFF.toShort() else result
    }
}
