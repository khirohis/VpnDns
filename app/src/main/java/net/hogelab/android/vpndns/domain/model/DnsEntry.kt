package net.hogelab.android.vpndns.domain.model

enum class SortField {
    FIRST_SEEN, LAST_SEEN, REQUEST_COUNT, HOST_NAME
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

data class HistorySortConfig(
    val field: SortField = SortField.FIRST_SEEN,
    val order: SortOrder = SortOrder.DESCENDING
)

data class DnsEntry(
    val hostName: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val requestCount: Int,
    val isBlocked: Boolean = false
)
