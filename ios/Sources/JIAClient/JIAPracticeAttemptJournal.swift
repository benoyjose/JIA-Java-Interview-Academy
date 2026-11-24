import Foundation
public struct JIAPracticeAttempt:Codable,Sendable,Equatable,Identifiable {
 public let id:String;public let sessionId:String;public let promptId:String;public let selectedOption:Int;public let correct:Bool;public let elapsedMilliseconds:Int;public let createdAt:Date
 public init(id:String,sessionId:String,promptId:String,selectedOption:Int,correct:Bool,elapsedMilliseconds:Int,createdAt:Date=Date()){self.id=id;self.sessionId=sessionId;self.promptId=promptId;self.selectedOption=selectedOption;self.correct=correct;self.elapsedMilliseconds=max(0,elapsedMilliseconds);self.createdAt=createdAt}
}
public actor JIAPracticeAttemptJournal {
 private let fileURL:URL;private var events:[JIAPracticeAttempt]=[]
 public init(fileURL:URL){self.fileURL=fileURL}
 public func load()throws->[JIAPracticeAttempt]{if FileManager.default.fileExists(atPath:fileURL.path){events=try JSONDecoder().decode([JIAPracticeAttempt].self,from:Data(contentsOf:fileURL))};return events}
 @discardableResult public func append(_ event:JIAPracticeAttempt)throws->Bool {
  guard !events.contains(where:{$0.sessionId==event.sessionId&&$0.promptId==event.promptId}) else{return false}
  events.append(event);try persist();return true
 }
 public func pending()->[JIAPracticeAttempt]{events}
 public func acknowledge(eventIds:Set<String>)throws{events.removeAll{acknowledged in eventIds.contains(acknowledged.id)};try persist()}
 private func persist()throws{try FileManager.default.createDirectory(at:fileURL.deletingLastPathComponent(),withIntermediateDirectories:true);let data=try JSONEncoder().encode(events);try data.write(to:fileURL,options:.atomic)}
}
