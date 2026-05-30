# VpnDns プロジェクト開発ルール

## プロジェクト概要

iOS の `NEPacketTunnelProvider` を利用し、DNS 制御を行うアプリケーション。

## 技術スタック

- **Language**: Swift (5.9+)
- **UI**: SwiftUI
- **Architecture**: Clean Architecture / Layered Architecture
  - **domain**: ビジネスロジック、リポジトリ契約 (`domain/repository/`)、およびモデル (`domain/model/`) を含む純粋なSwiftレイヤー。
  - **data**: リポジトリ具象実装 (`data/repository/`)、データソースやパーサーなどの具現化レイヤー。
  - **service**: Network Extension などのシステムサービスプロバイダ (`service/`)。
  - **presentation / di**: UIや依存性注入解決を行うプレゼンテーション層。

---

## 実装ガイドライン

### 1. NEPacketTunnelProvider 実装 (DnsVpnService)

- **スプリットトンネル設計 (Dummy IP Routing):**
  - システム全体のすべての通信（HTTP/HTTPS/TCP等）をVPNインターフェースに引き込んではならない。
  - `NEPacketTunnelNetworkSettings` において、ダミーの DNS サーバー IP（例: `198.18.0.1`）のみを設定し、`includedRoutes` に `198.18.0.1/32` のみを指定して、DNS クエリのみがトンネルにルーティングされるように構成すること。
- **15MB メモリ制限 (Strict Memory Limits):**
  - iOS Extension には **15MB** という非常に厳格な実行メモリ制限が存在する。大容量のブロックリストやメモリ上にキャッシュし続ける履歴データを無制限に保持してはならない。
  - メモリリークを防ぐため、DNS 要求の逆マッピングテーブル（`activeQueries`）などのキャッシュ情報は、解決完了後または一定秒数（例: 5秒）のタイムアウト経過後に必ず削除すること。
- **非同期とスレッドセーフティ:**
  - パケット受信ループはバックグラウンドのスレッドで常時稼働するため、共有されるデータソースやリポジトリへの読み書きは必ず `DispatchQueue` や Actor を用いて排他制御（Barrier書き込みなど）を行い、メモリ破壊を徹底的に防止すること。

### 2. DNS 処理 (DnsPacketParser)

- **プロトコル対応:**
  - IPv4 および IPv6 (UDP 53) の両方のフックと中継に対応すること。IPv6 環境での名前解決漏れ（DNS Leak）を防ぐため、IPv6 アドレスおよび IPv6 DNS サーバーのフックを必須とする。
  - UDP 53 で解決できない巨大な応答（例: 512バイト超のDNS応答でTCビットが立った場合など）に備え、TCP 53 トラフィックの中継対応も視野に入れた拡張性の高い設計にすること。
- **ブロックパケットのローカル合成:**
  - ブロック判定されたクエリに対しては、外部のアップストリームDNSサーバーに要求を転送してはならない。
  - `DnsPacketParser` を使用して、即座に解決先 `0.0.0.0` の A レコード（または `NXDOMAIN`）をカプセル化した UDP 応答パケットをローカルで構築し、システム側に直接書き戻す（`packetFlow.writePackets`）こと。

### 3. データ管理と UI 連携

- **プロセス境界と App Group 共有 (最重要):**
  - メインアプリプロセスと Network Extension プロセス（`DnsVpnService`）は、**完全に独立したメモリ空間**で動作している。インメモリでのシングルトン（`InMemoryDnsRepository`）はプロセス間でデータを共有できない。
  - Extension で発生した解決ログや、メインアプリで編集したブロックルールなどのデータを共有・連携する場合は、**App Group 共有コンテナ (`FileManager.default.containerURL(forSecurityApplicationGroupIdentifier:)`) 配下のファイル保存**、または `NETunnelProviderSession.sendProviderMessage` による IPC プロセス間通信を設計・実装すること。
- **依存関係の逆転 (DIP) と RepositoryProvider:**
  - ビューモデルやサービスなどのクライアントコードは、具体的なリポジトリ実装クラス（`InMemoryDnsRepository`）を直接生成または参照してはならない。
  - 必ず `RepositoryProvider.shared.dnsRepository` を介して、抽象プロトコルである `DnsRepository` 型としてインスタンスにアクセスし、疎結合な設計を維持すること。
- **柔軟なソート機能:**
  - ユーザーが通信傾向を分析できるよう、全ての統計フィールド（ホスト名、時刻、回数）において昇順・降順のソートを実装すること。

### 4. コードスタイル

- **不変性 (Immutability) の優先:**
  - ドメインエンティティ（`DnsEntity` など）を定義する際は、状態変更可能な `class` ではなく、不変な `struct` を原則として使用すること。
  - 状態を更新する場合は、元のプロパティを直接書き換えるのではなく、`incremented(at:with:)` のような新しいインスタンスを生成して返却する純粋関数（Pure Function）パターンのヘルパーを用意すること。
- **Swift 標準規約と外部 RawValue の相互変換:**
  - Swift 内での列挙型（`BlockType`）のケース名は、Swift 標準の `lowerCamelCase`（例: `.patternMatched`）を徹底すること。
  - 外部へのシリアライズ、永続化、またはログ書き出しの整合性を保つため、列挙型の Raw Value やデータベース保存時の表記には、規約で定められた大文字の文字列（例: `"PATTERN_MATCHED"`, `"EXACT"`, `"NONE"`) を定義すること。
