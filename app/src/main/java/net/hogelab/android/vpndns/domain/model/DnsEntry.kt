package net.hogelab.android.vpndns.domain.model

enum class SortField {
    FIRST_SEEN, LAST_SEEN, REQUEST_COUNT, HOST_NAME
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

data class HistorySortConfig(
    val field: SortField = SortField.REQUEST_COUNT,
    val order: SortOrder = SortOrder.DESCENDING,
    val showBlocked: Boolean = true
)

data class DnsEntry(
    val hostName: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val requestCount: Int,
    val isBlocked: Boolean = false
)
