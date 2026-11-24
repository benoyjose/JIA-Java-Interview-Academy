package com.jia.academy.app.assessment
import org.junit.Assert.*;import org.junit.Test
class PracticeAttemptJournalContractTest{
 @Test fun eventIdentityAndPendingDefaultsAreStable(){val event=PracticeAttemptEntity("evt","sess","question",2,false,1200,1000);assertEquals("PENDING",event.syncState);assertEquals(0,event.retryCount);assertEquals("evt",event.eventId)}
}
