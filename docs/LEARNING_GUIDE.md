# CodeTrace — How the application works

This guide is intended to help you understand the project well enough to explain it honestly in a software engineering interview.

## 1. Execution pipeline

`Engine.trace(source)` converts source text into tokens, parses them into a syntax tree, evaluates the statements, and records a list of immutable trace steps.

- **Lexer** walks characters and recognizes identifiers, numeric literals, punctuation, operators, and `//` comments; it tracks source line numbers.
- **Parser** uses recursive descent. Each method handles one precedence level: `or → and → equality → comparison → addition → multiplication → unary → primary`.
- **AST** represents expressions and statements as small Java records that know how to evaluate or execute themselves.
- **Runtime** maintains a call stack. Each frame has a stack of local variable scopes backed by `LinkedHashMap<String,Integer>`.
- **Trace** records line, action, call stack, output-so-far, and a copied variable snapshot.

## 2. Where your Java knowledge applies

- **OOP / interfaces:** `Expression` and `Statement` represent separate behaviors.
- **ArrayList:** tokens, arguments, statements, and trace steps.
- **HashMap/LinkedHashMap:** variable bindings and function lookup.
- **Stacks:** `ArrayDeque` models nested scopes and function invocations.
- **Recursion:** the parser recurses for nested expressions; the runtime handles recursive user functions.
- **Exception handling:** helpful syntax/runtime errors and `ReturnSignal` for unwinding a function call.

## 3. Follow one program

```java
int x = 1;
while (x < 3) {
    x = x + 1;
}
print(x);
```

The runtime records the declaration (`x=1`), loop condition true, assignment (`x=2`), another true condition, assignment (`x=3`), false condition, and print (`3`). Try it and count the steps.

## 4. Why snapshots matter

A `Step` stores a new, unmodifiable map. If you kept a reference to the live variables map, every historical step might accidentally show the **latest** values instead of those present at the time of execution.

## 5. Limitations you should disclose

CodeTrace implements a teaching-oriented Java-like DSL. It **cannot parse or debug arbitrary Java programs** and doesn't implement strings, arrays, classes, exceptions in the interpreted language, full Java typing, or the Java standard library. Its recursion and step limits intentionally protect against runaway examples. Tests verify the supported language—not full Java compatibility.

## 6. Practice tasks

1. **Easy:** Add an example to calculate the sum from 1 to 10; predict the final trace step before running it.
2. **Intermediate:** Change `Step` to record the number of variables in scope, and add a regression assertion.
3. **Intermediate:** Add a `--summary` command-line flag that prints only final variables and output.
4. **Advanced:** Implement a `for` statement or arrays with indexed reads/writes; document new syntax and tests.

## 7. Interview prompts and accurate answers

**What problem does CodeTrace solve?** It makes fundamental program execution observable through step snapshots, useful for understanding loops, variable scoping, and recursion.

**Why use recursive descent?** It makes operator precedence explicit and is manageable for a small teaching language without parser-generator dependencies.

**How are variables scoped?** Each function call has a frame. Braced blocks push maps onto a stack; lookups search innermost first, and leaving a block pops that scope.

**How do you prevent infinite loops?** Every trace-recording operation counts toward a maximum number of steps; recursive function calls also have a depth limit.

**What would you improve?** Add support for arrays or `for` loops, split the interpreter into more source files, and add a machine-readable trace export.

**How much did you personally implement?** Answer based on your own work. If you began from an AI-assisted codebase, acknowledge that and explain which parts you studied, tested, modified, or extended.
