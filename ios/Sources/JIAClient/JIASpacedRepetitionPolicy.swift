import Foundation
public struct JIASpacedRepetitionPolicy: Sendable {
 public enum Rating: Sendable { case again, hard, good, easy }
 public func nextIntervalDays(current: Int, rating: Rating) -> Int {
  let safe=max(0,current)
  switch rating { case .again: return 1; case .hard: return max(1,Int(Double(max(1,safe))*1.2)); case .good: return max(2,Int(Double(max(1,safe))*2.0)); case .easy: return max(3,Int(Double(max(1,safe))*2.8)) }
 }
}