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

enum class BlockType {
    NONE,           // 未ブロック
    EXACT,          // 完全一致ブロック
    PATTERN_MATCHED // パターン一致ブロック
}

data class DnsHistoryEntity(
    val hostName: String,
    val firstTime: Long,
    val accessTime: Long,
    val requestCount: Int
)
