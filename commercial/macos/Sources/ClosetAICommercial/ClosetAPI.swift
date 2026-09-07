import Foundation

actor ClosetAPI {
    static let shared = ClosetAPI()

    private let baseURL = URL(string: "https://kgcdvvurhbuqyqeylour.supabase.co")!
    private let publishableKey = "sb_publishable_H73Z9qahc47g3-cQyBaS-Q_JuJAqnbF"

    private func request(path: String, method: String = "GET", accessToken: String, body: Data? = nil, prefer: String? = nil) throws -> URLRequest {
        guard !accessToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw URLError(.userAuthenticationRequired)
        }
        var req = URLRequest(url: baseURL.appending(path: path))
        req.httpMethod = method
        req.setValue(publishableKey, forHTTPHeaderField: "apikey")
        req.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let prefer { req.setValue(prefer, forHTTPHeaderField: "Prefer") }
        req.httpBody = body
        return req
    }

    private func perform<T: Decodable>(_ req: URLRequest, as type: T.Type) async throws -> T {
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse, 200..<300 ~= http.statusCode else {
            let message = String(data: data, encoding: .utf8) ?? "Request failed"
            throw NSError(domain: "ClosetAPI", code: (response as? HTTPURLResponse)?.statusCode ?? -1, userInfo: [NSLocalizedDescriptionKey: message])
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    func fetchFacets(accessToken: String) async throws -> WardrobeFacets {
        let req = try request(path: "/rest/v1/rpc/wardrobe_filter_facets", method: "POST", accessToken: accessToken, body: Data("{}".utf8))
        return try await perform(req, as: WardrobeFacets.self)
    }

    func fetchTaxonomy(accessToken: String) async throws -> [TaxonomyRow] {
        let req = try request(
            path: "/rest/v1/fashion_taxonomy?select=category,product_type&active=eq.true&order=category.asc,product_type.asc",
            accessToken: accessToken
        )
        return try await perform(req, as: [TaxonomyRow].self)
    }

    func fetchPresets(accessToken: String) async throws -> [FilterPreset] {
        let req = try request(
            path: "/rest/v1/wardrobe_filter_presets?select=id,name,filters,natural_language_query,is_pinned,updated_at&order=is_pinned.desc,updated_at.desc",
            accessToken: accessToken
        )
        return try await perform(req, as: [FilterPreset].self)
    }

    func savePreset(name: String, filters: FilterState, naturalLanguageQuery: String?, userID: UUID, accessToken: String) async throws -> FilterPreset {
        var payload: [String: Any] = [
            "user_id": userID.uuidString,
            "name": name,
            "filters": filters.asPayload(),
            "is_pinned": false
        ]
        payload["natural_language_query"] = naturalLanguageQuery ?? NSNull()

        let body = try JSONSerialization.data(withJSONObject: payload)
        let req = try request(
            path: "/rest/v1/wardrobe_filter_presets?select=id,name,filters,natural_language_query,is_pinned,updated_at",
            method: "POST",
            accessToken: accessToken,
            body: body,
            prefer: "return=representation"
        )
        let rows = try await perform(req, as: [FilterPreset].self)
        guard let first = rows.first else { throw URLError(.cannotParseResponse) }
        return first
    }

    func naturalSearch(query: String, accessToken: String) async throws -> NaturalSearchResponse {
        let body = try JSONSerialization.data(withJSONObject: ["query": query, "limit": 120])
        let req = try request(path: "/functions/v1/closet-search", method: "POST", accessToken: accessToken, body: body)
        return try await perform(req, as: NaturalSearchResponse.self)
    }
}
