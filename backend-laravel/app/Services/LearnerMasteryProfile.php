<?php
namespace App\Services;
use InvalidArgumentException;
final class LearnerMasteryProfile {
 public function evaluate(int $correct,int $answered,int $completed,int $available,?\DateTimeImmutable $last,\DateTimeImmutable $now):array {
  if($answered<0||$correct<0||$correct>$answered||$completed<0||$available<0||$completed>$available) throw new InvalidArgumentException('Invalid mastery evidence');
  if($answered===0)return ['score'=>0,'band'=>'NOT_ASSESSED','reviewDue'=>false,'modelVersion'=>1];
  $score=(int)round(75*$correct/$answered+($available===0?0:25*$completed/$available));
  $due=$last===null||$last->diff($now)->days>=30;
  $band=$due&&$score>=70?'REVIEW_DUE':($score>=85?'MASTERED':($score>=60?'PRACTICING':'DEVELOPING'));
  return ['score'=>$score,'band'=>$band,'reviewDue'=>$due,'modelVersion'=>1];
 }
}