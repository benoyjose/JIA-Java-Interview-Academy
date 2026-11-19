<?php

namespace App\Http\Controllers\Api\V1;

use Illuminate\Database\Query\Builder;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\DB;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

final class CatalogueController
{
    public function technologies(): JsonResponse
    {
        $rows = DB::table('technologies')
            ->where('status', 'PUBLISHED')
            ->orderBy('sort_order')
            ->get(['id', 'slug', 'name', 'description', 'sort_order']);

        return response()->json($rows->map(fn (object $row): array => [
            'id' => $row->id,
            'slug' => $row->slug,
            'name' => $row->name,
            'description' => $row->description,
            'sortOrder' => $row->sort_order,
        ])->values());
    }

    public function technology(string $slug): JsonResponse
    {
        $row = $this->published('technologies', $slug, ['id', 'slug', 'name', 'description', 'sort_order']);

        return response()->json([
            'id' => $row->id,
            'slug' => $row->slug,
            'name' => $row->name,
            'description' => $row->description,
            'sortOrder' => $row->sort_order,
        ]);
    }

    public function categories(string $technologySlug): JsonResponse
    {
        $technology = DB::table('technologies')
            ->where('slug', $technologySlug)
            ->where('status', 'PUBLISHED')
            ->first(['id']);

        if ($technology === null) {
            throw new NotFoundHttpException('Technology not found');
        }

        $rows = DB::table('categories')
            ->where('technology_id', $technology->id)
            ->where('status', 'PUBLISHED')
            ->orderBy('sort_order')
            ->get(['id', 'slug', 'name', 'sort_order']);

        return response()->json($rows->map(fn (object $row): array => [
            'id' => $row->id,
            'slug' => $row->slug,
            'name' => $row->name,
            'sortOrder' => $row->sort_order,
            'technologySlug' => $technologySlug,
        ])->values());
    }

    public function lessons(string $categorySlug): JsonResponse
    {
        $category = DB::table('categories')
            ->where('slug', $categorySlug)
            ->where('status', 'PUBLISHED')
            ->first(['id']);

        if ($category === null) {
            throw new NotFoundHttpException('Category not found');
        }

        $rows = DB::table('lessons')
            ->where('category_id', $category->id)
            ->where('status', 'PUBLISHED')
            ->orderBy('title')
            ->get(['id', 'slug', 'title', 'summary', 'difficulty', 'content_revision']);

        return response()->json($rows->map(fn (object $row): array => [
            'id' => $row->id,
            'slug' => $row->slug,
            'title' => $row->title,
            'summary' => $row->summary,
            'difficulty' => $row->difficulty,
            'contentRevision' => $row->content_revision,
        ])->values());
    }

    public function lesson(string $slug): JsonResponse
    {
        $row = $this->published('lessons', $slug, [
            'id', 'slug', 'title', 'summary', 'difficulty',
            'content_revision', 'source_type', 'content_json',
        ]);

        return response()->json([
            'id' => $row->id,
            'slug' => $row->slug,
            'title' => $row->title,
            'summary' => $row->summary,
            'difficulty' => $row->difficulty,
            'contentRevision' => $row->content_revision,
            'sourceType' => $row->source_type,
            'contentJson' => $row->content_json,
        ]);
    }

    private function published(string $table, string $slug, array $columns): object
    {
        /** @var Builder $query */
        $query = DB::table($table);

        $row = $query->where('slug', $slug)
            ->where('status', 'PUBLISHED')
            ->first($columns);

        if ($row === null) {
            throw new NotFoundHttpException(ucfirst(rtrim($table, 's')).' not found');
        }

        return $row;
    }
}
