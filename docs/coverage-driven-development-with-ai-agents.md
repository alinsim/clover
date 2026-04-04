# Coverage-Driven Development with AI Agents

## Why This Exists

Every coverage tool can generate a report. You run tests, you get a number, you look at colored lines in a browser. That workflow was designed for humans reading HTML.

AI agents don't read HTML. They don't browse. They operate in a loop — write code, run tests, check results, decide what to do next — and they need that loop to be fast, structured, and queryable. They need to ask "what's uncovered in this specific class?" and get a JSON answer they can parse. They need to ask "which tests cover line 47?" and get a list they can reason about. They need to ask "am I making progress or spinning?" and get a signal.

No coverage tool does this. JaCoCo gives you XML after the build finishes. Cobertura gives you a static report. They're measurement instruments with no feedback channel — one-way glass.

**This is why Clover matters in 2026. Not because it's another coverage tool — but because it's the only one that can talk to the agent writing the tests.**

Clover's source-level instrumentation, per-test coverage tracking, and queryable database make it possible to close the loop: the agent writes a test, Clover measures exactly what it covers (and what it doesn't), the agent reads that measurement and writes the next test. No human in the loop. No report generation step. No parsing XML. Just a direct conversation between the agent and the code's coverage state.

That's the north star for this project. Everything below — the MCP server, the Maven goals, the JSON schema, the impact analysis — exists to make that conversation as rich and low-friction as possible.

---

## The Core Loop

Traditional TDD: human writes test (red) → human writes code (green) → human refactors.

Coverage-driven development with an agent: agent queries what's uncovered → agent writes a test targeting the gap → Clover measures the result → agent queries again. The coverage database is the agent's eyes into the codebase.

The human's role shifts from writing tests to reviewing them. The agent handles the mechanical work of identifying gaps and generating test code. Clover provides the ground truth that keeps the agent honest — no hallucinated coverage, no guessing what's tested.

---

## Setup

### 1. Add OpenClover to your Maven project

```xml
<plugin>
    <groupId>org.openclover</groupId>
    <artifactId>clover-maven-plugin</artifactId>
    <version>5.0.0-SNAPSHOT</version>
    <configuration>
        <generateHtml>true</generateHtml>
        <generateJson>false</generateJson>
    </configuration>
</plugin>
```

### 2. Run the instrumentation + test + report cycle

```bash
# Instrument, compile, test, generate coverage database + HTML report
mvn clean clover:setup test clover:aggregate clover:clover

# Generate the agent feedback JSON (run after tests)
mvn clover:agent-feedback -Dclover.agent=true
```

After this, you have:
- `target/clover/clover.db` — the coverage database (binary)
- `target/clover/agent-feedback.json` — machine-readable summary for agents
- `target/site/clover/` — HTML report (for human review)

The full agent workflow in one line:
```bash
mvn clean clover:setup test clover:aggregate clover:clover clover:agent-feedback -Dclover.agent=true
```

### 3. (Optional) Start the MCP server for Claude Code

```bash
java -cp target/clover/clover-all.jar \
  org.openclover.core.reporters.agent.CloverMcpServer \
  target/clover/clover.db
```

Add to your `.claude/settings.json`:
```json
{
  "mcpServers": {
    "clover": {
      "command": "java",
      "args": ["-cp", "target/clover/clover-all.jar",
               "org.openclover.core.reporters.agent.CloverMcpServer",
               "target/clover/clover.db"]
    }
  }
}
```

Now Claude Code can call `clover_uncovered`, `clover_suggest`, etc. directly.

### 4. Give the agent the reference prompt

Copy [docs/agent-clover-prompt.md](agent-clover-prompt.md) into your project's `CLAUDE.md` or system prompt. This tells the agent how to interpret coverage data, what the numbers mean, and what to do with them. Without this context, the agent will misread the JSON — for example, it won't know that the summary includes test sources (which inflates coverage percentages).

---

## The Agent Workflow

### Step 1: Assess the Current State

The agent's first action after a test run should be to understand overall coverage:

```bash
mvn -B -q clover:agent-report
cat target/clover/agent-report.json
```

This returns:
```json
{
  "format": "clover-agent-v1",
  "data": {
    "summary": {
      "statements": { "covered": 1459, "total": 2317, "pct": 62.9 },
      "branches":   { "covered": 145,  "total": 312,  "pct": 46.5 },
      "methods":    { "covered": 291,  "total": 591,  "pct": 49.2 }
    },
    "tests": [ ... ],
    "topUncovered": [ ... ]
  }
}
```

The agent now knows: 62.9% statement coverage, 46.5% branch coverage. The `topUncovered` array tells it which files have the most gaps.

### Step 2: Pick a Target

The agent picks a class to improve. It can use its own judgment (from the `topUncovered` list) or the human can direct it:

```bash
mvn -B -q clover:suggest -Dclass=OrderService
```

Response:
```json
{
  "data": {
    "file": "com/example/OrderService.java",
    "suggestions": [
      {
        "rank": 1,
        "method": "processOrder",
        "uncoveredLines": [45, 46, 47],
        "uncoveredBranches": [{ "line": 45, "type": "false" }],
        "complexity": 8,
        "coveredPct": 62.5
      }
    ]
  }
}
```

The agent now knows: `processOrder` has 3 uncovered lines and an uncovered false branch at line 45 with complexity 8. It should write a test that exercises the false branch.

### Step 3: Get Detailed Uncovered Info

For the specific file, the agent queries detailed coverage:

```bash
mvn -B -q clover:uncovered -Dclass=OrderService
```

Response includes:
- Exact uncovered line numbers
- Uncovered branch types (true/false) with method context
- Which existing tests cover nearby lines (`coveredBy` mapping)

The `coveredBy` field is crucial — it tells the agent which test CLASS already tests this area, so the new test goes in the right place.

### Step 4: Write the Test

The agent writes a test targeting the gap. It uses the uncovered lines, branch types, and `coveredBy` context to write a focused test. It knows the method name, the branch condition, and which test class to put it in.

### Step 5: Run and Measure

```bash
mvn test -Dtest=OrderServiceTest
mvn clover:aggregate
mvn -B -q clover:test-feedback -Dtest=OrderServiceTest.testProcessOrderWhenNotPending
```

Response:
```json
{
  "data": {
    "test": "OrderServiceTest.testProcessOrderWhenNotPending",
    "status": "pass",
    "coverage": {
      "OrderService.java": {
        "linesCovered": [45, 46, 47],
        "branchesCovered": [{ "line": 45, "type": "false" }]
      }
    },
    "uniqueCoverage": {
      "OrderService.java": [45, 46, 47]
    }
  }
}
```

The agent verifies: all 3 target lines now covered, the false branch at line 45 is hit, and all 3 lines are unique coverage (no other test covers them). The test added real value.

### Step 6: Repeat

The agent queries `clover:suggest` again for the next target. The loop continues until coverage reaches the desired level or the agent detects a plateau.

---

## Plateau Detection

The agent should track coverage velocity — how much coverage increases per test run. When the velocity drops near zero, writing more tests in the same area won't help. Time to:

1. Move to a different class
2. Try different test strategies (integration tests, edge cases, error paths)
3. Ask the human for guidance on untestable code

The `CoverageVelocityTracker` provides this signal. After each run, record the coverage percentage. When `isPlateaued()` returns true, switch strategies.

---

## Impact Analysis

When the agent modifies production code (refactoring, bug fixes), it should check which tests are affected:

```bash
# Which tests cover lines 45-50 of OrderService?
# (via MCP tool or programmatic API)
clover_impact --class=OrderService --lines=45,46,47,48,49,50
```

Response: list of test names that cover those lines. The agent should re-run exactly those tests to verify the change doesn't break anything.

---

## Staleness Detection

Before querying coverage, the agent should check if the data is fresh:

The JSON envelope includes a `stale` field. If `true`, the source has been modified since the last test run — the coverage data is outdated. The agent should re-run tests before making decisions based on stale data.

---

## Tool Reference

### Maven Goals

| Goal | Purpose | Key Parameters |
|------|---------|----------------|
| `clover:agent-report` | Full JSON report (summary + tests + uncovered) | `clover.agent.maxTests`, `clover.agent.maxFiles` |
| `clover:uncovered` | Per-file uncovered lines/branches | `-Dclass=ClassName` |
| `clover:test-feedback` | Per-test coverage and unique contribution | `-Dtest=TestName` |
| `clover:suggest` | Method ranking by uncovered lines | `-Dclass=ClassName`, `-Dmax=N` |
| `clover:agent-feedback` | Auto-generate feedback JSON after tests | `-Dclover.agent=true` |

All goals write JSON to `target/clover/` and (except `agent-report` and `agent-feedback`) also print to stdout for pipe-friendly usage.

### MCP Tools

| Tool | Description | Parameters |
|------|-------------|------------|
| `clover_uncovered` | Uncovered lines/branches for a class | `class` |
| `clover_test_coverage` | Coverage data for a specific test | `test` |
| `clover_suggest` | Test suggestions ranked by uncovered lines | `class` |
| `clover_summary` | Project coverage summary | (none) |
| `clover_impact` | Tests affected by changed lines | `class`, `lines` |
| `clover_risk` | Methods ranked by complexity-weighted risk | (none) |

### JSON Schema

All responses follow the `clover-agent-v1` envelope:
```json
{
  "format": "clover-agent-v1",
  "scope": "full-suite|single-test",
  "stale": false,
  "dbTimestamp": "ISO-8601",
  "dbPath": "path/to/clover.db",
  "error": null,
  "data": { ... }
}
```

When `error` is non-null, `data` is null:
```json
{
  "error": {
    "code": "class_not_found|test_not_found|db_missing",
    "message": "Human-readable description"
  }
}
```

---

## Patterns

### Pattern 1: Greenfield TDD

Starting from zero coverage. The agent generates tests for the entire codebase, prioritized by complexity risk:

1. Query `clover_risk` to find high-risk methods (high complexity, zero coverage)
2. For each method, query `clover_uncovered` to get exact lines
3. Write a test, run it, check `clover_test_feedback` to verify it hit the target
4. Repeat until the riskiest methods are covered

### Pattern 2: Gap Filling

Project has existing tests but incomplete coverage. The agent fills specific gaps:

1. Query `clover:suggest -Dclass=X` for each class the human wants improved
2. Write targeted tests for the suggested methods
3. Use `coveredBy` from `clover:uncovered` to put tests in the right test class
4. Track velocity — stop when gains plateau

### Pattern 3: Refactoring Safety Net

Before a refactoring, ensure the affected code has sufficient coverage:

1. Identify files that will change
2. For each file, query `clover:uncovered` — if uncovered lines exist in the refactoring zone, write tests first
3. After writing tests, query `clover_impact` to get the test list for the changed area
4. Refactor
5. Re-run the specific affected tests

### Pattern 4: Post-Merge Verification

After merging a feature branch, verify coverage didn't regress:

1. Run full test suite with Clover
2. Query `clover:agent-report`
3. Compare statement/branch/method percentages against baseline
4. If regression detected, query `clover:uncovered` for the newly-added files

### Pattern 5: Continuous Agent Loop

For maximum autonomy, the agent runs in a loop:

```
while coverage < target:
    run tests with Clover
    check velocity
    if plateaued: switch to next class
    query suggest for current class
    write test for top suggestion
    commit
```

The human reviews commits periodically. The agent never deploys — it only writes tests.

---

## What Clover Measures

| Metric | What It Counts | Why It Matters |
|--------|---------------|----------------|
| Statement coverage | Executable lines (assignments, calls, returns) | Basic "was this code reached?" |
| Branch coverage | True/false outcomes of if/while/for | "Were both paths tested?" |
| Method coverage | Methods that were entered at least once | "Was this method called at all?" |
| Per-test coverage | Which lines each individual test covers | "What does this test actually test?" |
| Unique coverage | Lines covered by exactly one test | "Is this test essential or redundant?" |

Branch coverage is the most valuable metric for agents. Statement coverage can be inflated by incidental execution (a method is called but only the happy path runs). Branch coverage proves both sides of every decision point were tested.

---

## Limitations

1. **Clover instruments source, not bytecode.** Lombok-generated code, annotation processors, and framework-generated classes are invisible to Clover unless hybrid instrumentation is enabled.

2. **Per-test coverage requires runtime recording.** The `globalSliceStart`/`globalSliceEnd` wrapper must be active. If tests run without Clover instrumentation, per-test data is unavailable.

3. **Coverage is not correctness.** A line being "covered" means it executed during a test, not that the test verified its behavior. The agent should write assertions, not just exercise code paths.

4. **The agent can't see what's untestable.** Some code (framework callbacks, platform-specific paths, external integrations) may be impossible to unit-test. The human should mark these with `// CLOVER:OFF` directives and tell the agent to skip them.

---

## Build Commands

```bash
# Full cycle: instrument → test → report → agent feedback
mvn clean clover:setup test clover:aggregate clover:clover clover:agent-feedback -Dclover.agent=true

# Quick: just query existing coverage (no recompile)
mvn clover:uncovered -Dclass=OrderService

# Install OpenClover core (needed after source changes)
make core

# Build everything including IntelliJ plugin
make build
```

---

## The Bigger Picture

The test suite is the most undervalued asset in a codebase. It's the only artifact that proves the code works. But writing tests is tedious, maintaining them is thankless, and most teams do the minimum to hit a coverage target they don't believe in.

AI agents change the economics. An agent can write 50 tests in the time a human writes 5. It doesn't get bored. It doesn't skip edge cases because it's Friday afternoon. But it needs something a human doesn't: a machine-readable feedback signal that tells it exactly where to aim and whether it hit the target.

That's what Clover provides. Not a report for a human to skim — a real-time, queryable, per-test, per-line, per-branch conversation with the codebase's coverage state. The agent asks questions, Clover answers with data, the agent acts on that data. The loop closes.

The end state isn't 100% coverage. It's a codebase where every critical path has a test, every test has a purpose, and an agent can tell you both of those things in under a second.
