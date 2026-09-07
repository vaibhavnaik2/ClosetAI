import Foundation
@main struct UploadPathChecks {
    static func main() {
        let owner = UUID(uuidString: "ABCDEF12-3456-4789-ABCD-123456ABCDEF")!
        let image = UUID(uuidString: "FEDCBA98-7654-4321-ABCD-FEDCBA987654")!
        precondition(UploadPath.make(userID: owner, imageID: image) == "abcdef12-3456-4789-abcd-123456abcdef/imports/fedcba98-7654-4321-abcd-fedcba987654.jpg")
        precondition(UploadPath.make(userID: owner) != UploadPath.make(userID: owner))
        print("Upload path regression checks passed")
    }
}
