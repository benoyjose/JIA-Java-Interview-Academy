package com.jia.academy.learning.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QuestionPriorityPolicyTest {
 @Test void dueWeakQuestionsRankAboveMasteredOnes() {
  var policy=new QuestionPriorityPolicy();
  assertTrue(policy.priority(new QuestionPriorityPolicy.Signals(3,4,2,true)) >
   policy.priority(new QuestionPriorityPolicy.Signals(0,8,4,false)));
 }
 @Test void rejectsImpossibleSignals() {
  assertThrows(IllegalArgumentException.class,()->new QuestionPriorityPolicy.Signals(4,2,3,false));
 }
}
