import Foundation

// Storage ownership and the analysis endpoint compare UUID prefixes as text.
// Foundation UUID strings are uppercase; Supabase auth IDs are lowercase.
enum UploadPath {
    static func make(userID: UUID, imageID: UUID = UUID()) -> String {
        "\(userID.uuidString.lowercased())/imports/\(imageID.uuidString.lowercased()).jpg"
    }
}
