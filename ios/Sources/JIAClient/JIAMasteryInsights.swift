import Foundation
import SwiftUI

public struct JIAMasteryTopic: Codable, Sendable, Identifiable, Equatable {
 public let topicSlug:String
 public let topicName:String
 public let answers:Int
 public let correct:Int
 public let completedLessons:Int
 public var id:String { topicSlug }
 public var accuracy:Int { answers > 0 ? Int((100.0 * Double(correct) / Double(answers)).rounded()) : 0 }
 public var band:String {
  guard answers > 0 else { return "Not assessed" }
  if accuracy >= 85 { return "Mastered" }
  if accuracy >= 60 { return "Practicing" }
  return "Developing"
 }
}
public struct JIAMasteryResponse: Codable, Sendable, Equatable {
 public let modelVersion:Int
 public let topics:[JIAMasteryTopic]
}
public struct JIAMasteryInsightsView: View {
 public let topics:[JIAMasteryTopic]
 public init(topics:[JIAMasteryTopic]) { self.topics=topics }
 public var body:some View {
  List {
   Section("Skill mastery") {
    Text("Based on submitted objective answers. Lesson views alone do not establish mastery.")
     .font(.footnote).foregroundStyle(.secondary)
    if topics.isEmpty { ContentUnavailableView("No mastery evidence yet",systemImage:"chart.bar",description:Text("Complete a quiz to begin.")) }
    ForEach(topics.sorted{$0.accuracy < $1.accuracy}) { topic in
     VStack(alignment:.leading,spacing:6) {
      HStack { Text(topic.topicName).font(.headline); Spacer(); Text(topic.band).font(.caption) }
      ProgressView(value:Double(topic.accuracy),total:100)
      Text("\(topic.accuracy)% · \(topic.correct)/\(topic.answers) correct · \(topic.completedLessons) lessons")
       .font(.caption).foregroundStyle(.secondary)
     }.padding(.vertical,4)
    }
   }
  }.navigationTitle("Mastery")
 }
}