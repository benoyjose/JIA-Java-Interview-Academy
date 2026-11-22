import fs from "node:fs";

const routeFile = "backend-laravel/routes/api.php";
const specFile = "docs/api/openapi.yaml";
const source = fs.readFileSync(routeFile, "utf8");
const spec = fs.readFileSync(specFile, "utf8");
const documented = new Map();

const pathLines = [...spec.matchAll(/^  (\/[^:]+):\s*$/gm)];
for (const pathMatch of pathLines) {
  const start = pathMatch.index + pathMatch[0].length;
  const nextPath = spec.slice(start).search(/^  \//m);
  const nextComponents = spec.slice(start).search(/^components:/m);
  const ends = [
    nextPath < 0 ? spec.length : start + nextPath,
    nextComponents < 0 ? spec.length : start + nextComponents,
  ];
  const block = spec.slice(start, Math.min(...ends));
  const methods = new Set(
    [...block.matchAll(/^    (get|post|put|patch|delete):\s*$/gm)].map((m) => m[1]),
  );
  documented.set(pathMatch[1], methods);
}

// Accept either quote style and harmless whitespace around the route
// declaration. Keep literal route strings required so the checker cannot
// silently skip a new declaration form and report a false-green result.
const routeDeclarationPattern =
  /Route::\s*(get|post|put|patch|delete)\s*\(\s*(['"])([^'"]+)\2\s*,/g;
const declarations = [...source.matchAll(/Route::\s*(get|post|put|patch|delete)\s*\(/g)];
const routes = [...source.matchAll(routeDeclarationPattern)];
if (routes.length !== declarations.length) {
  console.error(
    "Laravel route checker could not parse every literal Route:: HTTP declaration. " +
    "Parsed " + routes.length + " of " + declarations.length +
    "; update the checker for the new declaration syntax before merging.",
  );
  process.exit(1);
}

const missing = [];
for (const match of routes) {
  const method = match[1];
  const route = match[3].replace(/^\//, "").replace(/^v1\//, "");
  const path = "/" + route;
  if (!documented.get(path)?.has(method)) {
    missing.push(method.toUpperCase() + " " + path);
  }
}
if (missing.length) {
  console.error("Laravel routes missing from OpenAPI:");
  for (const route of missing) console.error("  - " + route);
  process.exit(1);
}
console.log("Verified " + routes.length + " Laravel route/method entries against OpenAPI.");
