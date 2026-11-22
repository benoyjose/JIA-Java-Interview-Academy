import XCTest
@testable import JIAClient

final class JIAClientModelTests: XCTestCase {
    func testTechnologyDecodesSharedCatalogueContract() throws {
        let json = """
        {"id":"10000000-0000-0000-0000-000000000001","slug":"core-java","name":"Core Java","description":"Java fundamentals","sortOrder":1}
        """.data(using: .utf8)!
        let technology = try JSONDecoder().decode(JIATechnology.self, from: json)
        XCTAssertEqual(technology.slug, "core-java")
        XCTAssertEqual(technology.sortOrder, 1)
    }
    func testTechnologyAllowsOmittedAndNullOptionalDescription() throws {
        let omitted = """
        {"id":"10000000-0000-0000-0000-000000000001","slug":"core-java","name":"Core Java","sortOrder":1}
        """.data(using: .utf8)!
        let nullValue = """
        {"id":"10000000-0000-0000-0000-000000000001","slug":"core-java","name":"Core Java","description":null,"sortOrder":1}
        """.data(using: .utf8)!
        XCTAssertNil(try JSONDecoder().decode(JIATechnology.self, from: omitted).description)
        XCTAssertNil(try JSONDecoder().decode(JIATechnology.self, from: nullValue).description)
    }
    func testTechnologyRejectsMissingRequiredSortOrder() {
        let malformed = """
        {"id":"10000000-0000-0000-0000-000000000001","slug":"core-java","name":"Core Java"}
        """.data(using: .utf8)!
        XCTAssertThrowsError(try JSONDecoder().decode(JIATechnology.self, from: malformed))
    }
    func testAPIErrorPreservesHTTPStatus() {
        XCTAssertEqual(JIAAPIError.httpStatus(401), JIAAPIError.httpStatus(401))
    }
}
