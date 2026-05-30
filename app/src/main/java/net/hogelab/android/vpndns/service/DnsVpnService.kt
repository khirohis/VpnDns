package net.hogelab.android.vpndns.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.hogelab.android.vpndns.MainActivity
import net.hogelab.android.vpndns.data.dns.DnsPacketParser
import net.hogelab.android.vpndns.data.repository.RepositoryProvider
import net.hogelab.android.vpndns.domain.repository.DnsRepository
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val dnsRepository: DnsRepository = RepositoryProvider.dnsRepository

    companion object {
        private const val TAG = "DnsVpnService"
        private const val TAG_PACKET = "VpnPacketFlow"
        private const val CHANNEL_ID = "vpn_service_channel"
        private const val NOTIFICATION_ID = 1

        private val _connectionState = MutableStateFlow(false)
        val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (_connectionState.value) return

        // 前処理としてブラックリストをロード
        dnsRepository.loadBlacklist(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        try {
            vpnInterface = Builder()
                .setSession("VpnDns")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addRoute("8.8.8.8", 32)
                // IPv6 の DNS 漏れを防ぐために Google の IPv6 DNS も追加
                .addDnsServer("2001:4860:4860::8888")
                .addRoute("2001:4860:4860::8888", 128)
                .establish()

            if (vpnInterface != null) {
                _connectionState.value = true
                Log.d(TAG, "VPN established: Hooking DNS (IPv4 & IPv6)")
                
                // パケット処理ループを開始
                startPacketLoop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            stopVpn()
        }
    }

    private fun startPacketLoop() {
        vpnJob = serviceScope.launch {
            val fileDescriptor = vpnInterface?.fileDescriptor ?: return@launch
            FileInputStream(fileDescriptor).use { input ->
                FileOutputStream(fileDescriptor).use { output ->
                    val buffer = ByteBuffer.allocate(32767)

                    try {
                        while (_connectionState.value) {
                            val length = input.read(buffer.array())
                            if (length > 0) {
                                buffer.limit(length)
                                buffer.rewind()
                                
                                // パケットをフックして処理
                                processPacket(buffer, output)
                                
                                buffer.clear()
                            }
                        }
                    } catch (e: Exception) {
                        if (_connectionState.value) {
                            Log.e(TAG, "Error in packet loop", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * DNS リクエストをフックして 8.8.8.8 へ中継する
     */
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

        val hostName = DnsPacketParser.parseHostName(dnsPayload)
        if (hostName != null) {
            dnsRepository.addHistory(hostName)
            
            if (dnsRepository.isBlocked(hostName)) {
                Log.i(TAG_PACKET, "Blocked DNS Query (v4): $hostName")
                serviceScope.launch {
                    val reply = buildBlockReplyV4(dnsPayload, srcIp, srcPort, dstIp, dstPort)
                    synchronized(output) {
                        try {
                            output.write(reply)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to write blocked reply v4", e)
                        }
                    }
                }
                return
            }
        }

        serviceScope.launch {
            relayDns(dnsPayload, srcIp, srcPort, dstIp, dstPort, output, false)
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

        val hostName = DnsPacketParser.parseHostName(dnsPayload)
        if (hostName != null) {
            dnsRepository.addHistory(hostName)

            if (dnsRepository.isBlocked(hostName)) {
                Log.i(TAG_PACKET, "Blocked DNS Query (v6): $hostName")
                serviceScope.launch {
                    val reply = buildBlockReplyV6(dnsPayload, srcIp, srcPort, dstIp, dstPort)
                    synchronized(output) {
                        try {
                            output.write(reply)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to write blocked reply v6", e)
                        }
                    }
                }
                return
            }
        }

        serviceScope.launch {
            relayDns(dnsPayload, srcIp, srcPort, dstIp, dstPort, output, true)
        }
    }

    private suspend fun relayDns(
        query: ByteArray,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        output: FileOutputStream,
        isIPv6: Boolean
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = 5000

            val serverAddr = if (isIPv6) {
                InetAddress.getByName("2001:4860:4860::8888")
            } else {
                InetAddress.getByName("8.8.8.8")
            }

            val outPacket = DatagramPacket(query, query.size, serverAddr, 53)
            socket.send(outPacket)
            Log.d(TAG_PACKET, "Relay -> ${serverAddr.hostAddress} (${query.size} bytes)")

            val responseBuffer = ByteArray(4096)
            val inPacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(inPacket)
            Log.d(TAG_PACKET, "Relay <- ${serverAddr.hostAddress} (${inPacket.length} bytes)")

            val responseData = inPacket.data.copyOfRange(0, inPacket.length)
            val ipPacket = if (isIPv6) {
                buildReplyPacketV6(dstIp, dstPort, srcIp, srcPort, responseData)
            } else {
                buildReplyPacketV4(dstIp, dstPort, srcIp, srcPort, responseData)
            }
            
            Log.d(TAG_PACKET, "Writing ${if (isIPv6) "v6" else "v4"} reply to TUN: ${ipPacket.size} bytes")
            synchronized(output) {
                try {
                    output.write(ipPacket)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write relay reply", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_PACKET, "DNS Relay error: ${e.message}")
        } finally {
            socket?.close()
        }
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

    /**
     * ブロックされた DNS クエリに対する応答パケット (v4) を作成する
     * 簡易的に 0.0.0.0 を返すか、単に応答しない (NXDOMAIN) 設定も可能だが、
     * ここではパケットを構成して 0.0.0.0 を返す。
     */
    private fun buildBlockReplyV4(
        query: ByteArray,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int
    ): ByteArray {
        // DNS 応答ペイロードの構築 (簡易版: 0.0.0.0)
        // 実際にはクエリパケットを元に Answer セクションを追加する必要がある。
        // ここでは実装を単純にするため、最小限のヘッダー書き換えを行う。
        val responseData = query.copyOf()
        if (responseData.size >= 4) {
            // Flags: 0x8183 (Standard query response, No such name)
            responseData[2] = 0x81.toByte()
            responseData[3] = 0x83.toByte()
        }
        return buildReplyPacketV4(dstIp, dstPort, srcIp, srcPort, responseData)
    }

    private fun buildBlockReplyV6(
        query: ByteArray,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int
    ): ByteArray {
        val responseData = query.copyOf()
        if (responseData.size >= 4) {
            responseData[2] = 0x81.toByte()
            responseData[3] = 0x83.toByte()
        }
        return buildReplyPacketV6(dstIp, dstPort, srcIp, srcPort, responseData)
    }

    private fun buildReplyPacketV4(
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        data: ByteArray
    ): ByteArray {
        val totalLen = 20 + 8 + data.size
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
        buffer.putShort((8 + data.size).toShort())
        buffer.putShort(0.toShort()) // Optional in IPv4

        buffer.put(data)
        return packet
    }

    private fun buildReplyPacketV6(
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        data: ByteArray
    ): ByteArray {
        val payloadLen = 8 + data.size
        val totalLen = 40 + payloadLen
        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)
        buffer.order(ByteOrder.BIG_ENDIAN)

        // IP Header (v6)
        buffer.putInt(0x60000000.toInt()) // Version 6, Traffic Class 0, Flow Label 0
        buffer.putShort(payloadLen.toShort())
        buffer.put(17.toByte()) // Next Header: UDP
        buffer.put(64.toByte()) // Hop Limit
        buffer.put(srcIp)
        buffer.put(dstIp)

        // UDP Header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(payloadLen.toShort())
        buffer.putShort(0.toShort()) // Checksum Placeholder (IPv6 UDP checksum should be calculated but often ignored by TUN)

        buffer.put(data)
        return packet
    }

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

    private fun stopVpn() {
        try {
            _connectionState.value = false
            
            // VPN 停止時にブラックリストを保存し、履歴をクリア
            dnsRepository.saveBlacklist(this)
            dnsRepository.clearHistory()

            vpnJob?.cancel()
            vpnJob = null
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "VPN stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VpnDns")
            .setContentText("DNS protection is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
