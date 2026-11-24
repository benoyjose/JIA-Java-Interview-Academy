import SwiftUI
public struct JIASessionView: View {
 @State private var runner:JIASessionRunner
 private let onExit:()->Void
 public init(runner:JIASessionRunner,onExit:@escaping()->Void={}){_runner=State(initialValue:runner);self.onExit=onExit}
 public var body:some View {
  Group {
   if runner.completed {ContentView(title:"Session complete",score:runner.score,total:runner.prompts.count,onExit:onExit)}
   else if let prompt=runner.current {ScrollView{VStack(alignment:.leading,spacing:16){Text("Question \\(runner.index+1) of \\(runner.prompts.count)").font(.caption);Text(prompt.question).font(.title2.bold())
    ForEach(prompt.options.indices,id:\\.self){i in Button(prompt.options[i]){runner.answer(i)}.buttonStyle(.borderedProminent).disabled(runner.submitted)}
    if runner.submitted{Text(prompt.explanation).font(.body).accessibilityIdentifier("answer-explanation")}
    HStack{Button("Check answer"){runner.submit()}.disabled(runner.submitted);Button(runner.index==runner.prompts.count-1 ? "Finish":"Next"){runner.next()}.disabled(!runner.submitted)}
   }.padding()}}
   else {ContentUnavailableView("No questions",systemImage:"book",description:Text("Choose a practice topic to begin."))}
  }.navigationTitle("Practice").accessibilityIdentifier("practice-session")
 }
}
private struct ContentView:View{let title:String;let score:Int;let total:Int;let onExit:()->Void;var body:some View{VStack(spacing:12){Text(title).font(.largeTitle);Text("Score \\(score) / \\(total)");Button("Return to learning",action:onExit)}}}
