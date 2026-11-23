<?php
namespace Tests\Unit;use App\Services\SpacedReviewScheduler;use InvalidArgumentException;use PHPUnit\Framework\TestCase;
class SpacedReviewSchedulerTest extends TestCase{
 public function testIntervalsAndFailureReset():void{$s=new SpacedReviewScheduler();$this->assertSame(1,$s->schedule(4,0,2.5,0)['intervalDays']);$this->assertSame(6,$s->schedule(4,1,2.5,1)['intervalDays']);$r=$s->schedule(1,7,1.35,20);$this->assertSame(0,$r['repetitions']);$this->assertSame(1.3,$r['ease']);}
 public function testRejectsInvalidRating():void{$this->expectException(InvalidArgumentException::class);(new SpacedReviewScheduler())->schedule(6,0,2.5,0);}
}
