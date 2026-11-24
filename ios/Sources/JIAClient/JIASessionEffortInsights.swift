import Foundation
public struct JIASessionEffortInsight:Equatable {
 public let accuracyPercent:Int
 public let averageSecondsPerQuestion:Double?
 public let needsReview:Bool
}
public enum JIASessionEffortInsights {
 public static func calculate(correct:Int,total:Int,durationSeconds:Int,reviewThreshold:Int=70)->JIASessionEffortInsight {
  precondition(total>=0&&correct>=0&&correct<=total&&durationSeconds>=0&&(0...100).contains(reviewThreshold))
  let accuracy=total==0 ? 0:Int((Double(correct)*100/Double(total)).rounded())
  return JIASessionEffortInsight(accuracyPercent:accuracy,averageSecondsPerQuestion:total==0 ? nil:Double(durationSeconds)/Double(total),needsReview:total>0&&accuracy<reviewThreshold)
 }
}