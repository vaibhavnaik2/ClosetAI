import SwiftUI

@main
struct ClosetAICommercialApp: App {
    var body: some Scene {
        WindowGroup("ClosetAI") {
            CommercialRootView()
                .frame(minWidth: 1050, minHeight: 680)
        }
    }
}

struct CommercialRootView: View {
    @AppStorage("closet_access_token") private var accessToken = ""
    @AppStorage("closet_user_id") private var userIDText = ""

    @State private var filters = FilterState()
    @State private var showingFilters = true
    @State private var results: [SearchResultItem] = []

    private var userID: UUID? { UUID(uuidString: userIDText) }

    var body: some View {
        NavigationSplitView {
            List {
                Label("Wardrobe", systemImage: "tshirt")
                Label("Outfits", systemImage: "sparkles")
                Label("Saved Looks", systemImage: "bookmark")
                Label("Insights", systemImage: "chart.bar")
            }
            .navigationTitle("ClosetAI")
        } content: {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    VStack(alignment: .leading) {
                        Text("Wardrobe").font(.largeTitle.bold())
                        Text(filters.isEmpty ? "Everything you own" : "\(filters.activeCount) active filters")
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button {
                        showingFilters.toggle()
                    } label: {
                        Label("Filters \(filters.activeCount > 0 ? "(\(filters.activeCount))" : "")", systemImage: "line.3.horizontal.decrease.circle")
                    }
                    .buttonStyle(.borderedProminent)
                }

                if accessToken.isEmpty {
                    ContentUnavailableView(
                        "Sign in required",
                        systemImage: "person.crop.circle.badge.exclamationmark",
                        description: Text("The commercial filter layer is authenticated. Connect it to the ClosetAI login session to load your live wardrobe facets.")
                    )
                } else if results.isEmpty {
                    ContentUnavailableView(
                        "Your filtered wardrobe",
                        systemImage: "tshirt.fill",
                        description: Text("Open Filters to browse by colour, brand, category, occasion, vibe, material or natural-language intent.")
                    )
                } else {
                    List(results) { item in
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(item.name).fontWeight(.semibold)
                                Text([item.brand, item.productType, item.primaryColor].compactMap { $0 }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text("\(Int(item.score * 100))%")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .padding(20)
            .navigationTitle("Wardrobe")
        } detail: {
            if showingFilters {
                MarketplaceFilterDrawer(
                    filters: $filters,
                    accessToken: accessToken,
                    userID: userID
                ) { newFilters, searchItems in
                    filters = newFilters
                    if let searchItems { results = searchItems }
                }
            } else {
                ContentUnavailableView("Filters hidden", systemImage: "sidebar.right")
            }
        }
    }
}
