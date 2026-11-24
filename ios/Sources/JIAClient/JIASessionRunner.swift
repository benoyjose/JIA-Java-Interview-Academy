import Foundation
import SwiftUI
import Observation
public struct JIAPrompt: Identifiable, Sendable, Equatable {
 public let id:String;public let question:String;public let options:[String];public let correctOption:Int;public let explanation:String
 public init(id:String,question:String,options:[String],correctOption:Int,explanation:String){self.id=id;self.question=question;self.options=options;self.correctOption=correctOption;self.explanation=explanation}
}
@MainActor @Observable public final class JIASessionRunner {
 public private(set) var prompts:[JIAPrompt];public private(set) var index=0;public private(set) var answers:[String:Int]=[:];public private(set) var submitted=false;public private(set) var completed=false
 public var current:JIAPrompt?{prompts.indices.contains(index) ? prompts[index] : nil}
 public var score:Int{prompts.filter{answers[$0.id]==$0.correctOption}.count}
 public init(prompts:[JIAPrompt]){var seen=Set<String>();self.prompts=prompts.filter{seen.insert($0.id).inserted}}
 public func answer(_ option:Int){guard !submitted,let item=current,item.options.indices.contains(option) else{return};answers[item.id]=option}
 public func submit(){guard current != nil,!submitted else{return};submitted=true}
 public func next(){guard submitted else{return};if index+1>=prompts.count{completed=true}else{index+=1;submitted=false}}
 public func reset(){index=0;answers.removeAll();submitted=false;completed=false}
}
