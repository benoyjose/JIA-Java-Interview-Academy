import Foundation
import SwiftUI
public struct JIAReviewCard:Codable,Sendable,Equatable,Identifiable{
 public let lessonSlug:String;public let title:String;public let prompt:String;public let answer:String
 public var id:String{lessonSlug}
}
public struct JIAReviewSessionState:Sendable,Equatable{
 public let index:Int;public let completed:Int;public let total:Int;public let answerRevealed:Bool
 public var finished:Bool{index>=total}
}
@MainActor public final class JIASpacedReviewSession:ObservableObject{
 @Published public private(set) var state:JIAReviewSessionState
 public let cards:[JIAReviewCard]
 public init(cards:[JIAReviewCard]){self.cards=cards;self.state=JIAReviewSessionState(index:0,completed:0,total:cards.count,answerRevealed:false)}
 public var current:JIAReviewCard?{cards.indices.contains(state.index) ? cards[state.index]:nil}
 public func reveal(){guard current != nil else{return};state=JIAReviewSessionState(index:state.index,completed:state.completed,total:state.total,answerRevealed:true)}
 @discardableResult public func grade(_ rating:Int)throws->JIAReviewSessionState{
  guard (0...5).contains(rating)else{throw JIAReviewSessionError.invalidRating}
  guard current != nil else{throw JIAReviewSessionError.finished}
  guard state.answerRevealed else{throw JIAReviewSessionError.answerNotRevealed}
  state=JIAReviewSessionState(index:state.index+1,completed:state.completed+1,total:state.total,answerRevealed:false);return state
 }
}
public enum JIAReviewSessionError:Error{case invalidRating,finished,answerNotRevealed}
public struct JIASpacedReviewSessionView:View{
 @ObservedObject private var session:JIASpacedReviewSession
 public init(session:JIASpacedReviewSession){self.session=session}
 public var body:some View{List{Section("Review session"){
  ProgressView(value:Double(session.state.completed),total:Double(max(1,session.state.total)))
  if let card=session.current{Text(card.title).font(.headline);Text(card.prompt)
   if session.state.answerRevealed{Text(card.answer)}
   Button("Reveal answer"){session.reveal()}
   if session.state.answerRevealed{HStack{ForEach(0...5,id:\\.self){rating in Button(String(rating)){try? session.grade(rating)}}}}
  }else{ContentUnavailableView("Session complete",systemImage:"checkmark.circle",description:Text("All queued cards reviewed."))}
 }}}
}
