<?php
namespace App\\Services;
use InvalidArgumentException;
/** Produces an explainable, bounded study plan from learner-owned progress and due reviews. */
final class LearningPlanComposer {
 public function compose(array $topics,array $dueReviewIds,int $availableMinutes,int $dailyGoalMinutes):array {
  if($availableMinutes<5||$availableMinutes>480)throw new InvalidArgumentException('availableMinutes must be 5..480');
  if($dailyGoalMinutes<5||$dailyGoalMinutes>240)throw new InvalidArgumentException('dailyGoalMinutes must be 5..240');
  $budget=min($availableMinutes,$dailyGoalMinutes);$reviews=array_values(array_unique(array_filter($dueReviewIds,'is_string')));
  $reviewSlots=min(count($reviews),max(1,(int)floor($budget*.35/5)));$reviewIds=array_slice($reviews,0,$reviewSlots);
  $remaining=$budget-count($reviewIds)*5;$plan=[];
  foreach($reviewIds as $id)$plan[]=['kind'=>'REVIEW','reference'=>$id,'minutes'=>5,'reason'=>'Due retention review'];
  $valid=array_values(array_filter($topics,static fn($t)=>is_array($t)&&isset($t['id'],$t['title'])&&is_string($t['id'])&&is_string($t['title'])));
  usort($valid,static fn($a,$b)=>(($b['weakness']??0)<=>($a['weakness']??0))?:strcmp($a['id'],$b['id']));
  foreach($valid as $topic){if($remaining<5)break;$minutes=min(25,$remaining);$plan[]=['kind'=>'LEARN','reference'=>$topic['id'],'title'=>$topic['title'],'minutes'=>$minutes,'reason'=>'Prioritize demonstrated weakness'];$remaining-=$minutes;}
  if(!$plan)$plan[]=['kind'=>'FOUNDATION','reference'=>'daily-recap','minutes'=>min(10,$budget),'reason'=>'Short retrieval warm-up'];
  return ['budgetMinutes'=>$budget,'scheduledMinutes'=>array_sum(array_column($plan,'minutes')),'items'=>$plan];
 }
}
