import Foundation

struct FacetCount: Codable, Hashable, Identifiable {
    var id: String { value }
    let value: String
    let count: Int
}

struct WardrobeFacets: Codable {
    var brands: [FacetCount] = []
    var categories: [FacetCount] = []
    var productTypes: [FacetCount] = []
    var colors: [FacetCount] = []
    var fits: [FacetCount] = []
    var patterns: [FacetCount] = []
    var fabricFamilies: [FacetCount] = []
    var moods: [FacetCount] = []
    var sleeves: [FacetCount] = []
    var collars: [FacetCount] = []
    var waistRises: [FacetCount] = []
    var occasions: [FacetCount] = []
    var vibes: [FacetCount] = []

    enum CodingKeys: String, CodingKey {
        case brands, categories, colors, fits, patterns, moods, sleeves, collars, occasions, vibes
        case productTypes = "product_types"
        case fabricFamilies = "fabric_families"
        case waistRises = "waist_rises"
    }
}

struct TaxonomyRow: Codable, Hashable, Identifiable {
    var id: String { "\(category)|\(productType)" }
    let category: String
    let productType: String

    enum CodingKeys: String, CodingKey {
        case category
        case productType = "product_type"
    }
}

struct FilterState: Codable, Equatable {
    var brands: Set<String> = []
    var categories: Set<String> = []
    var productTypes: Set<String> = []
    var colors: Set<String> = []
    var fits: Set<String> = []
    var occasions: Set<String> = []
    var vibes: Set<String> = []
    var materials: Set<String> = []
    var moods: Set<String> = []

    var isEmpty: Bool {
        brands.isEmpty && categories.isEmpty && productTypes.isEmpty && colors.isEmpty &&
        fits.isEmpty && occasions.isEmpty && vibes.isEmpty && materials.isEmpty && moods.isEmpty
    }

    var activeCount: Int {
        [brands, categories, productTypes, colors, fits, occasions, vibes, materials, moods]
            .reduce(0) { $0 + $1.count }
    }

    func asPayload() -> [String: [String]] {
        [
            "brands": brands.sorted(),
            "categories": categories.sorted(),
            "product_types": productTypes.sorted(),
            "colors": colors.sorted(),
            "fits": fits.sorted(),
            "occasions": occasions.sorted(),
            "vibes": vibes.sorted(),
            "materials": materials.sorted(),
            "moods": moods.sorted()
        ]
    }

    init() {}

    init(payload: [String: [String]]) {
        brands = Set(payload["brands"] ?? [])
        categories = Set(payload["categories"] ?? [])
        productTypes = Set(payload["product_types"] ?? [])
        colors = Set(payload["colors"] ?? [])
        fits = Set(payload["fits"] ?? [])
        occasions = Set(payload["occasions"] ?? [])
        vibes = Set(payload["vibes"] ?? [])
        materials = Set(payload["materials"] ?? [])
        moods = Set(payload["moods"] ?? [])
    }
}

struct FilterPreset: Codable, Identifiable {
    let id: UUID
    let name: String
    let filters: [String: [String]]
    let naturalLanguageQuery: String?
    let isPinned: Bool
    let updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case id, name, filters
        case naturalLanguageQuery = "natural_language_query"
        case isPinned = "is_pinned"
        case updatedAt = "updated_at"
    }
}

struct SearchResultItem: Codable, Identifiable {
    let id: UUID
    let name: String
    let brand: String?
    let category: String?
    let productType: String?
    let primaryColor: String?
    let fabricFamily: String?
    let fit: String?
    let mood: String?
    let occasions: [String]
    let styleTags: [String]
    let storagePath: String?
    let wearCount: Int
    let isFavorite: Bool
    let score: Double

    enum CodingKeys: String, CodingKey {
        case id, name, brand, category, fit, mood, occasions, score
        case productType = "product_type"
        case primaryColor = "primary_color"
        case fabricFamily = "fabric_family"
        case styleTags = "style_tags"
        case storagePath = "storage_path"
        case wearCount = "wear_count"
        case isFavorite = "is_favorite"
    }
}

struct NaturalSearchResponse: Codable {
    let query: String
    let interpretation: String?
    let semanticQuery: String?
    let filters: [String: [String]]
    let items: [SearchResultItem]

    enum CodingKeys: String, CodingKey {
        case query, interpretation, filters, items
        case semanticQuery = "semantic_query"
    }
}
