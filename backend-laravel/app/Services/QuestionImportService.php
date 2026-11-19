<?php

namespace App\Services;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Symfony\Component\HttpKernel\Exception\BadRequestHttpException;
use Symfony\Component\HttpKernel\Exception\ConflictHttpException;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

final class QuestionImportService
{
    private const TYPES = ['SINGLE_SELECT', 'MULTI_SELECT', 'TRUE_FALSE'];
    private const DIFFICULTIES = ['BEGINNER', 'NOVICE', 'PROFESSIONAL', 'EXPERT'];
    private const SOURCES = ['USER_ORIGINAL', 'ADMIN_AUTHORED', 'EXTERNAL_REFERENCE'];

    public function import(array $batch, object $actor): array
    {
        $this->assertKeys($batch, ['formatVersion', 'questions']);
        if (($batch['formatVersion'] ?? null) !== 1) {
            throw new BadRequestHttpException('Unsupported question import format version.');
        }
        $records = $batch['questions'] ?? null;
        if (! is_array($records) || ! array_is_list($records) || count($records) < 1 || count($records) > 100) {
            throw new BadRequestHttpException('Import batch must contain 1 to 100 questions.');
        }

        $keys = [];
        $resolved = [];
        foreach ($records as $index => $item) {
            $this->validateRecord($item);
            $categorySlug = trim($item['categorySlug']);
            $slug = $item['slug'];
            $key = $categorySlug.'/'.$slug;
            if (isset($keys[$key])) {
                throw new BadRequestHttpException('Duplicate categorySlug/slug in import batch: '.$key);
            }
            $keys[$key] = true;
            $category = DB::table('categories')->join('technologies', 'technologies.id', '=', 'categories.technology_id')
                ->where('categories.slug', $categorySlug)
                ->select('categories.id as category_id', 'categories.technology_id', 'technologies.academy_id')
                ->first();
            if ($category === null) {
                throw new NotFoundHttpException('Category not found: '.$categorySlug);
            }
            if (DB::table('questions')->where('category_id', $category->category_id)->where('slug', $slug)->exists()) {
                throw new ConflictHttpException('Question already exists: '.$key);
            }
            $resolved[] = $category;
        }

        return DB::transaction(function () use ($records, $resolved, $actor): array {
            $created = [];
            foreach ($records as $index => $item) {
                $category = $resolved[$index];
                $questionId = (string) Str::uuid();
                $revisionId = (string) Str::uuid();
                $now = now();
                $source = $item['provenance']['sourceType'];
                DB::table('questions')->insert([
                    'id' => $questionId,
                    'academy_id' => $category->academy_id,
                    'technology_id' => $category->technology_id,
                    'category_id' => $category->category_id,
                    'slug' => $item['slug'],
                    'question_type' => $item['questionType'],
                    'difficulty' => $item['difficulty'],
                    'status' => 'DRAFT',
                    'current_revision' => 1,
                    'created_at' => $now,
                    'updated_at' => $now,
                ]);
                DB::table('question_revisions')->insert([
                    'id' => $revisionId,
                    'question_id' => $questionId,
                    'revision_number' => 1,
                    'prompt_json' => json_encode($item['prompt'], JSON_THROW_ON_ERROR),
                    'options_json' => json_encode($item['options'], JSON_THROW_ON_ERROR),
                    'answer_json' => json_encode($item['answer'], JSON_THROW_ON_ERROR),
                    'explanation' => $item['explanation'],
                    'hint' => $item['hint'] ?? null,
                    'source_type' => $source,
                    'source_description' => $item['provenance']['sourceDescription'],
                    'attribution' => $item['provenance']['attribution'] ?? null,
                    'created_by' => $actor->id,
                    'created_at' => $now,
                ]);
                DB::table('audit_events')->insert([
                    'id' => (string) Str::uuid(),
                    'actor_user_id' => $actor->id,
                    'academy_id' => $category->academy_id,
                    'action' => 'QUESTION_DRAFT_IMPORTED',
                    'entity_type' => 'QUESTION',
                    'entity_id' => $questionId,
                    'metadata_json' => json_encode([
                        'revision' => 1,
                        'sourceType' => $source,
                        'changeNote' => $item['changeNote'] ?? null,
                    ], JSON_THROW_ON_ERROR),
                    'occurred_at' => $now,
                ]);
                $created[] = [
                    'categorySlug' => $item['categorySlug'],
                    'slug' => $item['slug'],
                    'status' => 'DRAFT',
                    'revisionNumber' => 1,
                ];
            }

            return ['formatVersion' => 1, 'importedCount' => count($created), 'questions' => $created];
        });
    }

    private function validateRecord(mixed $item): void
    {
        if (! is_array($item)) {
            throw new BadRequestHttpException('Invalid question import record.');
        }
        $this->assertKeys($item, ['categorySlug','slug','questionType','difficulty','prompt','options','answer','explanation','hint','provenance','changeNote']);
        foreach (['categorySlug' => 100, 'slug' => 120, 'explanation' => 10000] as $field => $max) {
            if (! is_string($item[$field] ?? null) || trim($item[$field]) === '' || strlen($item[$field]) > $max) {
                throw new BadRequestHttpException('Invalid question import record.');
            }
        }
        if (! preg_match('/^[a-z0-9]+(?:-[a-z0-9]+)*$/', $item['slug'])) {
            throw new BadRequestHttpException('Invalid question slug.');
        }
        if (! in_array($item['questionType'] ?? null, self::TYPES, true)
            || ! in_array($item['difficulty'] ?? null, self::DIFFICULTIES, true)) {
            throw new BadRequestHttpException('Unsupported question type or difficulty.');
        }
        $prompt = $item['prompt'] ?? null;
        if (! is_array($prompt)) {
            throw new BadRequestHttpException('Invalid prompt.');
        }
        $this->assertKeys($prompt, ['text', 'code']);
        if (! is_string($prompt['text'] ?? null) || trim($prompt['text']) === '' || strlen($prompt['text']) > 10000) {
            throw new BadRequestHttpException('Invalid prompt text.');
        }
        if (isset($prompt['code']) && $prompt['code'] !== null) {
            if (! is_array($prompt['code'])) {
                throw new BadRequestHttpException('Invalid prompt code.');
            }
            $this->assertKeys($prompt['code'], ['language', 'source']);
            foreach (['language' => 40, 'source' => 20000] as $field => $max) {
                if (! is_string($prompt['code'][$field] ?? null) || trim($prompt['code'][$field]) === '' || strlen($prompt['code'][$field]) > $max) {
                    throw new BadRequestHttpException('Invalid prompt code.');
                }
            }
        }
        $options = $item['options'] ?? null;
        if (! is_array($options) || ! array_is_list($options) || count($options) < 2 || count($options) > 20) {
            throw new BadRequestHttpException('Options must contain 2 to 20 entries.');
        }
        $values = [];
        foreach ($options as $option) {
            if (! is_array($option)) {
                throw new BadRequestHttpException('Invalid option.');
            }
            $this->assertKeys($option, ['value', 'label']);
            foreach (['value' => 100, 'label' => 1000] as $field => $max) {
                if (! is_string($option[$field] ?? null) || trim($option[$field]) === '' || strlen($option[$field]) > $max) {
                    throw new BadRequestHttpException('Invalid option.');
                }
            }
            if (isset($values[$option['value']])) {
                throw new BadRequestHttpException('Option values must be unique.');
            }
            $values[$option['value']] = true;
        }
        $answer = $item['answer'] ?? null;
        if (! is_array($answer)) {
            throw new BadRequestHttpException('Invalid answer.');
        }
        $this->assertKeys($answer, ['correctValues']);
        $correct = $answer['correctValues'] ?? null;
        if (! is_array($correct) || ! array_is_list($correct) || count($correct) < 1 || count($correct) > 20
            || count(array_unique($correct)) !== count($correct)) {
            throw new BadRequestHttpException('Invalid correct values.');
        }
        foreach ($correct as $value) {
            if (! is_string($value) || ! isset($values[$value])) {
                throw new BadRequestHttpException('Correct values must exist in options.');
            }
        }
        if ($item['questionType'] === 'SINGLE_SELECT' && count($correct) !== 1) {
            throw new BadRequestHttpException('SINGLE_SELECT requires exactly one correct value.');
        }
        if ($item['questionType'] === 'TRUE_FALSE'
            && (count($values) !== 2 || ! isset($values['true'], $values['false']) || count($correct) !== 1)) {
            throw new BadRequestHttpException('TRUE_FALSE requires true/false options and one correct value.');
        }
        if (isset($item['hint']) && $item['hint'] !== null && (! is_string($item['hint']) || strlen($item['hint']) > 2000)) {
            throw new BadRequestHttpException('Invalid hint.');
        }
        if (isset($item['changeNote']) && $item['changeNote'] !== null && (! is_string($item['changeNote']) || strlen($item['changeNote']) > 500)) {
            throw new BadRequestHttpException('Invalid changeNote.');
        }
        $provenance = $item['provenance'] ?? null;
        if (! is_array($provenance)) {
            throw new BadRequestHttpException('Invalid provenance.');
        }
        $this->assertKeys($provenance, ['sourceType','sourceDescription','attribution']);
        if (! in_array($provenance['sourceType'] ?? null, self::SOURCES, true)
            || ! is_string($provenance['sourceDescription'] ?? null)
            || trim($provenance['sourceDescription']) === '' || strlen($provenance['sourceDescription']) > 500) {
            throw new BadRequestHttpException('Invalid provenance.');
        }
        if (isset($provenance['attribution']) && $provenance['attribution'] !== null
            && (! is_string($provenance['attribution']) || strlen($provenance['attribution']) > 1000)) {
            throw new BadRequestHttpException('Invalid attribution.');
        }
        if ($provenance['sourceType'] === 'EXTERNAL_REFERENCE'
            && (! is_string($provenance['attribution'] ?? null) || trim($provenance['attribution']) === '')) {
            throw new BadRequestHttpException('External references require attribution.');
        }
    }

    private function assertKeys(array $value, array $allowed): void
    {
        if (array_diff(array_keys($value), $allowed) !== []) {
            throw new BadRequestHttpException('Unknown field in question import request.');
        }
    }
}
