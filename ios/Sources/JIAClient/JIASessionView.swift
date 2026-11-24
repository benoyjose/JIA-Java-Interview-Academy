import SwiftUI
public struct JIASessionView: View {
 @State private var runner:JIASessionRunner
 private let onExit:()->Void
 public init(runner:JIASessionRunner,onExit:@escaping()->Void={}){_runner=State(initialValue:runner);self.onExit=onExit}
 public var body:some View {
  Group {
   if runner.completed {
    if runner.reviewingMistakes {mistakeReview}
    else {resultSummary}
   } else if let prompt=runner.current {
    ScrollView{VStack(alignment:.leading,spacing:16){
     Text("Question \\(runner.index+1) of \\(runner.prompts.count)").font(.caption)
     Text(prompt.question).font(.title2.bold())
     ForEach(prompt.options.indices,id:\\.self){i in Button(prompt.options[i]){runner.answer(i)}.buttonStyle(.borderedProminent).disabled(runner.submitted)}
     if runner.submitted {Text(runner.answers[prompt.id]==prompt.correctOption ? "Correct" : "Not quite").font(.headline);Text(prompt.explanation).font(.body).accessibilityIdentifier("answer-explanation")}
     HStack{Button("Check answer"){runner.submit()}.disabled(runner.submitted);Button(runner.index==runner.prompts.count-1 ? "Finish":"Next"){runner.next()}.disabled(!runner.submitted)}
    }.padding()}
   } else {ContentUnavailableView("No questions",systemImage:"book",description:Text("Choose a practice topic to begin."))}
  }.navigationTitle("Practice").accessibilityIdentifier("practice-session")
 }
 private var resultSummary:some View{
  VStack(alignment:.leading,spacing:14){
   Text("Session complete").font(.largeTitle)
   Text("Score \\(runner.score) / \\(runner.prompts.count)")
   Text("Accuracy \\(runner.accuracyPercent)% · \\(runner.answeredCount) answered")
   if !runner.missedPrompts.isEmpty {Button("Review \\(runner.missedPrompts.count) missed"){runner.showMistakeReview()}.buttonStyle(.borderedProminent)}
   Button("Return to learning",action:onExit)
  }.padding()
 }
 private var mistakeReview:some View{
  ScrollView{VStack(alignment:.leading,spacing:16){
   Text("Review missed questions").font(.largeTitle)
   ForEach(runner.missedPrompts){prompt in
    Text(prompt.question).font(.headline)
    Text("Your answer: \\(prompt.options.indices.contains(runner.answers[prompt.id] ?? -1) ? prompt.options[runner.answers[prompt.id]!] : "Not answered")")
    Text("Correct answer: \\(prompt.options.indices.contains(prompt.correctOption) ? prompt.options[prompt.correctOption] : "Unavailable")")
    Text(prompt.explanation)
    Divider()
   }
   Button("Back to results"){runner.returnToSummary()}
  }.padding()}
 }
}
