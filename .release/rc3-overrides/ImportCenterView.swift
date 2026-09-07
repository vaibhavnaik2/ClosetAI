import SwiftUI
import UniformTypeIdentifiers
import AppKit

struct ImportCenterView: View {
    @EnvironmentObject var store: ClosetStore
    @State private var remoteURL = ""
    @State private var targeted = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack {
                    VStack(alignment: .leading) {
                        Text("Import Center").font(.largeTitle.bold())
                        Text("Disk folders, secure links and Google Drive").foregroundStyle(.secondary)
                    }
                    Spacer()
                }

                HStack(alignment: .top, spacing: 18) {
                    VStack(spacing: 16) {
                        VStack(spacing: 14) {
                            Image(systemName: "square.and.arrow.down.on.square").font(.system(size: 46))
                            Text("Drop clothing photos or folders").font(.title2.bold())
                            Text("ClosetAI decodes, resizes and rewrites local images before upload, stripping EXIF metadata. OCR label text is extracted locally and sent only as classification evidence.")
                                .multilineTextAlignment(.center)
                                .foregroundStyle(.secondary)
                                .frame(maxWidth: 500)
                            HStack {
                                Button("Choose images…") { chooseImages() }.buttonStyle(.borderedProminent)
                                Button("Choose folder…") { chooseFolder() }
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 300)
                        .background(targeted ? store.tint.opacity(0.12) : Color.secondary.opacity(0.05), in: RoundedRectangle(cornerRadius: 24))
                        .overlay {
                            RoundedRectangle(cornerRadius: 24)
                                .stroke(targeted ? store.tint : Color.secondary.opacity(0.3), style: StrokeStyle(lineWidth: 2, dash: [10]))
                        }
                        .dropDestination(for: URL.self) { urls, _ in
                            Task { await processDrops(urls) }
                            return !urls.isEmpty
                        } isTargeted: {
                            targeted = $0
                        }

                        GroupBox("Import from a secure link") {
                            VStack(alignment: .leading, spacing: 10) {
                                TextField("https://… direct image URL or public image link", text: $remoteURL)
                                    .textFieldStyle(.roundedBorder)
                                HStack {
                                    Text("Server firewall: HTTPS-only, private-network/SSRF blocking, redirect re-checks, timeout, MIME + magic bytes and 20 MB limit.")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                    Button("Import link") {
                                        let value = remoteURL.trimmingCharacters(in: .whitespacesAndNewlines)
                                        guard !value.isEmpty else { return }
                                        Task {
                                            await store.importRemote(value)
                                            remoteURL = ""
                                        }
                                    }
                                    .disabled(remoteURL.isEmpty)
                                }
                            }
                            .padding(8)
                        }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Google Drive").font(.headline)
                            Spacer()
                            if store.driveConnected {
                                Button("Disconnect") { Task { await store.disconnectDrive() } }
                            }
                            Button(store.driveConnected ? "Refresh" : "Connect") {
                                Task {
                                    if !store.driveConnected, let url = await store.driveAuthURL() {
                                        NSWorkspace.shared.open(url)
                                    } else {
                                        await store.loadDriveFiles()
                                    }
                                }
                            }
                        }

                        if !store.driveConfigured {
                            Label("Server OAuth credentials are not configured yet. Add GOOGLE_DRIVE_CLIENT_ID and GOOGLE_DRIVE_CLIENT_SECRET to the production Supabase project.", systemImage: "wrench.and.screwdriver")
                                .font(.caption)
                                .foregroundStyle(.orange)
                        } else if store.driveConnected {
                            Label("Drive connected securely. Refresh tokens are encrypted server-side and never stored in this app.", systemImage: "lock.shield.fill")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else {
                            Text("Connect a Google account with read-only Drive access, then import selected image files into your private wardrobe bucket.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        if store.driveFiles.isEmpty {
                            ContentUnavailableView(
                                "No Drive files loaded",
                                systemImage: "externaldrive",
                                description: Text(store.driveConnected ? "Press Refresh to load recent images." : "Connect Google Drive to browse image files.")
                            )
                        } else {
                            ForEach(store.driveFiles.prefix(80)) { file in
                                HStack {
                                    Image(systemName: "photo")
                                    VStack(alignment: .leading) {
                                        Text(file.name).lineLimit(1)
                                        Text(file.mimeType).font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Button("Import") { Task { await store.importDriveFile(file) } }
                                }
                                .padding(.vertical, 3)
                            }
                        }
                    }
                    .frame(width: 440)
                    .padding(16)
                    .background(.background, in: RoundedRectangle(cornerRadius: 20))
                    .overlay { RoundedRectangle(cornerRadius: 20).stroke(.quaternary) }
                }

                if !store.importJobs.isEmpty {
                    Text("Recent imports").font(.title2.bold())
                    ForEach(store.importJobs.prefix(30)) { job in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(job.title)
                                Text(job.status).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            ProgressView(value: job.progress).frame(width: 160)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .padding(24)
        }
    }

    private func chooseImages() {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.allowedContentTypes = [.image]
        panel.canChooseDirectories = false
        if panel.runModal() == .OK {
            Task { await store.importFiles(panel.urls) }
        }
    }

    private func chooseFolder() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        if panel.runModal() == .OK, let url = panel.url {
            Task { await store.importFolder(url) }
        }
    }

    private func processDrops(_ urls: [URL]) async {
        var files: [URL] = []
        for url in urls {
            var directory: ObjCBool = false
            if FileManager.default.fileExists(atPath: url.path, isDirectory: &directory), directory.boolValue {
                files += ImageFirewall.imageURLs(inFolder: url)
            } else {
                files.append(url)
            }
        }
        await store.importFiles(files)
    }
}
