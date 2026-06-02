# VpnDns プロジェクト開発ルール

## プロジェクト概要
Android システムの DNS クエリを `VpnService` を利用してフックし、 Blacklist 条件にマッチした host は NXDOMAIN を返すことにより、利用したくない通信をブロックするアプリケーション。
DNS クエリはその timestamp や回数などを History 管理し、ユーザはその情報から Blacklist 登録/削除が行える。

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

### 1. コードスタイル
- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html) に準拠する。
- 複雑なロジックやプロトコル処理（DNS パケットパース等）には KDoc を記述する。

### 2. VpnService 実装
- **責務**: `VpnDnsService` はパケットのルーティングとライフサイクル管理に専念し、DNS パケット処理は独立したオブジェクトに分離すること。
- **Foreground Service**: Android 14 以降の制約に従い、`AndroidManifest.xml` で `android:foregroundServiceType="specialUse"` を指定し、適切な権限とプロパティを記述すること。
- **Lifecycle**: VPN の接続状態を UI や他のコンポーネントが監視できるよう、接続状態は `StateFlow` 等で公開する。
- **Resource Management**: 
    - VPN 接続時はバッテリー消費に配慮し、不要なループやリソース保持を避ける。
    - また Service stop 時には確実にリソースを解放しリークを避ける。

### 3. DNS 処理
- **責務**: DNS クエリの解釈とブラックリスト判定に専念し、History 管理やブラックリスト管理は行わない。
- **検索パフォーマンス**:
    - ブラックリストの判定はパケット処理ループ内で行われるため、判定ロジックは **O(1)** の計算量で実行できるよう高速なデータ構造を使用すること。
    - ただしワイルドカード判定においてはその限りではなく **Trie** 木など、なるべく高速に行えるよう配慮すること。
- **遮断戦略**: ブロック対象のクエリに対しては、単にパケットを破棄するのではなく、元のクエリの Transaction ID を維持し、質問セクションをコピーした上で RCODE 3 を設定した有効な DNS パケットを構築すること。
- **UI通知**: DNS 処理レイヤーは、クエリの発生、ホスト名、判定結果を非同期イベントとして発行し、管理レイヤー（Repository）がそれを購読して History を構築する設計とすること。これにより、処理レイヤーを統計ロジックから完全に分離する。
- **スレッド安全性**: 
    - TUN インターフェースへの書き込み（`output.write`）は `synchronized` 等で適切に排他制御を行うこと。
    - 書き込み処理は VPN 停止（ストリームのクローズ）と競合する可能性があるため、必ず `try-catch` で保護すること。
- **IPv4/IPv6 対応**: 
    - IPv4 および IPv6 (UDP 53) のフックと中継に対応する。
    - IPv6 環境での名前解決（DNS Leak）を防ぐため、IPv6 アドレスおよび IPv6 DNS サーバーのフックを必須とする。

### 4. History 管理
- **保存情報**: History （`DnsHistoryEntity`) には host 名 (`hostName`) と初回クエリのタイムスタンプ (`firstTime`)、最終クエリのタイムスタンプ (`accessTime`)、クエリ回数 (`requestCount`) を保持する。 
- **保持戦略**:
    - History は永続化せず VpnService の start でクリアする。
    - History の上限は 500 件までとし、それを超えた場合は最終クエリの accessTime が古い順に削除する。
- **追加**: History の追加、更新、上限（500件）管理、およびソート・フィルタリングのロジックは、管理レイヤー（Kotlin 層）の専任責務とする。DNS 処理からのイベントをトリガーとして実行し、処理レイヤーはイベントの発生源に徹すること。
- **クリア**:
    - History は任意のタイミングでクリア可能とし追加と排他処理します。
    - History リストにクリアする UI を持ちます。

### 5. Blacklist 管理
- **保存情報**: Blacklist （`BlacklistEntity`) には host 名 (`hostName`) と初回クエリのタイムスタンプ (`firstTime`)、一時的解除 (`isPending`) を保持する。
- **保持戦略**: Blacklist への追加、削除があった場合はテキストファイルとして永続化し、更新があったことをイベントとして DNS 処理に通知すること。通知を受けた処理レイヤー（Kotlin/Native）は、永続化ファイルから最新のリストを再ロードして判定に反映させる設計とする。
- **追加**: History から追加操作が行われた際にエントリーを追加する。
- **削除**: Blacklist で削除操作が行える。

### 6. Whitelist 管理
- **保存情報**: Whitelist （`WhitelistEntity`) には host 名 (`hostName`) と最終更新のタイムスタンプ (`editTime`)、説明文 (`description`) を保持する。
- **保持戦略**: Whitelist への追加、削除があった場合はテキストファイルとして保存する。
- **マッチング仕様**:
    - 完全一致に加え、ワイルドカード (`*`) による後方一致に対応すること。
    - `*.example.com` は、`example.com` 自体およびその配下の全てのサブドメイン（`sub.example.com` 等）に合致する仕様とする。
- **追加**: Whitelist は History から追加する。また追加済みのホストはその Whitelist 登録情報を編集できる。
- **編集・削除**: 
    - Whitelist の登録情報を表示するポップアップでホストおよび説明文の編集およびエントリーの削除が行える。
    - History エントリーが既存のホワイトリスト・ルール（ワイルドカード含む）に合致している場合、そのエントリーから該当するルールを直接特定し、編集・削除できること。

### 7. データ管理と UI 連携
- **リアクティブな状態管理**: 履歴やブロックリストの管理はリポジトリパターンを採用し、データの更新は `StateFlow` を通じて UI に通知すること。
- **柔軟なソート機能**: ユーザーが通信傾向を分析できるよう、全ての統計フィールド（ホスト名、時刻、回数）において昇順・降順のソートを実装すること。
- **通知権限**: Android 13 (API 33) 以上の通知権限 (`POST_NOTIFICATIONS`) のクエリを適切に行う。

## 拡張予定
- **DNS 処理**: ネイティブライブラリ (`.so`) に置き換えます。そのさいはJNIコールを最小限とするためネイティブでBlacklistを持ちHistoryはUI層へのイベントとして定義します。

## その他のルール
- **リポジトリ操作**: add,commit,push などは指示されない限り行わない。
- **プランニング**: プランニングを指示されたときにファイルを変更しない。
