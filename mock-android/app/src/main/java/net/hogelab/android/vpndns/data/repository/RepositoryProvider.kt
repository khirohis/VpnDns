package net.hogelab.android.vpndns.data.repository

import net.hogelab.android.vpndns.domain.repository.BlacklistRepository
import net.hogelab.android.vpndns.domain.repository.DnsHistoryRepository

/**
 * リポジトリのインスタンスを提供するシングルトン
 * (将来的に Hilt 等に移行するまでの暫定的な管理)
 */
object RepositoryProvider {
    val dnsRepository: DnsHistoryRepository by lazy {
        InMemoryDnsHistoryRepository()
    }

    val blacklistRepository: BlacklistRepository by lazy {
        InMemoryBlacklistRepository()
    }
}
