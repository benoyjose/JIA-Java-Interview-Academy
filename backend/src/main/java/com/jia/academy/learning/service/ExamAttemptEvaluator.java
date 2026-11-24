package com.jia.academy.learning.service;
import java.util.Objects;
public final class ExamAttemptEvaluator {
 public record Result(int scorePercent, boolean passed, int correct, int total) {}
 public Result evaluate(int correct, int total, int passMark) {
  if(total<=0 || correct<0 || correct>total || passMark<0 || passMark>100) throw new IllegalArgumentException("Invalid exam attempt");
  int score=(int)Math.round(correct*100.0/total);
  return new Result(score,score>=passMark,correct,total);
 }
}