# VpnDns プロジェクト開発ルール

## プロジェクト概要
Android の `VpnService` を利用し、DNS 制御を行うアプリケーション。

## 技術スタック
- **Language**: Kotlin (Modern Kotlin patterns: Coroutines, Flow)
- **UI**: Jetpack Compose
- **Architecture**: MVVM / Clean Architecture 推奨
- **Android SDK**:
  - `minSdkVersion`: 26 (Android 8.0) - Always-on VPN サポートのため
  - `targetSdkVersion`: 34 (Android 14) 以上

## 実装ガイドライン

### 1. VpnService 実装
- **Foreground Service**: Android 14 以降の制約に従い、`AndroidManifest.xml` で `android:foregroundServiceType="vpn"` を必ず指定すること。
- **Lifecycle**: VPN の接続状態を UI や他のコンポーネントが監視できるよう、接続状態は `StateFlow` 等で公開する。
- **Resource Management**: VPN 接続時はバッテリー消費に配慮し、不要なループやリソース保持を避ける。

### 2. DNS 処理
- DNS パケットのパースや転送は、パフォーマンスと安全性を考慮して実装する。
- ユーザーがカスタム DNS サーバーを指定できる柔軟性を持たせる。
- **IPv4/IPv6 対応**: 
    - 現時点では IPv4 (UDP 53) のフックと中継に専念する。
    - 将来的に IPv6 環境での名前解決（DNS Leak）を防ぐため、IPv6 アドレスおよび IPv6 DNS サーバーのフック対応を検討する。

### 3. UI / UX
- VPN の接続状態を通知エリアとアプリ内で明確に表示する。
- Android 13 (API 33) 以上の通知権限 (`POST_NOTIFICATIONS`) のリクエストを適切に行う。

### 4. コードスタイル
- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html) に準拠する。
- 複雑なロジックには KDoc を記述する。
