<?php

namespace App\Services;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Symfony\Component\HttpKernel\Exception\BadRequestHttpException;
use Symfony\Component\HttpKernel\Exception\ConflictHttpException;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

final class LessonImportService
{
    private const DIFFICULTIES = ['BEGINNER', 'NOVICE', 'PROFESSIONAL', 'EXPERT'];
    private const SOURCES = ['USER_ORIGINAL', 'ADMIN_AUTHORED', 'EXTERNAL_REFERENCE'];

    public function import(array $batch, object $actor): array
    {
        if (array_diff(array_keys($batch), ['formatVersion', 'lessons']) !== []) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        if (($batch['formatVersion'] ?? null) !== 1) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        $records = $batch['lessons'] ?? null;
        if (! is_array($records) || ! array_is_list($records) || count($records) < 1 || count($records) > 100) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }

        // Validate the complete batch and all existing-key conflicts before the first write.
        $keys = [];
        foreach ($records as $index => $item) {
            $this->validateRecord($item, $index);
            $key = trim($item['categorySlug']).'/'.trim($item['slug']);
            if (isset($keys[$key])) {
                throw new BadRequestHttpException('Invalid lesson import request.');
            }
            $keys[$key] = true;
            $categoryId = DB::table('categories')->where('slug', trim($item['categorySlug']))->value('id');
            if ($categoryId === null) {
                throw new NotFoundHttpException('Category not found: '.trim($item['categorySlug']));
            }
            $exists = DB::table('lessons')->where('slug', trim($item['slug']))->exists();
            if ($exists) {
                throw new ConflictHttpException('Lesson already exists: '.$key);
            }
        }

        return DB::transaction(function () use ($records, $actor): array {
            $created = [];
            foreach ($records as $item) {
                $categoryId = DB::table('categories')->where('slug', trim($item['categorySlug']))->value('id');
                $lessonId = (string) Str::uuid();
                $revisionId = (string) Str::uuid();
                $now = now();
                $content = [
                    'schemaVersion' => 1,
                    'blocks' => $item['content']['blocks'],
                ];
                $difficulty = $item['difficulty'];
                $source = $item['provenance']['sourceType'];
                DB::table('lessons')->insert([
                    'id' => $lessonId,
                    'category_id' => $categoryId,
                    'slug' => trim($item['slug']),
                    'title' => trim($item['title']),
                    'summary' => $item['summary'] ?? null,
                    'difficulty' => $difficulty,
                    'content_json' => json_encode($content, JSON_THROW_ON_ERROR),
                    'content_revision' => 1,
                    'source_type' => $source,
                    'status' => 'DRAFT',
                    'published_revision_id' => null,
                    'draft_revision_id' => null,
                    'created_at' => $now,
                    'updated_at' => $now,
                ]);
                DB::table('lesson_revisions')->insert([
                    'id' => $revisionId,
                    'lesson_id' => $lessonId,
                    'revision_number' => 1,
                    'title' => trim($item['title']),
                    'summary' => $item['summary'] ?? null,
                    'difficulty' => $difficulty,
                    'content_json' => json_encode($content, JSON_THROW_ON_ERROR),
                    'source_type' => $source,
                    'source_description' => trim($item['provenance']['sourceDescription']),
                    'attribution' => $item['provenance']['attribution'] ?? null,
                    'changed_by' => $actor->id,
                    'change_note' => $item['changeNote'] ?? null,
                    'created_at' => $now,
                ]);
                $academyId = DB::table('categories')->join('technologies', 'technologies.id', '=', 'categories.technology_id')
                    ->where('categories.id', $categoryId)->value('technologies.academy_id');
                DB::table('audit_events')->insert([
                    'id' => (string) Str::uuid(),
                    'actor_user_id' => $actor->id,
                    'academy_id' => $academyId,
                    'action' => 'LESSON_DRAFT_IMPORTED',
                    'entity_type' => 'LESSON',
                    'entity_id' => $lessonId,
                    'metadata_json' => json_encode(['revision' => 1, 'sourceType' => $source], JSON_THROW_ON_ERROR),
                    'occurred_at' => $now,
                ]);
                $created[] = ['slug' => trim($item['slug']), 'status' => 'DRAFT', 'revisionNumber' => 1];
            }

            return ['formatVersion' => 1, 'importedCount' => count($created), 'lessons' => $created];
        });
    }

    private function validateRecord(mixed $item, int $index): void
    {
        $field = "lessons.$index";
        if (! is_array($item)) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        $allowed = ['categorySlug','slug','title','summary','difficulty','provenance','content','changeNote'];
        if (array_diff(array_keys($item), $allowed) !== []) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        if (isset($item['summary']) && (! is_string($item['summary']) || strlen($item['summary']) > 700)) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        if (isset($item['changeNote']) && (! is_string($item['changeNote']) || strlen($item['changeNote']) > 500)) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        foreach (['categorySlug', 'slug', 'title'] as $name) {
            if (! is_string($item[$name] ?? null) || trim($item[$name]) === '') {
                throw new BadRequestHttpException('Invalid lesson import request.');
            }
        }
        if (strlen($item['categorySlug']) > 100 || strlen($item['slug']) > 120 || strlen($item['title']) > 200
            || ! preg_match('/^[a-z0-9]+(?:-[a-z0-9]+)*$/', $item['slug'])) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        if (! in_array($item['difficulty'] ?? null, self::DIFFICULTIES, true)) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        $provenance = $item['provenance'] ?? null;
        if (is_array($provenance) && array_diff(array_keys($provenance), ['sourceType','sourceDescription','attribution']) !== []) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        if (! is_array($provenance)
            || ! in_array($provenance['sourceType'] ?? null, self::SOURCES, true)
            || ! is_string($provenance['sourceDescription'] ?? null)
            || trim($provenance['sourceDescription']) === ''
            || strlen($provenance['sourceDescription']) > 500) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        if (isset($provenance['attribution']) && (! is_string($provenance['attribution']) || strlen($provenance['attribution']) > 1000)) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        if ($provenance['sourceType'] === 'EXTERNAL_REFERENCE'
            && (! is_string($provenance['attribution'] ?? null) || trim($provenance['attribution']) === ''
                || strlen($provenance['attribution']) > 1000)) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        $content = $item['content'] ?? null;
        if (is_array($content) && array_diff(array_keys($content), ['schemaVersion','blocks']) !== []) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        if (! is_array($content) || ($content['schemaVersion'] ?? null) !== 1
            || ! is_array($content['blocks'] ?? null) || ! array_is_list($content['blocks']) || count($content['blocks']) > 250) {
            throw new BadRequestHttpException('Invalid lesson import request.');
        }
        foreach ($content['blocks'] as $blockIndex => $block) {
            if (! is_array($block)) {
                throw new BadRequestHttpException('Invalid lesson import request.');
            }
            $type = $block['type'] ?? '';
            if (in_array($type, ['markdown', 'callout'], true)
                && (! is_string($block['body'] ?? null) || trim($block['body']) === '')) {
                throw new BadRequestHttpException('Invalid lesson import request.');
            }
            if ($type === 'code' && ((! is_string($block['source'] ?? null) || trim($block['source']) === '')
                || (! is_string($block['language'] ?? null) || trim($block['language']) === ''))) {
                throw new BadRequestHttpException('Invalid lesson import request.');
            }
            if ($type === 'interviewAnswer' && (! is_array($block['body'] ?? null) || count($block['body']) < 1
                || collect($block['body'])->contains(fn ($point): bool => ! is_string($point) || trim($point) === ''))) {
                throw new BadRequestHttpException('Invalid lesson import request.');
            }
        }
    }
}
