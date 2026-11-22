<?php

namespace App\Services;

/** Offline validation for reviewable curriculum bundles; performs no database writes. */
final class CurriculumImportValidator
{
    public function validate(array $bundle, ?array $knownCategorySlugs = null): array
    {
        $errors = [];
        $this->keys($bundle, ['formatVersion', 'categories', 'lessons', 'questions'], '$', $errors);
        if (($bundle['formatVersion'] ?? null) !== 1) {
            $errors[] = '$.formatVersion must equal 1';
        }

        $categories = $bundle['categories'] ?? [];
        if (! is_array($categories) || ! array_is_list($categories)) {
            $errors[] = '$.categories must be an array when provided';
            $categories = [];
        }
        $categorySet = [];
        foreach ($categories as $i => $category) {
            $slug = is_array($category) ? ($category['slug'] ?? null) : $category;
            if (! is_string($slug) || trim($slug) === '') {
                $errors[] = "$.categories[$i].slug is required";
                continue;
            }
            if (isset($categorySet[$slug])) $errors[] = "$.categories[$i].slug duplicates '$slug'";
            $categorySet[$slug] = true;
        }
        $known = $knownCategorySlugs === null ? ($categorySet ?: null) : array_fill_keys($knownCategorySlugs, true);

        $lessons = $bundle['lessons'] ?? [];
        if (! is_array($lessons) || ! array_is_list($lessons)) {
            $errors[] = '$.lessons must be an array';
            $lessons = [];
        }
        if (count($lessons) > 100) $errors[] = '$.lessons exceeds 100 records';
        $lessonSlugs = [];
        foreach ($lessons as $i => $lesson) {
            $path = "$.lessons[$i]";
            if (! is_array($lesson)) { $errors[] = "$path must be an object"; continue; }
            foreach (['categorySlug', 'slug', 'title', 'difficulty', 'provenance', 'content'] as $required) {
                if (! array_key_exists($required, $lesson)) $errors[] = "$path.$required is required";
            }
            $this->validateCategory($lesson['categorySlug'] ?? null, "$path.categorySlug", $known, $errors);
            $slug = $lesson['slug'] ?? null;
            if (is_string($slug) && $slug !== '') {
                if (isset($lessonSlugs[$slug])) $errors[] = "$path.slug duplicates '$slug'";
                $lessonSlugs[$slug] = true;
                if (! preg_match('/^[a-z0-9]+(?:-[a-z0-9]+)*$/', $slug)) $errors[] = "$path.slug has invalid format";
            }
            if (! is_string($lesson['title'] ?? null) || trim($lesson['title']) === '') $errors[] = "$path.title must be non-empty";
            $this->validateContent($lesson['content'] ?? null, "$path.content", $errors);
            $this->validateProvenance($lesson['provenance'] ?? null, "$path.provenance", $errors);
        }

        $questions = $bundle['questions'] ?? [];
        if (! is_array($questions) || ! array_is_list($questions)) {
            $errors[] = '$.questions must be an array';
            $questions = [];
        }
        if (count($questions) > 100) $errors[] = '$.questions exceeds 100 records';
        $questionKeys = [];
        foreach ($questions as $i => $question) {
            $path = "$.questions[$i]";
            if (! is_array($question)) { $errors[] = "$path must be an object"; continue; }
            foreach (['categorySlug', 'slug', 'questionType', 'difficulty', 'prompt', 'options', 'answer', 'provenance'] as $required) {
                if (! array_key_exists($required, $question)) $errors[] = "$path.$required is required";
            }
            $this->validateCategory($question['categorySlug'] ?? null, "$path.categorySlug", $known, $errors);
            $slug = $question['slug'] ?? null;
            $category = $question['categorySlug'] ?? '';
            if (is_string($slug) && $slug !== '') {
                $key = $category.'/'.$slug;
                if (isset($questionKeys[$key])) $errors[] = "$path.slug duplicates '$key'";
                $questionKeys[$key] = true;
            }
            $options = $question['options'] ?? null;
            $optionValues = [];
            if (! is_array($options) || ! array_is_list($options) || count($options) < 2) {
                $errors[] = "$path.options must contain at least two entries";
            } else {
                foreach ($options as $oi => $option) {
                    $value = is_array($option) ? ($option['value'] ?? null) : null;
                    if (! is_string($value) || trim($value) === '') $errors[] = "$path.options[$oi].value is required";
                    elseif (isset($optionValues[$value])) $errors[] = "$path.options[$oi].value duplicates '$value'";
                    else $optionValues[$value] = true;
                }
            }
            $correct = $question['answer']['correctValues'] ?? null;
            if (! is_array($correct) || ! array_is_list($correct) || count($correct) < 1) {
                $errors[] = "$path.answer.correctValues must be a non-empty array";
            } else {
                foreach ($correct as $ci => $value) {
                    if (! is_string($value) || ! isset($optionValues[$value])) $errors[] = "$path.answer.correctValues[$ci] does not reference an option";
                }
                if (($question['questionType'] ?? null) === 'SINGLE_SELECT' && count($correct) !== 1) {
                    $errors[] = "$path.answer.correctValues must contain one value for SINGLE_SELECT";
                }
            }
            $this->validateProvenance($question['provenance'] ?? null, "$path.provenance", $errors);
        }

        if ($lessons === [] && $questions === []) $errors[] = '$ must contain at least one lesson or question';
        return array_values(array_unique($errors));
    }

    private function validateCategory(mixed $slug, string $path, ?array $known, array &$errors): void
    {
        if (! is_string($slug) || trim($slug) === '') {
            $errors[] = "$path is required";
        } elseif ($known !== null && ! isset($known[$slug])) {
            $errors[] = "$path references unknown category '$slug'";
        }
    }

    private function validateContent(mixed $content, string $path, array &$errors): void
    {
        if (! is_array($content) || ($content['schemaVersion'] ?? null) !== 1
            || ! is_array($content['blocks'] ?? null) || ! array_is_list($content['blocks'])) {
            $errors[] = "$path requires schemaVersion 1 and a blocks array";
            return;
        }
        if (count($content['blocks']) > 250) $errors[] = "$path.blocks exceeds 250 entries";
        foreach ($content['blocks'] as $i => $block) {
            if (! is_array($block) || ! is_string($block['type'] ?? null) || trim($block['type']) === '') {
                $errors[] = "$path.blocks[$i].type is required";
            }
        }
    }

    private function validateProvenance(mixed $value, string $path, array &$errors): void
    {
        if (! is_array($value)
            || ! in_array($value['sourceType'] ?? null, ['USER_ORIGINAL','ADMIN_AUTHORED','EXTERNAL_REFERENCE'], true)
            || ! is_string($value['sourceDescription'] ?? null) || trim($value['sourceDescription']) === '') {
            $errors[] = "$path requires sourceType and sourceDescription";
            return;
        }
        if (($value['sourceType'] ?? null) === 'EXTERNAL_REFERENCE'
            && (! is_string($value['attribution'] ?? null) || trim($value['attribution']) === '')) {
            $errors[] = "$path.attribution is required for external references";
        }
    }

    private function keys(array $value, array $allowed, string $path, array &$errors): void
    {
        foreach (array_diff(array_keys($value), $allowed) as $key) $errors[] = "$path contains unsupported field '$key'";
    }
}
