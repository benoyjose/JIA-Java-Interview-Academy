<?php
namespace Tests\Unit;
use App\Services\LearningCommitmentProgress;
use DateTimeImmutable;
use PHPUnit\Framework\TestCase;
final class LearningCommitmentProgressTest extends TestCase {
 public function testTimezoneAwareStreakAndCappedProgress():void {
  $r=(new LearningCommitmentProgress())->calculate(30,45,['2026-11-24','2026-11-23','2026-11-22'],'Asia/Kolkata',new DateTimeImmutable('2026-11-24T01:00:00+00:00'));
  self::assertSame(100,$r['progressPercent']);self::assertTrue($r['goalReached']);self::assertSame(3,$r['currentStreakDays']);
 }
 public function testMissedDayBreaksStreak():void {
  $r=(new LearningCommitmentProgress())->calculate(20,5,['2026-11-24','2026-11-22'],'UTC',new DateTimeImmutable('2026-11-24'));
  self::assertSame(1,$r['currentStreakDays']);self::assertSame(25,$r['progressPercent']);
 }
 public function testInvalidGoalIsRejected():void {
  $this->expectException(\InvalidArgumentException::class);
  (new LearningCommitmentProgress())->calculate(0,0,[],'UTC',new DateTimeImmutable());
 }
}
