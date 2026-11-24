<?php
declare(strict_types=1);
namespace Tests\Unit;
use App\Services\LearningPathUnlockPolicy;
use PHPUnit\Framework\TestCase;
final class LearningPathUnlockPolicyTest extends TestCase {
 public function testRequiresEveryUniquePrerequisite():void {
  $r=(new LearningPathUnlockPolicy())->evaluate(['advanced'=>['intro','intro','oop']],['intro'=>true]);
  self::assertFalse($r['advanced']['unlocked']); self::assertSame(['oop'],$r['advanced']['missingPrerequisites']);
 }
 public function testEmptyPrerequisitesUnlock():void {
  self::assertTrue((new LearningPathUnlockPolicy())->evaluate(['start'=>[]],[])['start']['unlocked']);
 }
}