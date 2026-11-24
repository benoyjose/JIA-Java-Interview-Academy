package com.jia.academy.learning.service;
import java.time.LocalDate;import java.util.Objects;
/** Pure, versioned SM-2 style scheduling for deterministic replay and audit. */
public final class SpacedReviewScheduler {
 public record State(int repetitions,double easeFactor,int intervalDays,LocalDate dueDate){}
 public State grade(int rating,int repetitions,double ease,int intervalDays,LocalDate reviewedOn){
  Objects.requireNonNull(reviewedOn);if(rating<0||rating>5||repetitions<0||intervalDays<0||!Double.isFinite(ease)||ease<1.3||ease>3.0)throw new IllegalArgumentException("Review inputs outside bounds");
  if(rating<3)return new State(0,round(Math.max(1.3,ease-.2)),1,reviewedOn.plusDays(1));
  int reps=repetitions+1,days=reps==1?1:(reps==2?6:(int)Math.min(365,Math.round(Math.max(1,intervalDays)*ease)));
  double next=round(Math.max(1.3,Math.min(3.0,ease+.1-(5-rating)*(.08+(5-rating)*.02))));
  return new State(reps,next,days,reviewedOn.plusDays(days));
 }
 private static double round(double n){return Math.round(n*100.0)/100.0;}
}
