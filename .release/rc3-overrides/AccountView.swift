import SwiftUI
import AppKit

struct AccountView: View {
    @EnvironmentObject var store: ClosetStore
    @State private var deletePhrase = ""
    @State private var showDelete = false

    var body: some View {
        Form {
            Section("Account") {
                LabeledContent("Email", value: store.session?.user.email ?? "—")
                LabeledContent("User ID", value: store.session?.user.id.uuidString ?? "—")
                LabeledContent("Backend", value: "ClosetAI Cloud")
                Label("Session refresh token stored in macOS Keychain", systemImage: "key.fill")
            }

            Section("Data protection firewall") {
                SecurityRow(icon: "lock.shield.fill", title: "Private image storage", detail: "Every storage path is scoped to the authenticated user.")
                SecurityRow(icon: "person.badge.shield.checkmark", title: "Row-level security", detail: "Wardrobe, outfits, preferences, imports and presets are isolated by auth.uid().")
                SecurityRow(icon: "photo.badge.checkmark", title: "Image sanitization", detail: "Disk imports are decoded and rewritten before upload; EXIF metadata is removed.")
                SecurityRow(icon: "network.badge.shield.half.filled", title: "Remote URL firewall", detail: "HTTPS-only imports block private networks, unsafe redirects, oversized payloads and invalid image signatures.")
                SecurityRow(icon: "lock.rotation", title: "Server-side secrets", detail: "OpenAI and Google OAuth secrets never ship in the application.")
                SecurityRow(icon: "list.bullet.clipboard.fill", title: "Audit + rate limits", detail: "Sensitive operations are authenticated, rate-limited and auditable.")
            }

            Section("Connected services") {
                HStack {
                    Label("Google Drive", systemImage: "externaldrive")
                    Spacer()
                    Text(store.driveConnected ? "Connected" : (store.driveConfigured ? "Not connected" : "Admin setup required"))
                        .foregroundStyle(.secondary)
                }
                if store.driveConnected {
                    Button("Disconnect Google Drive", role: .destructive) {
                        Task { await store.disconnectDrive() }
                    }
                }
            }

            Section("Privacy controls") {
                Button("Export my ClosetAI data…") {
                    Task {
                        if let data = await store.exportAccountData() {
                            saveExport(data)
                        }
                    }
                }
                Button("Delete account and wardrobe…", role: .destructive) {
                    showDelete = true
                }
                Text("Exports exclude encrypted OAuth tokens. Account deletion requires explicit confirmation and removes private wardrobe files before deleting the cloud account.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Commercial deployment") {
                LabeledContent("Supabase", value: AppConfig.productionURL)
                LabeledContent("App domain", value: AppConfig.productionDomain)
                Text("The publishable key is safe to embed. Service-role, OpenAI and OAuth secrets remain server-side. App-store signing/notarization credentials are intentionally outside the repository.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section {
                Button("Sign out", role: .destructive) {
                    Task { await store.signOut() }
                }
            }
        }
        .formStyle(.grouped)
        .navigationTitle("Account & Security")
        .sheet(isPresented: $showDelete) {
            VStack(alignment: .leading, spacing: 16) {
                Text("Delete ClosetAI account").font(.title2.bold())
                Text("This permanently deletes the cloud account, wardrobe records and private wardrobe files. Type DELETE MY CLOSET to continue.")
                    .foregroundStyle(.secondary)
                TextField("DELETE MY CLOSET", text: $deletePhrase)
                    .textFieldStyle(.roundedBorder)
                HStack {
                    Button("Cancel") {
                        showDelete = false
                        deletePhrase = ""
                    }
                    Spacer()
                    Button("Delete permanently", role: .destructive) {
                        Task {
                            if await store.deleteAccountPermanently() {
                                showDelete = false
                                deletePhrase = ""
                            }
                        }
                    }
                    .disabled(deletePhrase != "DELETE MY CLOSET")
                }
            }
            .padding(24)
            .frame(width: 480)
        }
    }

    private func saveExport(_ data: Data) {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "ClosetAI-Export.json"
        panel.allowedContentTypes = [.json]
        if panel.runModal() == .OK, let url = panel.url {
            try? data.write(to: url, options: .atomic)
        }
    }
}

struct SecurityRow: View {
    let icon: String
    let title: String
    let detail: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).fontWeight(.semibold)
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}
