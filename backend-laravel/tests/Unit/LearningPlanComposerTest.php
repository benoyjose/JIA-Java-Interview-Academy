<?php
namespace Tests\\Unit;
use App\\Services\\LearningPlanComposer;
use InvalidArgumentException;
use PHPUnit\\Framework\\TestCase;
final class LearningPlanComposerTest extends TestCase {
 public function test_composer_prioritizes_weak_topics_and_respects_budget():void {
  $plan=(new LearningPlanComposer())->compose([['id'=>'strong','title'=>'Strong','weakness'=>1],['id'=>'weak','title'=>'Weak','weakness'=>9]],['r1','r1','r2'],30,25);
  self::assertSame('r1',$plan['items'][0]['reference']);self::assertSame('weak',$plan['items'][2]['reference']);self::assertLessThanOrEqual(25,$plan['scheduledMinutes']);
 }
 public function test_invalid_goal_is_rejected():void {$this->expectException(InvalidArgumentException::class);(new LearningPlanComposer())->compose([],[],20,0);}
}
