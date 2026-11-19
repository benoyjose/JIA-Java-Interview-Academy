<?php

namespace Tests\Unit;

use App\Services\LessonImportService;
use PHPUnit\Framework\TestCase;
use Illuminate\Validation\ValidationException;

final class LessonImportValidationTest extends TestCase
{
    public function test_rejects_unsupported_format_version(): void
    {
        $this->expectException(ValidationException::class);
        (new LessonImportService())->import(['formatVersion' => 2, 'lessons' => []], (object) ['id' => 'actor']);
    }

    public function test_rejects_empty_batch(): void
    {
        $this->expectException(ValidationException::class);
        (new LessonImportService())->import(['formatVersion' => 1, 'lessons' => []], (object) ['id' => 'actor']);
    }

    public function test_rejects_external_reference_without_attribution(): void
    {
        $item = [
            'categorySlug' => 'java-language-fundamentals',
            'slug' => 'example-lesson',
            'title' => 'Example lesson',
            'difficulty' => 'BEGINNER',
            'provenance' => ['sourceType' => 'EXTERNAL_REFERENCE', 'sourceDescription' => 'Imported source'],
            'content' => ['schemaVersion' => 1, 'blocks' => []],
        ];
        $this->expectException(ValidationException::class);
        (new LessonImportService())->import(['formatVersion' => 1, 'lessons' => [$item]], (object) ['id' => 'actor']);
    }
}
