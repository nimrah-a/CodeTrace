package com.codetrace;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Interactive, dependency-free terminal interface for execution traces. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--help")) { help(); return; }
        String file = args.length == 0 ? "examples/factorial.ct" : args[0];
        boolean all = Arrays.asList(args).contains("--all");
        try {
            String source = Files.readString(Path.of(file));
            Engine.Result result = Engine.trace(source);
            System.out.println("\nCodeTrace | " + file + " | " + result.steps().size() + " steps\n");
            String[] lines = source.split("\n", -1);
            Scanner input = new Scanner(System.in);
            for (int i = 0; i < result.steps().size(); i++) {
                Engine.Step step = result.steps().get(i);
                System.out.printf("[%03d] line %-3d | %-28s | stack: %s%n", step.number(), step.line(), step.action(), step.callStack());
                if (step.line() >= 1 && step.line() <= lines.length) System.out.println("      > " + lines[step.line() - 1].strip());
                System.out.println("      variables: " + step.variables());
                if (!all && i < result.steps().size() - 1) {
                    System.out.print("      Enter = next | a = all | q = quit > ");
                    String response = input.nextLine().strip().toLowerCase(Locale.ROOT);
                    if (response.equals("q")) return;
                    if (response.equals("a")) all = true;
                }
            }
            System.out.println("\nFinal main variables: " + result.finalVariables());
            System.out.println("Program output:\n" + (result.output().isEmpty() ? "(none)" : result.output()));
        } catch (IOException e) {
            System.err.println("Cannot read '" + file + "': " + e.getMessage());
            System.err.println("Run from the CodeTrace project root or pass a source file path.");
            System.exit(1);
        } catch (Engine.TraceException e) {
            System.err.println("Trace error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void help() {
        System.out.println("Usage: java -cp out com.codetrace.Main [path/to/program.ct] [--all]");
        System.out.println("No path: examples/factorial.ct; --all: print trace without pausing.");
    }
}
