# CodeTrace — Java Execution Tracer
[![Java Tests](https://github.com/nimrah-a/CodeTrace/actions/workflows/java-tests.yml/badge.svg)](https://github.com/nimrah-a/CodeTrace/actions/workflows/java-tests.yml)

A **dependency-free Java 17 console application** that interprets a **documented subset of Java-like syntax** and displays every execution step. Trace variable updates, loops, conditionals, function calls, recursion, and output—without Swing, Maven, or any third-party libraries.

> **Scope:** This is an educational interpreter, **not a debugger for arbitrary Java source code**. It executes `.ct` examples using the small language described below. The runtime and parser are implemented in standard Java.

## Highlights

- Step-by-step terminal playback (Enter next / `a` all / `q` quit)
- Line numbers and currently executed statement
- Immutable snapshots of each active function's variables
- Call-stack display (`main -> factorial -> factorial`)
- Integer arithmetic and relational/boolean expressions
- `if/else`, `while`, scoped variables, custom functions, and recursion
- Short-circuit evaluation for `&&` / `||`
- Clear errors for invalid syntax, unknown variables/functions, division by zero
- Safety limits: **5,000 recorded steps** and **64 call frames**
- Self-contained regression suite and GitHub Actions CI

## Requirements

- **JDK 17 or newer** (`java -version` and `javac -version`)
- Terminal (macOS/Linux/Windows) or IntelliJ IDEA
- **No Maven, Gradle, Swing, database, or dependencies**

## Quick start

From the **project root**:

```bash
# macOS / Linux
bash run.sh examples/factorial.ct --all
bash run.sh examples/loops.ct --all
bash test.sh
```

Windows Command Prompt:

```bat
run.bat examples\factorial.ct --all
run.bat examples\loops.ct --all
test.bat
```

Omit `--all` to step interactively. Omit the path to use `examples/factorial.ct`.

### Example code

```java
// examples/factorial.ct
fun factorial(n) {
    if (n <= 1) {
        return 1;
    } else {
        return n * factorial(n - 1);
    }
}
int number = 5;
int answer = factorial(number);
print(answer);
```

Sample output excerpt:

```text
[001] line 10  | declare number = 5           | stack: main
      variables: {number=5}
[002] line 11  | call factorial[5]            | stack: main -> factorial
      variables: {n=5}
...
Final main variables: {number=5, answer=120}
Program output:
120
```

## IntelliJ IDEA setup

1. **File → Open** → choose the extracted `CodeTrace` folder.
2. Set **Project SDK** to **Java 17+** in *File → Project Structure*.
3. If IntelliJ does not recognize the source directories automatically, right-click `src/main/java` → **Mark Directory as → Sources Root**, and `src/test/java` → **Test Sources Root**.
4. Open `src/main/java/com/codetrace/Main.java`, then click **Run** next to `main`.
5. Under **Run → Edit Configurations**, set **Working directory** to the repository root so the default `examples/factorial.ct` is found.
6. For noninteractive playback, enter `examples/factorial.ct --all` in **Program arguments**.
7. Run `src/test/java/com/codetrace/EngineTest.java` for the executable test suite (enable assertions with `-ea` if necessary). Alternatively run `bash test.sh` in IntelliJ's terminal.

## Supported `.ct` language

| Construct | Example | Notes |
|---|---|---|
| Declaration | `int count = 0;` | Integer only |
| Assignment | `count = count + 1;` | Must already exist |
| Output | `print(count);` | Prints an integer |
| Conditional | `if (count > 2) { ... } else { ... }` | Braces required |
| Loop | `while (count < 10) { ... }` | No `for` loops |
| Function | `fun add(a, b) { return a + b; }` | Function uses an expression return |
| Call | `int total = add(2, 3);` | Recursive calls supported |
| Operators | `+ - * / %`, comparisons, `&& || !` | Integer `1`=true, `0`=false |
| Comments | `// hello` | Single-line comments |

Function declarations must occur at the top level. Variables are scoped to the current function and braces block. Return values and calculations use Java 32-bit `int` arithmetic (overflow wraps). Unsupported Java syntax includes arrays, strings, classes, `for` loops, `System.out.println`, and arbitrary `.java` files. `return` should only be used in function bodies. If you need production Java debugging, use IntelliJ's built-in debugger instead.

## Architecture

```text
Source .ct
   │
   ▼
Lexer (tokens + line numbers)
   │
   ▼
Recursive-descent Parser (AST statements and expressions)
   │
   ▼
Runtime (scope stack + call stack + evaluator)
   │
   ▼
Immutable Step snapshots ──▶ Console step player
```

- **`Engine.java`**: lexer, parser, AST records, expression evaluation, scoped runtime, call stack, and trace snapshots.
- **`Main.java`**: terminal playback, loading source files, error handling.
- **`EngineTest.java`**: no-dependency regression suite (arithmetic, recursion, control flow, scoping, failure cases, seeded randomized checks).
- **`examples/`**: ready-to-run programs demonstrating features.

The `Engine.trace(...)` method is also reusable from other Java code:

```java
Engine.Result result = Engine.trace("int x = 2; x = x * 3; print(x);");
for (Engine.Step step : result.steps()) {
    System.out.println(step.action() + " " + step.variables());
}
```

## Repository structure

```text
CodeTrace/
├── .github/workflows/java-ci.yml
├── examples/
│   ├── factorial.ct
│   ├── loops.ct
│   ├── conditions.ct
│   └── scopes.ct
├── src/main/java/com/codetrace/
│   ├── Engine.java
│   └── Main.java
├── src/test/java/com/codetrace/EngineTest.java
├── docs/LEARNING_GUIDE.md
├── run.sh / run.bat
├── test.sh / test.bat
├── .gitignore
├── LICENSE
└── README.md
```

## Automated testing

Run `bash test.sh` from the project root. This compiles **main and test sources with `javac --release 17`**, then runs regression tests. The GitHub Actions workflow runs the same script on pushes, pull requests, and manual dispatch, and smoke-tests the factorial example.

## Ideas for your own improvements

1. Add `for` loops by lowering them into initializer + `while` + update.
2. Implement arrays and indexed expressions.
3. Track memory usage or add a JSON trace exporter.
4. Write additional unit tests for nested scopes and recursive functions.

These are **future enhancements**, not features claimed in the current version.

## AI assistance and attribution

This repository can be used as an educational, AI-assisted starter project. If publishing as a personal portfolio project, describe your actual contributions and what you have tested or changed yourself; don't claim to have independently implemented code you haven't reviewed. Understand the parser, evaluator, and execution trace before discussing it in an interview.

## License

MIT — see [LICENSE](LICENSE).
