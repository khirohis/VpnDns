# VpnDns プロジェクト開発ルール

## プロジェクト概要
Android の `VpnService` を利用し、DNS 制御を行うアプリケーション。

## 技術スタック
- **Language**: Kotlin 2.x (Coroutines, Flow による非同期・リアクティブプログラミング)
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM / Layered Architecture
  - 状態管理には `StateFlow` / `SharedFlow` を活用する
- **Build System**: Gradle Kotlin DSL / Version Catalog (`libs.versions.toml`)
- **Android SDK**:
  - `minSdkVersion`: 26 (Android 8.0) - Always-on VPN サポートのため
  - `targetSdkVersion`: 37 (Android 15) 以上
  - `compileSdkVersion`: 37
- **Dependency Injection**: Hilt (将来的な導入を推奨)

## 実装ガイドライン

### 1. VpnService 実装
- **Foreground Service**: Android 14 以降の制約に従い、`AndroidManifest.xml` で `android:foregroundServiceType="specialUse"` を指定し、適切な権限とプロパティを記述すること。
- **Lifecycle**: VPN の接続状態を UI や他のコンポーネントが監視できるよう、接続状態は `StateFlow` 等で公開する。
- **Resource Management**: 
    - VPN 接続時はバッテリー消費に配慮し、不要なループやリソース保持を避ける。
    - `FileInputStream` / `FileOutputStream` 等のストリームリソース is `use` ブロック等を用いて確実にクローズし、リークを防ぐ。

### 2. DNS 処理
- **責務の分離**: `DnsVpnService` はパケットのルーティングとライフサイクル管理に専念させ、DNS パケットのパースロジックは `DnsPacketParser` 等の独立したオブジェクトに分離すること。
- **検索パフォーマンス**: ブラックリストの判定はパケット処理ループ内で行われるため、判定ロジック（`isBlocked`）は **O(1)** の計算量で実行できるよう、`ConcurrentHashMap` 等の高速なデータ構造を使用すること。
- **遮断戦略**: ブロック対象のクエリに対しては、単にパケットを破棄するのではなく、即座に **NXDOMAIN** (RCODE 3) 等の DNS 応答を返却することで、クライアント側のタイムアウト待ちを回避し UX を向上させること。
- **スレッド安全性**: 
    - TUN インターフェースへの書き込み（`output.write`）は `synchronized` 等で適切に排他制御を行うこと。
    - 書き込み処理は VPN 停止（ストリームのクローズ）と競合する可能性があるため、必ず `try-catch` で保護すること。
- **IPv4/IPv6 対応**: 
    - IPv4 および IPv6 (UDP 53) のフックと中継に対応する。
    - IPv6 環境での名前解決（DNS Leak）を防ぐため、IPv6 アドレスおよび IPv6 DNS サーバーのフックを必須とする。

### 3. データ管理と UI 連携
- **リアクティブな状態管理**: 履歴やブロックリストの管理はリポジトリパターンを採用し、データの更新は `StateFlow` を通じて UI に通知すること。
- **統計情報の保持**: 履歴データ（`DnsEntity`）には、ドメイン名の他に以下の情報を持たせ、ユーザーのブロック判断を支援すること。
    - `firstSeen` / `lastSeen`: 通信の発生時期。
    - `requestCount`: 通信頻度（異常なリクエストの検知）。
- **柔軟なソート機能**: ユーザーが通信傾向を分析できるよう、全ての統計フィールド（ホスト名、時刻、回数）において昇順・降順のソートを実装すること。
- **通知権限**: Android 13 (API 33) 以上の通知権限 (`POST_NOTIFICATIONS`) のリクエストを適切に行う。

### 4. コードスタイル
- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html) に準拠する。
- 複雑なロジックやプロトコル処理（DNS パケットパース等）には KDoc を記述する。
