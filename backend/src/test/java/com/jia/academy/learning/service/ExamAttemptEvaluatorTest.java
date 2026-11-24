package com.jia.academy.learning.service;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ExamAttemptEvaluatorTest {
 private final ExamAttemptEvaluator evaluator=new ExamAttemptEvaluator();
 @Test void calculatesRoundedScoreAndPassState(){assertEquals(67,evaluator.evaluate(2,3,60).scorePercent());assertTrue(evaluator.evaluate(2,3,60).passed());assertFalse(evaluator.evaluate(1,3,60).passed());}
 @Test void rejectsInvalidAttempt(){assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(4,3,50));assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(1,0,50));}
}