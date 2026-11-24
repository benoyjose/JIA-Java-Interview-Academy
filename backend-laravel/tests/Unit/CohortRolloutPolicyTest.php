<?php
namespace Tests\\Unit;
use App\\Services\\CohortRolloutPolicy; use InvalidArgumentException; use PHPUnit\\Framework\\TestCase;
final class CohortRolloutPolicyTest extends TestCase {
 public function test_assignment_is_stable_and_percentage_bounded():void {
  $p=new CohortRolloutPolicy(); self::assertSame($p->assign('u-1','exp-a',37),$p->assign('u-1','exp-a',37));
  self::assertFalse($p->assign('u-1','exp-a',0)); self::assertTrue($p->assign('u-1','exp-a',100));
 }
 public function test_invalid_configuration_is_rejected():void { $this->expectException(InvalidArgumentException::class); (new CohortRolloutPolicy())->assign('','exp',10); }
}