# OpenClover Agent Reference

> Drop this into your agent's system prompt or CLAUDE.md when working with Clover coverage data.

## Reading agent-feedback.json

The feedback file has three sections: `summary`, `tests`, and `topUncovered`.

### summary — Project-Level Metrics (Split by Source Type)

```json
"summary": {
  "app": {
    "statements": { "covered": 1207, "total": 1946, "pct": 62.0 },
    "branches":   { "covered": 538,  "total": 966,  "pct": 55.7 },
    "methods":    { "covered": 837,  "total": 2194, "pct": 38.1 }
  },
  "test": {
    "statements": { "covered": 4982, "total": 5018, "pct": 99.3 },
    "branches":   { "covered": 102,  "total": 186,  "pct": 54.8 },
    "methods":    { "covered": 831,  "total": 996,  "pct": 83.4 }
  },
  "combined": {
    "statements": { "covered": 6189, "total": 6964, "pct": 88.9 },
    "branches":   { "covered": 640,  "total": 1152, "pct": 55.6 },
    "methods":    { "covered": 1668, "total": 3190, "pct": 52.3 }
  }
}
```

**Use `summary.app` for all coverage decisions.** This is the production code — the code that matters.

- `summary.test` shows how well the test code itself is covered. Test statement coverage is naturally high (~99%) because tests execute their own lines. This is expected and not actionable.
- `summary.combined` is the raw total (app + test). Rarely useful. It inflates statement coverage because test sources dominate.

**What matters in `summary.app`:**
- **Branch coverage** is the most honest metric. Statement coverage can be inflated by incidental execution. Branch coverage proves both sides of every `if`/`while`/`for` were tested.
- **Method coverage** below 50% means large parts of the production codebase have never been called by any test.
- Don't chase 100%. Diminishing returns start around 80% for statements, 60% for branches.

### tests — Per-Test Summary

```json
"tests": [
  {
    "name": "com.example.OrderServiceTest.testCalculateTotal",
    "status": "pass",
    "durationMs": 45,
    "linesCovered": 22,
    "uniqueLinesCovered": 3
  }
]
```

**What matters:**
- `uniqueLinesCovered` is the key metric. It's the number of lines ONLY this test covers — no other test reaches them. If it's 0, this test is redundant (it exercises paths already covered by other tests).
- `status`: "pass", "fail", or "error". Failed tests still contribute coverage data for the lines they executed before failing.
- The list may be capped (default 50 tests). Not all tests are shown.

### topUncovered — Where to Focus

```json
"topUncovered": [
  {
    "file": "OrderService.java",
    "uncoveredLines": [25, 26, 30, 31, 45, 46, 47],
    "uncoveredBranches": [
      { "line": 30, "type": "false" },
      { "line": 45, "type": "true" }
    ]
  }
]
```

**This is your primary action item.** These are production files sorted by number of uncovered lines (worst first). Write tests targeting these files.

**What matters:**
- `uncoveredLines`: exact line numbers with zero coverage. Write tests that execute these lines.
- `uncoveredBranches`: decision points where one path was never taken. `"type": "false"` means the condition was always true — write a test where it's false. `"type": "true"` means the opposite.
- Files with many uncovered lines AND uncovered branches are the highest priority — they have untested logic paths.

---

## Querying Specific Files

When you need detail on a specific class:

```bash
mvn -B -q clover:uncovered -Dclass=OrderService
```

Response includes:
- `uncoveredLines`: exact line numbers
- `uncoveredBranches`: with `line`, `type` ("true"/"false"), and `method` name
- `coveredBy`: which existing tests cover nearby lines — use this to decide which test class to put your new test in
- `coverage`: per-file metrics (statements, branches, methods with covered/total/pct)

**Interpreting `coveredBy`:**
```json
"coveredBy": {
  "15-22": ["OrderServiceTest.testCalculateTotal"],
  "35-42": ["OrderServiceTest.testProcessOrder", "IntegrationTest.testEndToEnd"]
}
```
Lines 15-22 are covered by `testCalculateTotal`. Your new test for uncovered lines 25-26 should probably go in `OrderServiceTest` since it's already testing this area.

---

## Querying Test Impact

After writing a test, verify it hit the target:

```bash
mvn -B -q clover:test-feedback -Dtest=OrderServiceTest.testNullInput
```

Check:
- `coverage.{file}.linesCovered` — did it cover the lines you targeted?
- `uniqueCoverage.{file}` — are any of those lines unique to this test? If not, you're duplicating existing coverage.

---

## Getting Test Suggestions

```bash
mvn -B -q clover:suggest -Dclass=OrderService -Dmax=5
```

Returns methods ranked by uncovered line count. Start with rank 1.

Each suggestion includes:
- `method`: which method to test
- `uncoveredLines`: specific lines to target
- `uncoveredBranches`: specific branches to exercise
- `complexity`: higher = more code paths to test
- `coveredPct`: current coverage of this method

---

## Common Gotchas

1. **Use `summary.app`, not `summary.combined`.** The `summary.combined` section includes both production and test code. Test files inflate combined coverage because they execute their own lines. Always use `summary.app` for coverage decisions.

2. **Stale data.** If `"stale": true` in the envelope, source files have been modified since the last test run. The coverage data is outdated. Re-run tests before making decisions.

3. **Branch types.** `"true"` and `"false"` refer to the boolean outcome of the condition, not "good" and "bad". `"type": "false"` on an `if (x == null)` means `x` was always null in tests — write a test where `x` is not null.

4. **Zero tests shown.** If `tests` array is empty, per-test recording may not be active. The project needs `globalSliceStart`/`globalSliceEnd` instrumentation for test-level tracking. Aggregate coverage still works.

5. **Class not found errors.** The `-Dclass` parameter matches by filename (e.g., `OrderService` matches `OrderService.java`). Use the simple class name, not the fully qualified package path.

6. **Lines vs statements.** Multiple statements can be on one line (`a = 1; b = 2;`). Clover counts statements, not lines. The "uncovered lines" in the JSON are deduplicated — each line appears once even if it has multiple statements.

7. **Source-only branches.** The `uncoveredBranches` array only includes branches from source-level instrumentation (if/else, while, for, ternary). Bytecode-only branches from Lombok-generated code (equals, hashCode, builders) are NOT included. This is intentional — you can't write a test targeting a bytecode-only branch since there's no source line to exercise. Focus on the branches shown.

---

## Decision Framework

| Situation | Action |
|-----------|--------|
| High statement %, low branch % | Write tests for the false branches shown in `uncoveredBranches` |
| Method coverage below 50% | Query `clover:suggest` — focus on methods never called |
| `uniqueLinesCovered` is 0 for a test | This test is redundant. Consider if it tests different behavior despite same paths |
| `stale: true` | Re-run `mvn test` before querying |
| Coverage stopped improving | Switch to a different class. Check `clover:suggest` for new targets |
| Refactoring code | First query `clover:uncovered` for that file. If uncovered lines exist in the area you're changing, write tests first |

---

## Workflow Commands (Copy-Paste Ready)

```bash
# Full cycle: instrument, test, report, generate agent feedback
mvn clean clover:setup test clover:aggregate clover:clover clover:agent-feedback -Dclover.agent=true

# Query uncovered lines for a specific class
mvn -B -q clover:uncovered -Dclass=ClassName

# Get test suggestions
mvn -B -q clover:suggest -Dclass=ClassName -Dmax=5

# Verify a test hit its target
mvn -B -q clover:test-feedback -Dtest=TestClass.testMethod

# Full agent report
mvn -B -q clover:agent-report
```
