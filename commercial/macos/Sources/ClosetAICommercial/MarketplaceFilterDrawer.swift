import SwiftUI

struct MarketplaceFilterDrawer: View {
    @Binding var filters: FilterState
    let accessToken: String
    let userID: UUID?
    let onApply: (FilterState, [SearchResultItem]?) -> Void

    @State private var facets = WardrobeFacets()
    @State private var taxonomy: [TaxonomyRow] = []
    @State private var presets: [FilterPreset] = []
    @State private var brandQuery = ""
    @State private var naturalQuery = ""
    @State private var interpretation = ""
    @State private var presetName = ""
    @State private var searchResults: [SearchResultItem]? = nil
    @State private var isLoading = false
    @State private var errorMessage: String?

    private var visibleBrands: [FacetCount] {
        guard !brandQuery.isEmpty else { return facets.brands }
        return facets.brands.filter { $0.value.localizedCaseInsensitiveContains(brandQuery) }
    }

    private var groupedTaxonomy: [(String, [String])] {
        let grouped = Dictionary(grouping: taxonomy, by: \TaxonomyRow.category)
            .mapValues { Array(Set($0.map(\.productType))).sorted() }
        return grouped.keys.sorted().map { ($0, grouped[$0] ?? []) }
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    naturalLanguageSection
                    presetsSection
                    colorSection
                    brandsSection
                    taxonomySection
                    chipSection(title: "Occasion", values: facets.occasions.map(\.value), selection: $filters.occasions)
                    chipSection(title: "Vibe", values: facets.vibes.map(\.value), selection: $filters.vibes)
                    chipSection(title: "Material", values: facets.fabricFamilies.map(\.value), selection: $filters.materials)
                    chipSection(title: "Fit", values: facets.fits.map(\.value), selection: $filters.fits)
                    chipSection(title: "Mood", values: facets.moods.map(\.value), selection: $filters.moods)
                    if let errorMessage {
                        Text(errorMessage).font(.caption).foregroundStyle(.red)
                    }
                }
                .padding(18)
            }
            Divider()
            footer
        }
        .frame(minWidth: 390, idealWidth: 430, maxWidth: 500)
        .task { await refresh() }
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Filters").font(.title2.bold())
                Text(filters.activeCount == 0 ? "All wardrobe items" : "\(filters.activeCount) active selections")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button("Clear") {
                filters = FilterState()
                interpretation = ""
                searchResults = nil
            }
            .disabled(filters.isEmpty && naturalQuery.isEmpty)
        }
        .padding(18)
    }

    private var naturalLanguageSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("AI Closet Search", systemImage: "sparkles").font(.headline)
            TextField("e.g. navy or beige quiet-luxury clothes for a client dinner", text: $naturalQuery, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .lineLimit(2...4)

            HStack {
                Button {
                    Task { await runNaturalSearch() }
                } label: {
                    if isLoading { ProgressView().controlSize(.small) }
                    else { Label("Interpret & Search", systemImage: "wand.and.stars") }
                }
                .buttonStyle(.borderedProminent)
                .disabled(naturalQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isLoading)

                if !interpretation.isEmpty {
                    Text(interpretation).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
            }
        }
    }

    private var presetsSection: some View {
        DisclosureGroup {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(presets) { preset in
                    Button {
                        filters = FilterState(payload: preset.filters)
                        naturalQuery = preset.naturalLanguageQuery ?? ""
                        searchResults = nil
                    } label: {
                        HStack {
                            Image(systemName: preset.isPinned ? "pin.fill" : "bookmark")
                            Text(preset.name)
                            Spacer()
                            Text("\(FilterState(payload: preset.filters).activeCount)")
                                .foregroundStyle(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                }

                HStack {
                    TextField("Preset name", text: $presetName)
                    Button("Save") { Task { await savePreset() } }
                        .disabled(presetName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || userID == nil)
                }
            }
            .padding(.top, 8)
        } label: {
            Label("Saved Presets", systemImage: "bookmark.fill")
                .font(.headline)
        }
    }

    private var colorSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Colour").font(.headline)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 72), spacing: 10)], spacing: 10) {
                ForEach(facets.colors) { facet in
                    let selected = filters.colors.contains(facet.value)
                    Button {
                        toggle(facet.value, in: &filters.colors)
                    } label: {
                        VStack(spacing: 6) {
                            ZStack {
                                Circle().fill(color(for: facet.value))
                                Circle().strokeBorder(selected ? Color.primary : Color.secondary.opacity(0.25), lineWidth: selected ? 3 : 1)
                                if selected { Image(systemName: "checkmark").font(.caption.bold()).foregroundStyle(contrastColor(for: facet.value)) }
                            }
                            .frame(width: 30, height: 30)
                            Text(facet.value).font(.caption).lineLimit(1)
                            Text("\(facet.count)").font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var brandsSection: some View {
        DisclosureGroup {
            VStack(alignment: .leading, spacing: 8) {
                TextField("Search brands", text: $brandQuery)
                    .textFieldStyle(.roundedBorder)
                ForEach(visibleBrands.prefix(80)) { facet in
                    Toggle(isOn: Binding(
                        get: { filters.brands.contains(facet.value) },
                        set: { _ in toggle(facet.value, in: &filters.brands) }
                    )) {
                        HStack {
                            Text(facet.value)
                            Spacer()
                            Text("\(facet.count)").foregroundStyle(.secondary)
                        }
                    }
                    .toggleStyle(.checkbox)
                }
            }.padding(.top, 8)
        } label: {
            Label("Brands", systemImage: "tag.fill").font(.headline)
        }
    }

    private var taxonomySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Category → Product Type").font(.headline)
            ForEach(groupedTaxonomy, id: \.0) { category, products in
                DisclosureGroup {
                    VStack(alignment: .leading, spacing: 7) {
                        Button(filters.categories.contains(category) ? "Remove category filter" : "Select all \(category)") {
                            toggle(category, in: &filters.categories)
                        }
                        .buttonStyle(.link)

                        ForEach(products, id: \.self) { product in
                            Toggle(product, isOn: Binding(
                                get: { filters.productTypes.contains(product) },
                                set: { _ in toggle(product, in: &filters.productTypes) }
                            ))
                            .toggleStyle(.checkbox)
                        }
                    }
                    .padding(.leading, 12)
                    .padding(.top, 6)
                } label: {
                    HStack {
                        Text(category)
                        if filters.categories.contains(category) || products.contains(where: filters.productTypes.contains) {
                            Spacer()
                            Image(systemName: "checkmark.circle.fill")
                        }
                    }
                }
            }
        }
    }

    private func chipSection(title: String, values: [String], selection: Binding<Set<String>>) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.headline)
            FlowLayout(spacing: 8) {
                ForEach(values, id: \.self) { value in
                    let selected = selection.wrappedValue.contains(value)
                    Button(value) {
                        var next = selection.wrappedValue
                        if selected { next.remove(value) } else { next.insert(value) }
                        selection.wrappedValue = next
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .fontWeight(selected ? .semibold : .regular)
                }
            }
        }
    }

    private var footer: some View {
        HStack {
            Spacer()
            Button("Apply Filters") { onApply(filters, searchResults) }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.return, modifiers: [.command])
        }
        .padding(14)
    }

    private func refresh() async {
        guard !accessToken.isEmpty else { return }
        do {
            async let f = ClosetAPI.shared.fetchFacets(accessToken: accessToken)
            async let t = ClosetAPI.shared.fetchTaxonomy(accessToken: accessToken)
            async let p = ClosetAPI.shared.fetchPresets(accessToken: accessToken)
            let values = try await (f, t, p)
            facets = values.0
            taxonomy = values.1
            presets = values.2
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func runNaturalSearch() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let result = try await ClosetAPI.shared.naturalSearch(query: naturalQuery, accessToken: accessToken)
            filters = FilterState(payload: result.filters)
            interpretation = result.interpretation ?? ""
            searchResults = result.items
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func savePreset() async {
        guard let userID else { return }
        do {
            let saved = try await ClosetAPI.shared.savePreset(
                name: presetName.trimmingCharacters(in: .whitespacesAndNewlines),
                filters: filters,
                naturalLanguageQuery: naturalQuery.isEmpty ? nil : naturalQuery,
                userID: userID,
                accessToken: accessToken
            )
            presets.insert(saved, at: 0)
            presetName = ""
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func toggle(_ value: String, in set: inout Set<String>) {
        if set.contains(value) { set.remove(value) } else { set.insert(value) }
    }

    private func color(for name: String) -> Color {
        switch name.lowercased() {
        case let s where s.contains("navy"): return Color(red: 0.04, green: 0.09, blue: 0.22)
        case let s where s.contains("black"): return .black
        case let s where s.contains("white"): return .white
        case let s where s.contains("beige"), let s where s.contains("cream"): return Color(red: 0.88, green: 0.82, blue: 0.68)
        case let s where s.contains("grey"), let s where s.contains("gray"): return .gray
        case let s where s.contains("blue"): return .blue
        case let s where s.contains("red"): return .red
        case let s where s.contains("green"), let s where s.contains("olive"): return .green
        case let s where s.contains("brown"), let s where s.contains("tan"): return .brown
        case let s where s.contains("pink"): return .pink
        case let s where s.contains("purple"): return .purple
        case let s where s.contains("orange"): return .orange
        case let s where s.contains("yellow"): return .yellow
        default: return .secondary
        }
    }

    private func contrastColor(for name: String) -> Color {
        let lower = name.lowercased()
        return (lower.contains("white") || lower.contains("beige") || lower.contains("cream") || lower.contains("yellow")) ? .black : .white
    }
}

private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 400
        var x: CGFloat = 0
        var y: CGFloat = 0
        var lineHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width {
                x = 0
                y += lineHeight + spacing
                lineHeight = 0
            }
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
        return CGSize(width: width, height: y + lineHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var lineHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += lineHeight + spacing
                lineHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
    }
}
