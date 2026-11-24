package com.jia.academy.learning.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class StudyBlockPlannerTest {
 @Test void respectsBudgetAndSkipsBlankTopics(){
  var r=new StudyBlockPlanner().plan(List.of("Java"," ","Spring","SQL"),45,20);
  assertEquals(List.of(new StudyBlockPlanner.Block("Java",20),new StudyBlockPlanner.Block("Spring",20),new StudyBlockPlanner.Block("SQL",5)),r);
  assertEquals(45,r.stream().mapToInt(StudyBlockPlanner.Block::minutes).sum());
 }
 @Test void rejectsInvalidBlockSize(){assertThrows(IllegalArgumentException.class,()->new StudyBlockPlanner().plan(List.of("Java"),20,0));}
}