<?php
declare(strict_types=1);
namespace Tests\Unit;
use App\Services\CourseProgressReport;
use PHPUnit\Framework\TestCase;
final class CourseProgressReportTest extends TestCase {
 public function testReportsCompletionAndValidScoreAverage(): void {
  self::assertSame(['total'=>3,'completed'=>2,'completionPercent'=>67,'averageScore'=>75.0],
   (new CourseProgressReport())->summarize([['completed'=>true,'score'=>80],['completed'=>true,'score'=>70],['completed'=>false,'score'=>150]]));
 }
 public function testEmptyCourseIsSafe(): void {
  self::assertSame(['total'=>0,'completed'=>0,'completionPercent'=>0,'averageScore'=>null],(new CourseProgressReport())->summarize([]));
 }
}
