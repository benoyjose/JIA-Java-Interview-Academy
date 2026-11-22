<?php

namespace App\Console\Commands;

use App\Services\CurriculumImportValidator;
use Illuminate\Console\Command;
use JsonException;

final class ValidateCurriculumImport extends Command
{
    protected $signature = 'jia:validate-curriculum {file : JSON curriculum bundle path} {--categories= : Optional JSON category catalog for reference validation}';

    protected $description = 'Validate curriculum lesson/question imports without writing to the database';

    public function handle(CurriculumImportValidator $validator): int
    {
        $file = $this->argument('file');
        $path = is_file($file) ? $file : base_path($file);
        if (! is_file($path) || ! is_readable($path)) {
            $this->error("Cannot read curriculum file: {$file}");
            return self::FAILURE;
        }

        try {
            $bundle = json_decode(file_get_contents($path), true, 512, JSON_THROW_ON_ERROR);
        } catch (JsonException $exception) {
            $this->error('Invalid JSON: '.$exception->getMessage());
            return self::FAILURE;
        }
        if (! is_array($bundle)) {
            $this->error('Curriculum JSON root must be an object.');
            return self::FAILURE;
        }

        $knownCategories = null;
        $categoryFile = $this->option('categories');
        if (is_string($categoryFile) && trim($categoryFile) !== '') {
            $categoryPath = is_file($categoryFile) ? $categoryFile : base_path($categoryFile);
            if (! is_file($categoryPath) || ! is_readable($categoryPath)) {
                $this->error("Cannot read category catalog: {$categoryFile}");
                return self::FAILURE;
            }
            try {
                $catalog = json_decode(file_get_contents($categoryPath), true, 512, JSON_THROW_ON_ERROR);
            } catch (JsonException $exception) {
                $this->error('Invalid category catalog JSON: '.$exception->getMessage());
                return self::FAILURE;
            }
            $entries = is_array($catalog) && isset($catalog['categories']) ? $catalog['categories'] : $catalog;
            if (! is_array($entries) || ! array_is_list($entries)) {
                $this->error('Category catalog must be an array or contain a categories array.');
                return self::FAILURE;
            }
            $knownCategories = array_map(
                static fn ($entry) => is_array($entry) ? ($entry['slug'] ?? '') : $entry,
                $entries
            );
        }

        $errors = $validator->validate($bundle, $knownCategories);
        $lessonCount = is_array($bundle['lessons'] ?? null) ? count($bundle['lessons']) : 0;
        $questionCount = is_array($bundle['questions'] ?? null) ? count($bundle['questions']) : 0;
        $this->line("Validated {$lessonCount} lesson(s) and {$questionCount} question(s).");
        if ($errors === []) {
            $this->info('Curriculum bundle is valid. No database changes were made.');
            return self::SUCCESS;
        }

        $this->error('Curriculum validation found '.count($errors).' issue(s):');
        foreach ($errors as $error) $this->line(' - '.$error);
        return self::FAILURE;
    }
}
