import XCTest
@testable import JIAClient

final class JIAClientModelTests: XCTestCase {

    func testAuthTokenResponseDecodesSharedBackendIdentity() throws {
        let json = """
        {
          "accessToken":"short-lived-access",
          "refreshToken":"rotating-refresh",
          "tokenType":"Bearer",
          "expiresAt":"2026-11-22T14:00:00Z",
          "user":{
            "id":"60000000-0000-0000-0000-000000000001",
            "name":"JIA Learner",
            "email":"learner@example.test",
            "roles":["LEARNER"]
          }
        }
        """.data(using: .utf8)!
        let token = try JSONDecoder().decode(JIATokenResponse.self, from: json)
        XCTAssertEqual(token.tokenType, "Bearer")
        XCTAssertEqual(token.refreshToken, "rotating-refresh")
        XCTAssertEqual(token.user.email, "learner@example.test")
        XCTAssertEqual(token.user.roles, ["LEARNER"])
    }

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
    func testLessonSummaryDecodesMissingOptionalSummary() throws {
        let json = """
        {"id":"10000000-0000-0000-0000-000000000002","slug":"streams","title":"Streams","difficulty":"BEGINNER","contentRevision":1}
        """.data(using: .utf8)!
        let lesson = try JSONDecoder().decode(JIALessonSummary.self, from: json)
        XCTAssertNil(lesson.summary)
        XCTAssertEqual(lesson.contentRevision, 1)
    }
    func testStructuredLessonDecodesKnownBlocksAndRetainsUnknownTypes() throws {
        let json = """
        {"schemaVersion":1,"blocks":[
          {"type":"interview_answer","title":"Short answer","text":"Use streams"},
          {"type":"code","language":"java","code":"items.stream()"},
          {"type":"future_widget","payload":{"x":1}}
        ]}
        """.data(using: .utf8)!
        let content = try JSONDecoder().decode(JIALessonContent.self, from: json)
        XCTAssertEqual(content.schemaVersion, 1)
        XCTAssertEqual(content.blocks.count, 3)
        XCTAssertEqual(content.blocks[0].text, "Use streams")
        XCTAssertEqual(content.blocks[1].language, "java")
        XCTAssertFalse(content.blocks[2].isSupported)
    }

    func testStructuredLessonRejectsUnsupportedSchemaVersion() {
        let json = #"{"schemaVersion":2,"blocks":[]}"#.data(using: .utf8)!
        XCTAssertThrowsError(try JSONDecoder().decode(JIALessonContent.self, from: json))
    }

    func testStructuredLessonRejectsMissingRequiredFieldsAndMalformedBlocks() {
        let missingVersion = #"{"blocks":[]}"#.data(using: .utf8)!
        let missingType = #"{"schemaVersion":1,"blocks":[{"title":"missing type"}]}"#.data(using: .utf8)!
        XCTAssertThrowsError(try JSONDecoder().decode(JIALessonContent.self, from: missingVersion))
        XCTAssertThrowsError(try JSONDecoder().decode(JIALessonContent.self, from: missingType))
    }

    func testCategoryAndLessonListModelsDecodeCatalogueResponses() throws {
        let categoryJSON = """
        {"id":"20000000-0000-0000-0000-000000000001","slug":"streams","name":"Streams"}
        """.data(using: .utf8)!
        let category = try JSONDecoder().decode(JIACategory.self, from: categoryJSON)
        XCTAssertEqual(category.slug, "streams")
        XCTAssertNil(category.technologySlug)

        let lessonJSON = """
        {"id":"30000000-0000-0000-0000-000000000001","slug":"stream-map","title":"Stream map","difficulty":"BEGINNER","contentRevision":1}
        """.data(using: .utf8)!
        XCTAssertEqual(try JSONDecoder().decode(JIALessonSummary.self, from: lessonJSON).slug, "stream-map")
    }
}
