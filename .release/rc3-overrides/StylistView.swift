import SwiftUI

struct StylistView: View {
    @EnvironmentObject var store: ClosetStore
    @State private var prompt = "I have a client dinner tonight. Give me polished, quiet-luxury options that feel effortless."
    @State private var mode = "outfit"

    private let modes = [
        ("outfit", "Outfits"),
        ("packing", "Packing"),
        ("wardrobe_gap", "Gaps"),
        ("purchase_advice", "Purchase"),
        ("closet_audit", "Closet Audit"),
        ("care", "Care")
    ]

    var body: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 18) {
                Text("AI Stylist Lab").font(.largeTitle.bold())
                Text("Cloud reasoning + your real wardrobe. ClosetAI only returns IDs for pieces you actually own.")
                    .foregroundStyle(.secondary)

                Picker("Mode", selection: $mode) {
                    ForEach(modes, id: \.0) { option in
                        Text(option.1).tag(option.0)
                    }
                }
                .pickerStyle(.segmented)

                TextEditor(text: $prompt)
                    .font(.title3)
                    .padding(12)
                    .frame(minHeight: 190)
                    .background(.quaternary, in: RoundedRectangle(cornerRadius: 16))

                Button {
                    Task { await store.askStylist(prompt: prompt, mode: mode) }
                } label: {
                    Label("Ask ClosetAI", systemImage: "wand.and.stars")
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || store.isBusy)

                Divider()
                Text("Fast mode").font(.headline)
                Text("For instant offline-feeling suggestions, Today uses the bounded local outfit engine. This lab is the deeper server-side stylist.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding(24)
            .frame(width: 450)

            Divider()

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if let response = store.stylistResponse {
                        Text(response.summary).font(.title2.bold())
                        ForEach(response.looks) { look in
                            StylistLookCard(look: look, mode: mode)
                        }

                        if !response.advice.isEmpty {
                            GroupBox("Stylist notes") {
                                VStack(alignment: .leading, spacing: 8) {
                                    ForEach(response.advice, id: \.self) { note in
                                        Label(note, systemImage: "sparkles")
                                    }
                                }
                                .padding(8)
                            }
                        }

                        if !response.followUps.isEmpty {
                            Text("Try next").font(.headline)
                            ForEach(response.followUps, id: \.self) { suggestion in
                                Button(suggestion) {
                                    prompt = suggestion
                                }
                                .buttonStyle(.link)
                            }
                        }
                    } else {
                        ContentUnavailableView(
                            "Your wardrobe has a point of view",
                            systemImage: "wand.and.stars",
                            description: Text("Ask for outfits, a trip capsule, wardrobe gaps, purchase compatibility, closet audit or care guidance.")
                        )
                    }
                }
                .padding(24)
            }
        }
    }
}

struct StylistLookCard: View {
    @EnvironmentObject var store: ClosetStore
    let look: StylistLook
    let mode: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(look.title).font(.headline)
                Spacer()
                Text("\(look.score)/100").font(.headline).monospacedDigit()
            }
            Text(look.rationale).foregroundStyle(.secondary)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(look.itemIDs.compactMap { id in store.items.first { $0.id == id } }) { item in
                        VStack(alignment: .leading) {
                            CloudImageView(item: item)
                                .frame(width: 145, height: 170)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            Text(item.name).font(.caption.bold()).lineLimit(1)
                            Text(item.displayBrand).font(.caption2).foregroundStyle(.secondary)
                        }
                        .frame(width: 145)
                    }
                }
            }

            HStack {
                ForEach(look.tags, id: \.self) { tag in
                    Text(tag)
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(.quaternary, in: Capsule())
                }
                Spacer()
                if !look.itemIDs.isEmpty {
                    Button("Save Look") {
                        Task { await store.saveLook(look, occasion: nil, vibe: nil) }
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
        .padding(16)
        .background(.background, in: RoundedRectangle(cornerRadius: 20))
        .overlay { RoundedRectangle(cornerRadius: 20).stroke(.quaternary) }
    }
}
