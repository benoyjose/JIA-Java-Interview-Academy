import fs from "node:fs";
import path from "node:path";

const sourceRoot = "backend/src/main/java";
const specPath = "docs/api/openapi.yaml";
const apiPrefix = "/api/v1";
const mappingPattern = /@(Get|Post|Put|Patch|Delete)Mapping\s*(?:\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\))?/g;

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(target) : [target];
  });
}

function normalizeRoute(route) {
  const normalized = "/" + route.split("/").filter(Boolean).join("/");
  if (!normalized.startsWith(apiPrefix)) {
    throw new Error("Controller route does not start with " + apiPrefix + ": " + route);
  }
  return normalized.slice(apiPrefix.length) || "/";
}

const springRoutes = new Set();
for (const file of walk(sourceRoot).filter((name) => name.endsWith("Controller.java"))) {
  const source = fs.readFileSync(file, "utf8");
  const classMapping = source.match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\)[\s\S]*?public class/);
  const classPrefix = classMapping?.[1] ?? "";
  for (const match of source.matchAll(mappingPattern)) {
    const method = match[1].toLowerCase();
    const methodPath = match[2] ?? "";
    springRoutes.add(method + " " + normalizeRoute(classPrefix + methodPath));
  }
}

const documentedRoutes = new Set();
const lines = fs.readFileSync(specPath, "utf8").split(/\r?\n/);
for (let index = 0; index < lines.length; index += 1) {
  const pathMatch = lines[index].match(/^  (\/[^:]+):\s*$/);
  if (!pathMatch) continue;
  for (let cursor = index + 1; cursor < lines.length; cursor += 1) {
    if (/^  \//.test(lines[cursor]) || /^components:/.test(lines[cursor])) break;
    const methodMatch = lines[cursor].match(/^    (get|post|put|patch|delete):\s*$/);
    if (methodMatch) documentedRoutes.add(methodMatch[1] + " " + pathMatch[1]);
  }
}

const missing = [...springRoutes].filter((route) => !documentedRoutes.has(route)).sort();
if (missing.length > 0) {
  console.error("Spring Boot routes missing from OpenAPI:");
  for (const route of missing) console.error("  - " + route);
  process.exit(1);
}

console.log("Verified " + springRoutes.size + " Spring Boot route/method mappings against OpenAPI.");

const laravelRouteFile = "backend-laravel/routes/api.php";
const laravelSource = fs.readFileSync(laravelRouteFile, "utf8");
const laravelRoutes = new Set();
const laravelMappingPattern = /Route::(get|post|put|patch|delete)\s*\(\s*['"]([^'"]+)['"]/g;
for (const match of laravelSource.matchAll(laravelMappingPattern)) {
  const method = match[1].toLowerCase();
  const route = match[2];
  // Laravel's api.php is mounted at /api; the file's v1 group supplies the
  // remaining prefix. OpenAPI paths intentionally omit the common /api/v1.
  const versionedRoute = route.startsWith("/v1/") ? route.slice(3) : route;
  laravelRoutes.add(method + " " + versionedRoute);
}

const undocumentedLaravel = [...laravelRoutes]
  .filter((route) => !documentedRoutes.has(route))
  .sort();
if (undocumentedLaravel.length > 0) {
  console.error("Laravel API routes missing from OpenAPI:");
  for (const route of undocumentedLaravel) console.error("  - " + route);
  process.exit(1);
}
console.log("Verified " + laravelRoutes.size + " Laravel route/method mappings against OpenAPI.");
