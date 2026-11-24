<?php
namespace Tests\Unit;
use App\Services\LearnerMasteryProfile;
use DateTimeImmutable;
use InvalidArgumentException;
use PHPUnit\Framework\TestCase;
final class LearnerMasteryProfileTest extends TestCase {
 public function test_no_assessment_is_not_mastery():void {
  $r=(new LearnerMasteryProfile())->evaluate(0,0,0,4,null,new DateTimeImmutable('2026-11-24'));
  self::assertSame('NOT_ASSESSED',$r['band']); self::assertSame(0,$r['score']);
 }
 public function test_stale_high_performance_evidence_requires_review():void {
  $r=(new LearnerMasteryProfile())->evaluate(9,10,8,10,new DateTimeImmutable('2026-10-01'),new DateTimeImmutable('2026-11-24'));
  self::assertSame(88,$r['score']); self::assertSame('REVIEW_DUE',$r['band']);
 }
 public function test_impossible_counts_are_rejected():void {
  $this->expectException(InvalidArgumentException::class);
  (new LearnerMasteryProfile())->evaluate(3,2,1,4,null,new DateTimeImmutable());
 }
}