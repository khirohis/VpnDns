package net.hogelab.android.vpndns.data.dns

/**
 * 解析された DNS クエリ情報
 */
data class DnsQuery(
    val transactionId: Short,
    val flags: Short,
    val hostName: String,
    val qType: Int,
    val qClass: Int,
    val rawQuestionSection: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DnsQuery

        if (transactionId != other.transactionId) return false
        if (flags != other.flags) return false
        if (hostName != other.hostName) return false
        if (qType != other.qType) return false
        if (qClass != other.qClass) return false
        if (!rawQuestionSection.contentEquals(other.rawQuestionSection)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transactionId.toInt()
        result = 31 * result + flags.toInt()
        result = 31 * result + hostName.hashCode()
        result = 31 * result + qType
        result = 31 * result + qClass
        result = 31 * result + rawQuestionSection.contentHashCode()
        return result
    }
}
