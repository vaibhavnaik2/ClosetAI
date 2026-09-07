import Foundation

actor CloudClient {
    static let shared = CloudClient()

    private func endpoint(_ path: String, config: CloudConfig, query: [URLQueryItem] = []) throws -> URL {
        guard let base = config.baseURL else { throw CloudError.notConfigured }
        var url = base
        for part in path.split(separator: "/") { url.appendPathComponent(String(part)) }
        guard !query.isEmpty else { return url }
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        components.queryItems = query
        guard let final = components.url else { throw CloudError.invalidResponse }
        return final
    }

    private func request(_ path: String, method: String = "GET", body: Data? = nil, session: AuthSession, config: CloudConfig, contentType: String = "application/json", query: [URLQueryItem] = []) throws -> URLRequest {
        var req = URLRequest(url: try endpoint(path, config: config, query: query))
        req.httpMethod = method
        req.setValue(config.publishableKey, forHTTPHeaderField: "apikey")
        req.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        if body != nil { req.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        req.httpBody = body
        return req
    }

    private func perform(_ req: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: req)
        try CloudError.validate(response, data)
        return data
    }

    func bootstrap(session: AuthSession, config: CloudConfig) async throws -> BootstrapEnvelope {
        let req = try request("functions/v1/app-bootstrap", method: "POST", body: Data("{}".utf8), session: session, config: config)
        return try JSONDecoder().decode(BootstrapEnvelope.self, from: await perform(req))
    }

    func registerDevice(session: AuthSession, config: CloudConfig) async throws {
        let deviceIDKey = "device-id"
        let deviceID: String
        if let data = KeychainStore.load(account: deviceIDKey), let existing = String(data: data, encoding: .utf8) { deviceID = existing }
        else { let fresh = UUID().uuidString; try? KeychainStore.save(Data(fresh.utf8), account: deviceIDKey); deviceID = fresh }
        let payload: [String: Any] = [
            "user_id": session.user.id.uuidString,
            "device_id": deviceID,
            "platform": "macos",
            "device_name": Host.current().localizedName ?? "Mac",
            "app_version": "1.0.0-rc3",
            "last_seen_at": ISO8601DateFormatter().string(from: Date())
        ]
        var req = try request("rest/v1/app_devices", method: "POST", body: try JSONSerialization.data(withJSONObject: payload), session: session, config: config, query: [.init(name:"on_conflict",value:"user_id,device_id")])
        req.setValue("resolution=merge-duplicates,return=minimal", forHTTPHeaderField: "Prefer")
        _ = try await perform(req)
    }

    func fetchItems(session: AuthSession, config: CloudConfig) async throws -> [WardrobeItem] {
        let fields = "id,user_id,source_kind,source_ref,storage_path,image_sha256,name,audience,department,catalog_group,category,product_type,garment_type,primary_color,secondary_colors,brand,brand_confidence,brand_evidence,material,material_confidence,fabric_family,pattern,fit,formality,seasons,occasions,works_with_colors,style_tags,mood,status,is_favorite,wear_count,last_worn_at,ai_confidence,location_label,purchase_price,purchase_currency,created_at"
        let req = try request("rest/v1/wardrobe_items", session: session, config: config, query: [.init(name:"select",value:fields),.init(name:"order",value:"created_at.desc"),.init(name:"limit",value:"5000")])
        return try JSONDecoder().decode([WardrobeItem].self, from: await perform(req))
    }

    func fetchPreferences(session: AuthSession, config: CloudConfig) async throws -> UserPreferences? {
        let req = try request("rest/v1/user_preferences", session: session, config: config, query: [.init(name:"select",value:"*"),.init(name:"user_id",value:"eq.\(session.user.id.uuidString)"),.init(name:"limit",value:"1")])
        return try JSONDecoder().decode([UserPreferences].self, from: await perform(req)).first
    }

    func savePreferences(_ preferences: UserPreferences, session: AuthSession, config: CloudConfig) async throws {
        let body = try JSONEncoder().encode(preferences)
        var req = try request("rest/v1/user_preferences", method: "POST", body: body, session: session, config: config, query: [.init(name:"on_conflict",value:"user_id")])
        req.setValue("resolution=merge-duplicates,return=minimal", forHTTPHeaderField: "Prefer")
        _ = try await perform(req)
    }

    func fetchTaxonomy(session: AuthSession, config: CloudConfig) async throws -> [TaxonomyRow] {
        let req = try request("rest/v1/fashion_taxonomy", session: session, config: config, query: [.init(name:"select",value:"category,product_type"),.init(name:"active",value:"eq.true"),.init(name:"order",value:"category.asc,product_type.asc")])
        return try JSONDecoder().decode([TaxonomyRow].self, from: await perform(req))
    }

    func fetchFacets(session: AuthSession, config: CloudConfig) async throws -> WardrobeFacets {
        let req = try request("rest/v1/rpc/wardrobe_filter_facets", method: "POST", body: Data("{}".utf8), session: session, config: config)
        return try JSONDecoder().decode(WardrobeFacets.self, from: await perform(req))
    }

    func fetchPresets(session: AuthSession, config: CloudConfig) async throws -> [FilterPreset] {
        let req = try request("rest/v1/wardrobe_filter_presets", session: session, config: config, query: [.init(name:"select",value:"id,name,filters,natural_language_query,is_pinned"),.init(name:"order",value:"is_pinned.desc,updated_at.desc")])
        return try JSONDecoder().decode([FilterPreset].self, from: await perform(req))
    }

    func savePreset(name: String, filters: FilterState, naturalQuery: String?, session: AuthSession, config: CloudConfig) async throws -> FilterPreset {
        var payload: [String: Any] = ["user_id":session.user.id.uuidString,"name":name,"filters":filters.payload(),"is_pinned":false]
        payload["natural_language_query"] = naturalQuery ?? NSNull()
        var req = try request("rest/v1/wardrobe_filter_presets", method: "POST", body: try JSONSerialization.data(withJSONObject: payload), session: session, config: config, query: [.init(name:"select",value:"id,name,filters,natural_language_query,is_pinned")])
        req.setValue("return=representation", forHTTPHeaderField: "Prefer")
        guard let preset = try JSONDecoder().decode([FilterPreset].self, from: await perform(req)).first else { throw CloudError.invalidResponse }
        return preset
    }

    func naturalSearch(_ query: String, session: AuthSession, config: CloudConfig) async throws -> NaturalSearchResponse {
        let body = try JSONSerialization.data(withJSONObject: ["query":query,"limit":160])
        let req = try request("functions/v1/closet-search", method: "POST", body: body, session: session, config: config)
        return try JSONDecoder().decode(NaturalSearchResponse.self, from: await perform(req))
    }

    func uploadAndAnalyze(_ image: SanitizedImage, source: ImportSource, sourceRef: String?, ocrText: String, session: AuthSession, config: CloudConfig) async throws -> WardrobeItem {
        let path = UploadPath.make(userID: session.user.id)
        var upload = try request("storage/v1/object/wardrobe-private/\(path)", method: "POST", body: image.data, session: session, config: config, contentType: "image/jpeg")
        upload.setValue("false", forHTTPHeaderField: "x-upsert")
        _ = try await perform(upload)
        let payload: [String: Any] = ["storage_path":path,"sha256":image.sha256,"source_kind":source.rawValue,"source_ref":sourceRef ?? NSNull(),"ocr_text":String(ocrText.prefix(4000))]
        let analyze = try request("functions/v1/analyze-wardrobe-item", method: "POST", body: try JSONSerialization.data(withJSONObject: payload), session: session, config: config)
        let data = try await perform(analyze)
        let envelope = try JSONDecoder().decode(AnalysisEnvelope.self, from: data)
        if let item = envelope.item { return item }
        throw CloudError.invalidResponse
    }

    func importRemote(_ url: String, session: AuthSession, config: CloudConfig) async throws {
        let req = try request("functions/v1/import-url", method: "POST", body: try JSONSerialization.data(withJSONObject:["url":url]), session: session, config: config)
        _ = try await perform(req)
    }

    func drive(action: String, payload: [String: Any] = [:], session: AuthSession, config: CloudConfig) async throws -> DriveEnvelope {
        var object = payload; object["action"] = action
        let req = try request("functions/v1/google-drive", method: "POST", body: try JSONSerialization.data(withJSONObject: object), session: session, config: config)
        return try JSONDecoder().decode(DriveEnvelope.self, from: await perform(req))
    }

    func stylist(prompt: String, mode: String, session: AuthSession, config: CloudConfig) async throws -> StylistResponse {
        let req = try request("functions/v1/ai-stylist", method: "POST", body: try JSONSerialization.data(withJSONObject:["prompt":prompt,"mode":mode]), session: session, config: config)
        return try JSONDecoder().decode(StylistResponse.self, from: await perform(req))
    }

    func fetchOutfits(session: AuthSession, config: CloudConfig) async throws -> [OutfitRecord] {
        let req = try request("rest/v1/outfits", session: session, config: config, query: [.init(name:"select",value:"id,user_id,title,occasion,vibe,season,item_ids,score,rationale,tags,saved,created_at"),.init(name:"order",value:"created_at.desc"),.init(name:"limit",value:"500")])
        return try JSONDecoder().decode([OutfitRecord].self, from: await perform(req))
    }

    func saveLook(_ look: StylistLook, occasion: String?, vibe: String?, session: AuthSession, config: CloudConfig) async throws -> OutfitRecord {
        let record = OutfitRecord(id: UUID(), userID: session.user.id, title: look.title, occasion: occasion, vibe: vibe, season: nil, itemIDs: look.itemIDs, score: look.score, rationale: look.rationale, tags: look.tags, saved: true, createdAt: nil)
        var req = try request("rest/v1/outfits", method: "POST", body: try JSONEncoder().encode(record), session: session, config: config)
        req.setValue("return=representation", forHTTPHeaderField: "Prefer")
        guard let saved = try JSONDecoder().decode([OutfitRecord].self, from: await perform(req)).first else { throw CloudError.invalidResponse }
        return saved
    }

    func recordOutfitWorn(_ outfitID: UUID, session: AuthSession, config: CloudConfig) async throws {
        let req = try request("rest/v1/rpc/record_outfit_worn", method: "POST", body: try JSONSerialization.data(withJSONObject:["p_outfit_id":outfitID.uuidString]), session: session, config: config)
        _ = try await perform(req)
    }

    func updateItem(_ id: UUID, fields: [String: Any], session: AuthSession, config: CloudConfig) async throws {
        let req = try request("rest/v1/wardrobe_items", method: "PATCH", body: try JSONSerialization.data(withJSONObject: fields), session: session, config: config, query: [.init(name:"id",value:"eq.\(id.uuidString)")])
        _ = try await perform(req)
    }

    func imageData(path: String, session: AuthSession, config: CloudConfig) async throws -> Data {
        let req = try request("storage/v1/object/authenticated/wardrobe-private/\(path)", session: session, config: config)
        return try await perform(req)
    }
    func exportAccount(session: AuthSession, config: CloudConfig) async throws -> Data {
        let req = try request("functions/v1/account-export", method: "POST", body: Data("{}".utf8), session: session, config: config)
        return try await perform(req)
    }

    func deleteAccount(session: AuthSession, config: CloudConfig) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["confirmation":"DELETE MY CLOSET"])
        let req = try request("functions/v1/delete-account", method: "POST", body: body, session: session, config: config)
        _ = try await perform(req)
    }

}
