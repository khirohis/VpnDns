package net.hogelab.android.vpndns.data.dns

import android.util.Log

/**
 * DNS パケットの解析を行うクラス
 */
object DnsPacketParser {
    private const val TAG = "DnsPacketParser"

    /**
     * DNS ペイロードからホスト名 (QNAME) を解析する
     *
     * @param dnsPayload UDP ペイロード (DNS パケット全体)
     * @return 解析されたホスト名。失敗した場合は null
     */
    fun parseHostName(dnsPayload: ByteArray): String? {
        try {
            // DNS ヘッダーは 12 バイト固定
            if (dnsPayload.size < 13) return null
            
            val sb = StringBuilder()
            var pos = 12 
            
            while (pos < dnsPayload.size) {
                val len = dnsPayload[pos].toInt() and 0xFF
                if (len == 0) break // QNAME の終端
                
                if (pos + 1 + len > dnsPayload.size) return null
                
                if (sb.isNotEmpty()) sb.append(".")
                val label = String(dnsPayload, pos + 1, len, Charsets.US_ASCII)
                sb.append(label)
                
                pos += (1 + len)
            }
            
            val result = sb.toString()
            return if (result.isEmpty()) null else result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse DNS QNAME", e)
            return null
        }
    }
}
