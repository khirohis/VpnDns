package net.hogelab.android.vpndns.domain.model

/**
 * ブラックリストに登録されたドメイン
 */
data class BlacklistEntry(
    val hostName: String,
    val addedAt: Long
)
