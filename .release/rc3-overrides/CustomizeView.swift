import SwiftUI

struct CustomizeView: View {
    @EnvironmentObject var store: ClosetStore
    @State private var draft: UserPreferences?

    private let accents = ["#111111", "#1E3A8A", "#5B21B6", "#065F46", "#7C2D12", "#9F1239"]

    var body: some View {
        Form {
            if draft != nil {
                interfaceSection
                styleDNASection
                aiBehaviorSection
                privacySection
                saveSection
            } else {
                ProgressView()
            }
        }
        .formStyle(.grouped)
        .navigationTitle("Customize")
        .onAppear { draft = store.preferences }
        .onChange(of: store.preferences) { _, newValue in
            if draft == nil { draft = newValue }
        }
    }

    @ViewBuilder
    private var interfaceSection: some View {
        if let binding = preferenceBinding {
            Section("Interface") {
                Picker("Appearance", selection: binding.theme) {
                    Text("System").tag("system")
                    Text("Light").tag("light")
                    Text("Dark").tag("dark")
                }
                Picker("Grid density", selection: binding.gridDensity) {
                    Text("Compact").tag("compact")
                    Text("Comfortable").tag("comfortable")
                    Text("Spacious").tag("spacious")
                }
                Picker("Card size", selection: binding.cardSize) {
                    Text("Small").tag("small")
                    Text("Medium").tag("medium")
                    Text("Large").tag("large")
                }
                Toggle("Interface motion", isOn: binding.interfaceMotion)
                Toggle("Compact sidebar", isOn: binding.sidebarCompact)

                HStack {
                    Text("Accent")
                    Spacer()
                    ForEach(accents, id: \.self) { hex in
                        Circle()
                            .fill(Color(hex: hex) ?? .black)
                            .frame(width: 26, height: 26)
                            .overlay {
                                if binding.accentHex.wrappedValue == hex {
                                    Image(systemName: "checkmark")
                                        .font(.caption)
                                        .foregroundStyle(.white)
                                }
                            }
                            .onTapGesture { binding.accentHex.wrappedValue = hex }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var styleDNASection: some View {
        if let binding = preferenceBinding {
            Section("Style DNA") {
                TokenEditor(title: "Preferred styles", values: binding.preferredStyles, placeholder: "Minimal, quiet luxury, streetwear…")
                TokenEditor(title: "Style goals", values: binding.styleGoals, placeholder: "Sharper tailoring, more color…")
                TokenEditor(title: "Favorite colours", values: binding.preferredColors, placeholder: "Navy, cream, olive…")
                TokenEditor(title: "Avoid colours", values: binding.avoidColors, placeholder: "Colours to de-prioritize")
                TokenEditor(title: "Favorite brands", values: binding.preferredBrands, placeholder: "Brands to prioritize")

                TextField("Climate / city context", text: optionalStringBinding(binding.climateProfile))
                TextField("Default occasion", text: optionalStringBinding(binding.defaultOccasion))
                TextField("Notes for your stylist", text: optionalStringBinding(binding.styleNotes), axis: .vertical)
                    .lineLimit(2...5)
            }
        }
    }

    @ViewBuilder
    private var aiBehaviorSection: some View {
        if let binding = preferenceBinding {
            Section("AI behavior") {
                Picker("Stylist persona", selection: binding.stylistPersona) {
                    Text("Quiet Luxury").tag("quiet_luxury")
                    Text("Classic").tag("classic")
                    Text("Minimal").tag("minimal")
                    Text("Editorial").tag("editorial")
                    Text("Practical").tag("practical")
                }
                Stepper("Maximum outfit options: \(binding.maxOutfits.wrappedValue)", value: binding.maxOutfits, in: 1...10)
                Toggle("Include accessories", isOn: binding.includeAccessories)
                Toggle("Auto-analyze imports", isOn: binding.autoAnalyze)
                VStack(alignment: .leading) {
                    Text("AI review threshold \(Int(binding.reviewThreshold.wrappedValue * 100))%")
                    Slider(value: binding.reviewThreshold, in: 0.5...0.98)
                }
            }
        }
    }

    @ViewBuilder
    private var privacySection: some View {
        if let binding = preferenceBinding {
            Section("Privacy") {
                Picker("Privacy mode", selection: binding.privacyMode) {
                    Text("Standard").tag("standard")
                    Text("Strict").tag("strict")
                }
                let strict = binding.privacyMode.wrappedValue == "strict"
                Text(strict
                     ? "Strict mode is reserved for additional client-side privacy controls as the mobile clients ship. Cloud RLS and private storage remain active in both modes."
                     : "Private storage, row-level security and authenticated AI are always active.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var saveSection: some View {
        Section {
            HStack {
                Spacer()
                Button("Save customization") {
                    if let draft {
                        Task { await store.savePreferences(draft) }
                    }
                }
                .buttonStyle(.borderedProminent)
            }
        }
    }

    private var preferenceBinding: Binding<UserPreferences>? {
        guard draft != nil else { return nil }
        return Binding(
            get: { draft! },
            set: { draft = $0 }
        )
    }

    private func optionalStringBinding(_ source: Binding<String?>) -> Binding<String> {
        Binding(
            get: { source.wrappedValue ?? "" },
            set: { source.wrappedValue = $0.isEmpty ? nil : $0 }
        )
    }
}

struct TokenEditor: View {
    let title: String
    @Binding var values: [String]
    let placeholder: String
    @State private var text = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
            HStack {
                TextField(placeholder, text: $text)
                    .onSubmit { add() }
                Button("Add") { add() }
                    .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(values, id: \.self) { value in
                        HStack(spacing: 4) {
                            Text(value)
                            Button {
                                values.removeAll { $0 == value }
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                            }
                            .buttonStyle(.plain)
                        }
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(.quaternary, in: Capsule())
                    }
                }
            }
        }
    }

    private func add() {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if !value.isEmpty && !values.contains(value) {
            values.append(value)
        }
        text = ""
    }
}
