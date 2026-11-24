package com.jia.academy.learning
import org.junit.Assert.assertEquals
import org.junit.Test
class OfflineQueuePolicyTest {
 @Test fun filtersExpiredFutureAndDuplicateRecords() {
  val result=OfflineQueuePolicy.eligible(listOf(
   PendingActivity("a",100,0,100),PendingActivity("a",101,0,100),
   PendingActivity("old",1,0,100),PendingActivity("future",201,0,100)),200,50,1024)
  assertEquals(listOf("a"),result.map { it.id })
 }
}
