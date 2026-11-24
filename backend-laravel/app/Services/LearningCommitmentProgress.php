<?php
namespace App\Services;
use DateTimeImmutable;
use DateTimeZone;
use InvalidArgumentException;
final class LearningCommitmentProgress {
 public function calculate(int $goalMinutes,int $completedMinutes,array $activityDates,string $timezone,DateTimeImmutable $now):array {
  if($goalMinutes<5||$goalMinutes>240) throw new InvalidArgumentException('goalMinutes must be 5..240');
  if($completedMinutes<0) throw new InvalidArgumentException('completedMinutes cannot be negative');
  try{$zone=new DateTimeZone($timezone);}catch(\Throwable $e){throw new InvalidArgumentException('Invalid IANA timezone',0,$e);}
  $today=$now->setTimezone($zone)->format('Y-m-d');
  $dates=array_values(array_unique(array_filter($activityDates,static fn($d)=>is_string($d)&&preg_match('/^\d{4}-\d{2}-\d{2}$/',$d)===1)));
  sort($dates);$set=array_fill_keys($dates,true);$cursor=new DateTimeImmutable($today,$zone);
  if(!isset($set[$today]))$cursor=$cursor->modify('-1 day');$streak=0;
  while(isset($set[$cursor->format('Y-m-d')])){$streak++;$cursor=$cursor->modify('-1 day');}
  return ['goalMinutes'=>$goalMinutes,'completedMinutes'=>$completedMinutes,'progressPercent'=>min(100,(int)floor($completedMinutes*100/$goalMinutes)),'goalReached'=>$completedMinutes>=$goalMinutes,'currentStreakDays'=>$streak,'timezone'=>$zone->getName(),'localDate'=>$today];
 }
}
