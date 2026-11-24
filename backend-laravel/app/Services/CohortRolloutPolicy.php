<?php
namespace App\\Services;
final class CohortRolloutPolicy {
 public function assign(string $learnerId, string $experimentKey, int $percentage): bool {
  if (trim($learnerId)==='' || trim($experimentKey)==='' || $percentage<0 || $percentage>100) throw new \\InvalidArgumentException('Invalid rollout input');
  if ($percentage===0) return false; if ($percentage===100) return true;
  $bucket=(int)(sprintf('%u',crc32($experimentKey.':'.$learnerId))%100);
  return $bucket<$percentage;
 }
}