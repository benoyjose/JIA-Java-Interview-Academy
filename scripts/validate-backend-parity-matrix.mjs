import fs from "node:fs";

const file = "docs/api/BACKEND_PARITY_MATRIX.md";
const text = fs.readFileSync(file, "utf8");
const rows = [...text.matchAll(/^\| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$/gm)]
  .map((match) => match.slice(1).map((cell) => cell.trim()));

const header = ["Capability", "Contract / route family", "Spring Boot evidence", "Laravel evidence", "Policy / follow-up"];
if (!text.includes(header.join(" | "))) {
  console.error("Parity matrix is missing its canonical table header.");
  process.exit(1);
}
const data = rows.filter((row) => row[0] !== "Capability");
if (data.length < 8) {
  console.error("Parity matrix unexpectedly contains fewer than eight capability rows.");
  process.exit(1);
}
const names = new Set();
const errors = [];
for (const [capability, contract, spring, laravel, policy] of data) {
  if (!capability || !contract || !spring || !laravel || !policy) {
    errors.push("Every matrix row must populate all five columns.");
  }
  if (names.has(capability)) errors.push("Duplicate capability row: " + capability);
  names.add(capability);
  if (!/Parity-required|Spring-first|Gap/.test(policy)) {
    errors.push("Capability policy must state Parity-required, Spring-first, or Gap: " + capability);
  }
}
if (errors.length) {
  for (const error of errors) console.error(error);
  process.exit(1);
}
console.log("Validated " + data.length + " backend parity matrix capabilities.");
