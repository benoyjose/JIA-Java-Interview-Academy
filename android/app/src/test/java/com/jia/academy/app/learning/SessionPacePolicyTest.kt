package com.jia.academy.app.learning
import org.junit.Assert.assertEquals
import org.junit.Test
class SessionPacePolicyTest {
 @Test fun returnsBreakAndPaceSignals(){
  assertEquals(PaceAdvice.TAKE_BREAK,SessionPacePolicy.advise(8,20,4,10,3))
  assertEquals(PaceAdvice.SLOW_DOWN,SessionPacePolicy.advise(15,20,5,10,0))
  assertEquals(PaceAdvice.ON_TRACK,SessionPacePolicy.advise(10,20,5,10,0))
 }
}