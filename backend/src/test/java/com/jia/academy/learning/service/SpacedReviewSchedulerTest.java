package com.jia.academy.learning.service;
import static org.junit.jupiter.api.Assertions.*;import java.time.LocalDate;import org.junit.jupiter.api.Test;
class SpacedReviewSchedulerTest{
 private final SpacedReviewScheduler s=new SpacedReviewScheduler();private final LocalDate d=LocalDate.of(2026,11,24);
 @Test void standardScheduleIsStable(){assertEquals(1,s.grade(4,0,2.5,0,d).intervalDays());var second=s.grade(5,1,2.5,1,d);assertEquals(6,second.intervalDays());assertEquals(d.plusDays(6),second.dueDate());}
 @Test void failedRecallResetsAndClampsEase(){var r=s.grade(2,8,1.4,50,d);assertEquals(0,r.repetitions());assertEquals(1.3,r.easeFactor());assertEquals(1,r.intervalDays());}
 @Test void capsAndValidates(){assertEquals(365,s.grade(5,2,3,200,d).intervalDays());assertThrows(IllegalArgumentException.class,()->s.grade(6,0,2.5,0,d));}
}
