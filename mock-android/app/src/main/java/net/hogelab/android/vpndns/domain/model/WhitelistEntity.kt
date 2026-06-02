package net.hogelab.android.vpndns.domain.model

/**
 * ホワイトリストに登録されたドメイン
 */
data class WhitelistEntity(
    val hostName: String,
    val editTime: Long,
    val description: String = ""
)
