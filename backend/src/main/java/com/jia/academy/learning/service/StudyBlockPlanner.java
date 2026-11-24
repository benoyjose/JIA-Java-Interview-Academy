package com.jia.academy.learning.service;
import java.util.ArrayList;
import java.util.List;
public final class StudyBlockPlanner {
 public record Block(String topic,int minutes){}
 public List<Block> plan(List<String> topics,int availableMinutes,int blockMinutes){
  if(availableMinutes<0||blockMinutes<1)throw new IllegalArgumentException("Invalid time budget");
  List<Block> blocks=new ArrayList<>(); int remaining=availableMinutes;
  for(String topic:topics){if(topic==null||topic.isBlank())continue;if(remaining==0)break;
   int allocation=Math.min(blockMinutes,remaining);blocks.add(new Block(topic.trim(),allocation));remaining-=allocation;}
  return List.copyOf(blocks);
 }
}