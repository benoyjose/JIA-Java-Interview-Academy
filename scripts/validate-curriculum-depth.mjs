import fs from "node:fs";

const lessons = [
  "docs/curriculum/advanced-java/01-java-memory-model-and-safe-publication.md",
  "docs/curriculum/advanced-java/02-collections-generics-and-streams.md",
  "docs/curriculum/advanced-java/03-jvm-internals-gc-and-performance.md",
  "docs/curriculum/spring/01-spring-container-boot-transactions-security.md",
  "docs/curriculum/persistence/01-jpa-hibernate-sql-and-data-engineering.md",
  "docs/curriculum/distributed-systems/01-microservices-resilience-consistency-and-sagas.md",
  "docs/curriculum/messaging/01-kafka-rabbitmq-event-driven-systems.md",
  "docs/curriculum/system-design/01-system-design-capacity-reliability-multiregion.md",
  "docs/curriculum/platform/01-docker-kubernetes-cloud-cicd-gitops.md",
  "docs/curriculum/operations/01-observability-security-incidents-engineering-leadership.md",
];

const requirements = [
  ["learning objectives", /learning outcomes|## outcomes/i],
  ["interview-ready answer", /interview answer|spoken interview answer|interview-ready summary|repeatable interview framework/i],
  ["hands-on exercises or scenarios", /## .*exercises|## .*hands-on scenarios/i],
  ["senior/architect interview assessment", /senior.*interview|interview.*questions|interview prompts|follow-up questions|interview case studies/i],
  ["production, reliability or failure analysis", /production|operational|incident|reliability|failure scenario/i],
  ["reviewer references", /## references|reviewer references/i],
  ["draft/review status", /editorial status|status:\s*draft/i],
];

let failed = false;
for (const file of lessons) {
  if (!fs.existsSync(file)) {
    console.error("Missing curriculum lesson: " + file);
    failed = true;
    continue;
  }
  const content = fs.readFileSync(file, "utf8");
  const missing = requirements.filter(([, pattern]) => !pattern.test(content)).map(([name]) => name);
  if (content.length < 8000) missing.push("minimum deep-content threshold (8,000 characters)");
  if (!/https:\/\//.test(content)) missing.push("at least one attributed external reference");
  if (missing.length) {
    console.error(file + " is incomplete: " + missing.join(", "));
    failed = true;
  } else {
    console.log("Validated " + file + " (" + content.length.toLocaleString() + " characters)");
  }
}
if (failed) process.exit(1);
console.log("Curriculum depth gate passed for " + lessons.length + " domain lessons. This validates draft structure only; it does not approve or publish content.");
