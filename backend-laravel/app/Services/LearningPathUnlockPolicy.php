<?php
declare(strict_types=1);
namespace App\Services;
final class LearningPathUnlockPolicy {
 public function evaluate(array $prerequisites, array $completed): array {
  $result=[];
  foreach($prerequisites as $lesson=>$required){
   $missing=array_values(array_filter(array_unique($required),static fn(string $id):bool=>!($completed[$id]??false)));
   $result[$lesson]=['unlocked'=>$missing===[],'missingPrerequisites'=>$missing];
  }
  return $result;
 }
}