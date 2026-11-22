<?php

namespace Tests\Unit;

use App\Services\CurriculumImportValidator;
use PHPUnit\Framework\TestCase;

final class CurriculumImportValidatorTest extends TestCase
{
    public function test_accepts_valid_lesson_and_question_bundle_with_known_categories(): void
    {
        $errors = (new CurriculumImportValidator())->validate($this->bundle(), ['core-java']);
        self::assertSame([], $errors);
    }

    public function test_reports_duplicate_slugs_and_unknown_category_references(): void
    {
        $bundle = $this->bundle();
        $bundle['lessons'][] = $bundle['lessons'][0];
        $bundle['questions'][0]['categorySlug'] = 'missing-category';

        $errors = (new CurriculumImportValidator())->validate($bundle, ['core-java']);
        self::assertContains("$.lessons[1].slug duplicates 'streams-basics'", $errors);
        self::assertContains("$.questions[0].categorySlug references unknown category 'missing-category'", $errors);
    }

    public function test_reports_schema_version_and_missing_external_attribution(): void
    {
        $bundle = $this->bundle();
        $bundle['lessons'][0]['content']['schemaVersion'] = 2;
        $bundle['lessons'][0]['provenance'] = [
            'sourceType' => 'EXTERNAL_REFERENCE',
            'sourceDescription' => 'Imported article',
        ];

        $errors = (new CurriculumImportValidator())->validate($bundle);
        self::assertContains('$.lessons[0].content requires schemaVersion 1 and a blocks array', $errors);
        self::assertContains('$.lessons[0].provenance.attribution is required for external references', $errors);
    }

    public function test_question_answer_must_reference_existing_options_and_have_valid_type(): void
    {
        $bundle = $this->bundle();
        $bundle['questions'][0]['answer']['correctValues'] = ['not-an-option'];
        $bundle['questions'][0]['questionType'] = 'CODING';

        $errors = (new CurriculumImportValidator())->validate($bundle);
        self::assertContains("$.questions[0].answer.correctValues[0] does not reference an option", $errors);
        self::assertContains('$.questions[0].questionType is unsupported', $errors);
    }

    public function test_checked_in_java_foundations_sample_passes_the_import_validator(): void
    {
        $samplePath = dirname(__DIR__, 3).'/docs/curriculum/samples/java-foundations-import-v1.json';
        self::assertFileExists($samplePath);
        $sample = json_decode(file_get_contents($samplePath), true, 512, JSON_THROW_ON_ERROR);
        self::assertSame([], (new CurriculumImportValidator())->validate($sample, ['java-language-fundamentals']));
    }

    private function bundle(): array
    {
        return [
            'formatVersion' => 1,
            'categories' => ['core-java'],
            'lessons' => [[
                'categorySlug' => 'core-java',
                'slug' => 'streams-basics',
                'title' => 'Java Streams Basics',
                'difficulty' => 'BEGINNER',
                'provenance' => ['sourceType' => 'ADMIN_AUTHORED', 'sourceDescription' => 'Original JIA lesson'],
                'content' => ['schemaVersion' => 1, 'blocks' => [['type' => 'overview']]],
            ]],
            'questions' => [[
                'categorySlug' => 'core-java',
                'slug' => 'stream-map-question',
                'questionType' => 'SINGLE_SELECT',
                'difficulty' => 'BEGINNER',
                'prompt' => ['text' => 'Which operation maps values?'],
                'options' => [['value' => 'map', 'label' => 'map'], ['value' => 'filter', 'label' => 'filter']],
                'answer' => ['correctValues' => ['map']],
                'provenance' => ['sourceType' => 'ADMIN_AUTHORED', 'sourceDescription' => 'Original question'],
            ]],
        ];
    }
}
