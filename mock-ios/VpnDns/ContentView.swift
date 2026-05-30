//
//  ContentView.swift
//  VpnDns
//
//  Created by 小林博久 on 2026/05/30.
//

import SwiftUI
import NetworkExtension
#if canImport(UIKit)
import UIKit
#endif

struct ContentView: View {
    @ObservedObject private var vpnManager = VpnManager.shared
    
    // Tab selection
    @State private var selectedTab: Int = 0
    
    // Settings states
    @State private var selectedUpstream: String = "Google Public DNS (8.8.8.8)"
    let upstreamOptions = [
        "Google Public DNS (8.8.8.8)",
        "Cloudflare DNS (1.1.1.1)",
        "AdGuard DNS (94.140.14.14)",
        "Custom Upstream"
    ]
    @State private var customUpstreamIP: String = ""
    
    var body: some View {
        TabView(selection: $selectedTab) {
            HomeTab(vpnManager: vpnManager)
                .tabItem {
                    Label("ホーム", systemImage: "house.fill")
                }
                .tag(0)
            
            HistoryTab(vpnManager: vpnManager)
                .tabItem {
                    Label("履歴", systemImage: "list.bullet.rectangle.portrait")
                }
                .tag(1)
            
            BlacklistTab(vpnManager: vpnManager)
                .tabItem {
                    Label("リスト", systemImage: "shield.xmark.fill")
                }
                .tag(2)
            
            SettingsTab(selectedUpstream: $selectedUpstream, customUpstreamIP: $customUpstreamIP, upstreamOptions: upstreamOptions)
                .tabItem {
                    Label("設定", systemImage: "gearshape.fill")
                }
                .tag(3)
        }
        .tint(.blue)
    }
}

// MARK: - Home Tab

struct HomeTab: View {
    @ObservedObject var vpnManager: VpnManager
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // Status Shield Card with vibrant gradient background
                    VStack(spacing: 16) {
                        Image(systemName: vpnManager.status == .connected ? "shield.fill" : "shield.slash")
                            .font(.system(size: 72))
                            .foregroundColor(.white)
                            .shadow(color: vpnManager.status == .connected ? .green.opacity(0.5) : .gray.opacity(0.5), radius: 10, x: 0, y: 5)
                            .padding(.top, 24)
                        
                        Text(vpnManager.status == .connected ? "DNS 保護アクティブ" : "DNS 保護無効")
                            .font(.title2)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        
                        Text(vpnManager.status == .connected ? "システム全体のDNSリクエストを安全にインターセプトし、ブロックしています。" : "DNS保護が無効です。安全な通信とブロックルールは適用されていません。")
                            .font(.footnote)
                            .foregroundColor(.white.opacity(0.85))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                            .padding(.bottom, 24)
                    }
                    .frame(maxWidth: .infinity)
                    .background(
                        LinearGradient(
                            gradient: Gradient(colors: vpnManager.status == .connected ? [Color.green, Color.blue] : [Color.gray, Color.black.opacity(0.8)]),
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .cornerRadius(20)
                    .shadow(radius: 6)
                    .padding(.horizontal)
                    .padding(.top, 16)
                    
                    // Toggle section
                    VStack(alignment: .leading, spacing: 12) {
                        Text("接続設定")
                            .font(.headline)
                            .foregroundColor(.secondary)
                            .padding(.horizontal)
                        
                        VStack(spacing: 0) {
                            HStack {
                                Image(systemName: vpnManager.status == .connected ? "power.circle.fill" : "power.circle")
                                    .font(.title2)
                                    .foregroundColor(vpnManager.status == .connected ? .green : .gray)
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("DNS 保護サービス")
                                        .fontWeight(.medium)
                                    Text(vpnManager.statusDescription)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                
                                Spacer()
                                
                                Toggle("", isOn: Binding(
                                    get: { vpnManager.status == .connected },
                                    set: { newValue in
                                        toggleVpn(to: newValue)
                                    }
                                ))
                                .labelsHidden()
                                .disabled(vpnManager.isPending)
                            }
                            .padding(.all, 16)
                            #if canImport(UIKit)
                            .background(Color(UIColor.secondarySystemGroupedBackground))
                            #else
                            .background(Color.secondary.opacity(0.1))
                            #endif
                        }
                        .cornerRadius(12)
                        .padding(.horizontal)
                    }
                    
                    // Info card
                    VStack(alignment: .leading, spacing: 12) {
                        Text("ローカルDNSプロキシの仕組み")
                            .font(.headline)
                            .foregroundColor(.secondary)
                            .padding(.horizontal)
                        
                        VStack(alignment: .leading, spacing: 14) {
                            infoRow(icon: "lock.shield.fill", color: .blue, title: "プライバシー保護", text: "クエリはデバイス上のローカルVPNトンネル（198.18.0.1 / fd00::53）で処理され、外部へのDNS漏洩を完全に防ぎます。")
                            Divider()
                            infoRow(icon: "bolt.fill", color: .orange, title: "超高速な処理", text: "DNSパケットはローカルの超軽量なC言語ライブラリ互換パケットパーサーにより解析され、ミリ秒未満のレイテンシで解決します。")
                            Divider()
                            infoRow(icon: "hand.raised.fill", color: .red, title: "自動トラッキング防止", text: "ブラックリストに含まれる不要な接続要求は、即座に NXDOMAIN（ドメイン不在）をローカルで返却して遮断します。")
                        }
                        .padding(.all, 16)
                        #if canImport(UIKit)
                        .background(Color(UIColor.secondarySystemGroupedBackground))
                        #else
                        .background(Color.secondary.opacity(0.1))
                        #endif
                        .cornerRadius(12)
                        .padding(.horizontal)
                    }
                    
                    Spacer()
                }
            }
            #if canImport(UIKit)
            .background(Color(UIColor.systemGroupedBackground))
            #else
            .background(Color.gray.opacity(0.1))
            #endif
            .navigationTitle("ホーム")
        }
    }
    
    private func toggleVpn(to newValue: Bool) {
        if newValue {
            Task {
                await vpnManager.startVpn()
            }
        } else {
            vpnManager.stopVpn()
        }
    }
    
    @ViewBuilder
    private func infoRow(icon: String, color: Color, title: String, text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(color)
                .frame(width: 24, alignment: .center)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.bold)
                Text(text)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

// MARK: - History Tab

struct HistoryTab: View {
    @ObservedObject var vpnManager: VpnManager
    
    // ソートキーとソート順のローカル状態
    enum SortKey {
        case hostName
        case lastSeen
        case requestCount
    }
    
    enum SortOrder {
        case ascending
        case descending
    }
    
    @State private var sortKey: SortKey = .lastSeen
    @State private var sortOrder: SortOrder = .descending
    
    // 動的にソートされた履歴の計算プロパティ
    private var sortedHistory: [DnsEntity] {
        vpnManager.history.sorted { a, b in
            switch sortKey {
            case .hostName:
                return sortOrder == .ascending ? a.hostName < b.hostName : a.hostName > b.hostName
            case .lastSeen:
                return sortOrder == .ascending ? a.lastSeen < b.lastSeen : a.lastSeen > b.lastSeen
            case .requestCount:
                return sortOrder == .ascending ? a.requestCount < b.requestCount : a.requestCount > b.requestCount
            }
        }
    }
    
    var body: some View {
        NavigationStack {
            List {
                Section {
                    if sortedHistory.isEmpty {
                        HStack {
                            Spacer()
                            Text("クエリ履歴はありません")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .padding(.vertical, 12)
                            Spacer()
                        }
                    } else {
                        // 並び替えコントローラ
                        HStack {
                            Text("並び替え:")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            
                            Spacer()
                            
                            HStack(spacing: 6) {
                                sortButton(title: "ホスト", key: .hostName)
                                sortButton(title: "時間", key: .lastSeen)
                                sortButton(title: "回数", key: .requestCount)
                            }
                        }
                        .padding(.vertical, 4)
                        
                        ForEach(sortedHistory) { query in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(query.hostName)
                                        .font(.system(.body, design: .monospaced))
                                        .foregroundColor(.primary)
                                        .lineLimit(1)
                                        .truncationMode(.middle)
                                    
                                    Text("最終検知: \(formattedDate(query.lastSeen))")
                                        .font(.system(size: 11, design: .default))
                                        .foregroundColor(.secondary)
                                }
                                
                                Spacer()
                                
                                HStack(spacing: 8) {
                                    if query.blockType != .none {
                                        Text("BLOCKED")
                                            .font(.system(size: 9, weight: .bold))
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color.red)
                                            .cornerRadius(4)
                                    }
                                    
                                    Text("\(query.requestCount)回")
                                        .font(.system(.subheadline, design: .rounded))
                                        .foregroundColor(.secondary)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 2)
                                        .background(Color.secondary.opacity(0.1))
                                        .cornerRadius(8)
                                }
                            }
                            .padding(.vertical, 2)
                        }
                    }
                } header: {
                    HStack {
                        Text("DNS クエリ履歴")
                        Spacer()
                        if !sortedHistory.isEmpty {
                            Button(role: .destructive, action: {
                                withAnimation {
                                    vpnManager.clearHistory()
                                }
                            }) {
                                Text("履歴クリア")
                                    .font(.caption)
                                    .foregroundColor(.red)
                            }
                        }
                    }
                }
            }
            .navigationTitle("履歴")
        }
    }
    
    // MARK: - Helper Views
    
    @ViewBuilder
    private func sortButton(title: String, key: SortKey) -> some View {
        Button(action: {
            withAnimation(.easeInOut(duration: 0.2)) {
                toggleSort(key: key)
            }
        }) {
            HStack(spacing: 2) {
                Text(title)
                if sortKey == key {
                    Image(systemName: sortOrder == .ascending ? "chevron.up" : "chevron.down")
                }
            }
            .font(.caption2)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(sortKey == key ? Color.blue.opacity(0.15) : Color.secondary.opacity(0.08))
            .foregroundColor(sortKey == key ? .blue : .primary)
            .cornerRadius(6)
        }
        .buttonStyle(.plain)
    }
    
    private func toggleSort(key: SortKey) {
        if sortKey == key {
            sortOrder = (sortOrder == .ascending) ? .descending : .ascending
        } else {
            sortKey = key
            sortOrder = .descending // デフォルトは降順
        }
    }
    
    private func formattedDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        return formatter.string(from: date)
    }
}

// MARK: - Blacklist Tab

struct BlacklistTab: View {
    @ObservedObject var vpnManager: VpnManager
    
    @State private var newDomain: String = ""
    @State private var searchQuery: String = ""
    
    private var filteredBlacklist: [BlacklistEntity] {
        if searchQuery.isEmpty {
            return vpnManager.blacklist
        } else {
            return vpnManager.blacklist.filter { $0.hostName.lowercased().contains(searchQuery.lowercased()) }
        }
    }
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Rule addition bar
                HStack(spacing: 10) {
                    TextField("追加するドメイン名 (例: doubleclick.net)", text: $newDomain)
                        .textFieldStyle(.roundedBorder)
                        .font(.system(size: 15, design: .monospaced))
                    
                    Button(action: addRule) {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                            Text("追加")
                        }
                        .fontWeight(.medium)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(newDomain.isEmpty ? Color.gray.opacity(0.3) : Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                    }
                    .disabled(newDomain.isEmpty)
                }
                .padding()
                #if canImport(UIKit)
                .background(Color(UIColor.secondarySystemGroupedBackground))
                #else
                .background(Color.secondary.opacity(0.1))
                #endif
                
                // Search bar
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.secondary)
                    TextField("リスト内検索...", text: $searchQuery)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                #if canImport(UIKit)
                .background(Color(UIColor.tertiarySystemGroupedBackground))
                #else
                .background(Color.secondary.opacity(0.15))
                #endif
                .cornerRadius(10)
                .padding(.horizontal)
                .padding(.bottom, 10)
                
                // Rules list
                List {
                    Section {
                        if filteredBlacklist.isEmpty {
                            HStack {
                                Spacer()
                                Text(searchQuery.isEmpty ? "ブラックリストは空です" : "一致するルールが見つかりません")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                                    .padding(.vertical, 16)
                                Spacer()
                            }
                        } else {
                            ForEach(filteredBlacklist) { rule in
                                HStack {
                                    Image(systemName: "shield.xmark")
                                        .foregroundColor(.red)
                                    
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(rule.hostName)
                                            .font(.system(size: 15, weight: .regular, design: .monospaced))
                                        
                                        Text("追加日: \(formattedDate(rule.addedAt))")
                                            .font(.caption2)
                                            .foregroundColor(.secondary)
                                    }
                                    
                                    Spacer()
                                    
                                    Text("完全一致")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(.secondary)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.secondary.opacity(0.15))
                                        .cornerRadius(4)
                                    
                                    Button(action: { deleteRule(rule) }) {
                                        Image(systemName: "trash")
                                            .foregroundColor(.red)
                                    }
                                    .buttonStyle(.plain)
                                }
                                .padding(.vertical, 4)
                            }
                            .onDelete(perform: deleteRules)
                        }
                    } header: {
                        HStack {
                            Text("ブロックルール (\(vpnManager.blacklist.count) 件)")
                            Spacer()
                            if !vpnManager.blacklist.isEmpty {
                                Button(role: .destructive, action: {
                                    withAnimation {
                                        vpnManager.clearBlacklist()
                                    }
                                }) {
                                    Text("一括クリア")
                                        .font(.caption)
                                        .foregroundColor(.red)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                #if os(iOS)
                .listStyle(.grouped)
                #endif
            }
            #if canImport(UIKit)
            .background(Color(UIColor.systemGroupedBackground))
            #else
            .background(Color.gray.opacity(0.1))
            #endif
            .navigationTitle("リスト")
        }
    }
    
    private func addRule() {
        let cleaned = newDomain.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleaned.isEmpty else { return }
        
        if !vpnManager.blacklist.contains(where: { $0.hostName == cleaned }) {
            withAnimation {
                vpnManager.addBlacklist(hostName: cleaned)
            }
        }
        newDomain = ""
    }
    
    private func deleteRule(_ rule: BlacklistEntity) {
        withAnimation {
            vpnManager.removeBlacklist(rule)
        }
    }
    
    private func deleteRules(at offsets: IndexSet) {
        let targets = offsets.map { filteredBlacklist[$0] }
        withAnimation {
            for target in targets {
                vpnManager.removeBlacklist(target)
            }
        }
    }
    
    private func formattedDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy/MM/dd HH:mm"
        return formatter.string(from: date)
    }
}

// MARK: - Settings Tab

struct SettingsTab: View {
    @Binding var selectedUpstream: String
    @Binding var customUpstreamIP: String
    let upstreamOptions: [String]
    
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("プロトコル/サーバー", selection: $selectedUpstream) {
                        ForEach(upstreamOptions, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                    .pickerStyle(.menu)
                    
                    if selectedUpstream == "Custom Upstream" {
                        HStack {
                            Text("カスタム IP")
                            Spacer()
                            TextField("8.8.4.4", text: $customUpstreamIP)
                                .multilineTextAlignment(.trailing)
                                .font(.system(size: 15, design: .monospaced))
                                .frame(width: 150)
                        }
                    }
                } header: {
                    Text("アップストリーム DNS 設定")
                } footer: {
                    Text("ブロックされていないDNSクエリは、この上流DNSサーバーに安全に転送されます。")
                }
                
                Section {
                    HStack {
                        Text("IPv4 トンネルローカル")
                        Spacer()
                        Text("198.18.0.2")
                            .font(.system(size: 15, weight: .regular, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                    HStack {
                        Text("IPv4 ダミーDNS")
                        Spacer()
                        Text("198.18.0.1")
                            .font(.system(size: 15, weight: .regular, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                    HStack {
                        Text("IPv6 トンネルローカル")
                        Spacer()
                        Text("fd00::54")
                            .font(.system(size: 15, weight: .regular, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                    HStack {
                        Text("IPv6 ダミーDNS")
                        Spacer()
                        Text("fd00::53")
                            .font(.system(size: 15, weight: .regular, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                } header: {
                    Text("ルーティング IP 設定")
                } footer: {
                    Text("スプリットトンネリング（Split Tunnel）により、このダミーIP宛ての通信のみが安全に保護されます。")
                }
                
                Section {
                    HStack {
                        Text("一時バッファ上限")
                        Spacer()
                        Text("500 件")
                            .foregroundColor(.secondary)
                    }
                    HStack {
                        Text("実行メモリ制限")
                        Spacer()
                        Text("15.0 MB")
                            .foregroundColor(.secondary)
                    }
                    HStack {
                        Text("アプリバージョン")
                        Spacer()
                        Text("v1.2.0")
                            .foregroundColor(.secondary)
                    }
                } header: {
                    Text("システム詳細情報")
                }
            }
            .navigationTitle("設定")
        }
    }
}

#Preview {
    ContentView()
}
