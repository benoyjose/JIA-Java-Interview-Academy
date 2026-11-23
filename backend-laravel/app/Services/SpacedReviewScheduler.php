<?php
namespace App\Services;
final class SpacedReviewScheduler {
 public function schedule(int $rating,int $repetitions,float $ease,int $intervalDays):array {
  if($rating<0||$rating>5||$repetitions<0||$intervalDays<0||!is_finite($ease)||$ease<1.3||$ease>3.0) throw new \InvalidArgumentException('Review inputs outside bounds');
  if($rating<3)return ['repetitions'=>0,'ease'=>max(1.3,round($ease-.2,2)),'intervalDays'=>1,'dueAt'=>now()->addDay()];
  $reps=$repetitions+1;$interval=$reps===1?1:($reps===2?6:(int)min(365,round(max(1,$intervalDays)*$ease)));
  $nextEase=min(3.0,max(1.3,round($ease+.1-(5-$rating)*(.08+(5-$rating)*.02),2)));
  return ['repetitions'=>$reps,'ease'=>$nextEase,'intervalDays'=>$interval,'dueAt'=>now()->addDays($interval)];
 }
}
