<?php

namespace Tests\\Unit;

use App\\Services\\QuestionImportService;
use PHPUnit\\Framework\\TestCase;
use Symfony\\Component\\HttpKernel\\Exception\\BadRequestHttpException;

final class QuestionImportValidationTest extends TestCase
{
    public function test_rejects_unknown_question_type(): void
    {
        $batch = $this->batch();
        $batch['questions'][0]['questionType'] = 'CODING';
        $this->expectException(BadRequestHttpException::class);
        (new QuestionImportService())->import($batch, (object) ['id' => 'actor']);
    }

    public function test_rejects_answer_value_not_present_in_options(): void
    {
        $batch = $this->batch();
        $batch['questions'][0]['answer']['correctValues'] = ['C'];
        $this->expectException(BadRequestHttpException::class);
        (new QuestionImportService())->import($batch, (object) ['id' => 'actor']);
    }

    public function test_rejects_external_reference_without_attribution(): void
    {
        $batch = $this->batch();
        $batch['questions'][0]['provenance'] = [
            'sourceType' => 'EXTERNAL_REFERENCE',
            'sourceDescription' => 'External source',
        ];
        $this->expectException(BadRequestHttpException::class);
        (new QuestionImportService())->import($batch, (object) ['id' => 'actor']);
    }

    private function batch(): array
    {
        return [
            'formatVersion' => 1,
            'questions' => [[
                'categorySlug' => 'java-language-fundamentals',
                'slug' => 'sample-question',
                'questionType' => 'SINGLE_SELECT',
                'difficulty' => 'BEGINNER',
                'prompt' => ['text' => 'Which answer is correct?'],
                'options' => [
                    ['value' => 'A', 'label' => 'First'],
                    ['value' => 'B', 'label' => 'Second'],
                ],
                'answer' => ['correctValues' => ['A']],
                'explanation' => 'The first option is correct.',
                'provenance' => [
                    'sourceType' => 'ADMIN_AUTHORED',
                    'sourceDescription' => 'JIA editorial team',
                ],
            ]],
        ];
    }
}
