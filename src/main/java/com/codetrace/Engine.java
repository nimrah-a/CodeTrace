package com.codetrace;

import java.util.*;

/** A deliberately small, deterministic interpreter for a documented Java-like language. */
public final class Engine {
    private Engine() {}

    public record Step(int number, int line, String action, String callStack,
                       Map<String, Integer> variables, String output) {
        public Step {
            variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        }
    }

    public record Result(List<Step> steps, String output, Map<String, Integer> finalVariables) {
        public Result {
            steps = List.copyOf(steps);
            finalVariables = Collections.unmodifiableMap(new LinkedHashMap<>(finalVariables));
        }
    }

    public static final class TraceException extends RuntimeException {
        public TraceException(String message) { super(message); }
    }

    private enum Type { ID, NUM, SYM, EOF }
    private record Token(Type type, String value, int line) {}

    private static List<Token> lex(String source) {
        List<Token> tokens = new ArrayList<>();
        int pos = 0, line = 1;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\n') { line++; pos++; continue; }
            if (Character.isWhitespace(c)) { pos++; continue; }
            if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                while (pos < source.length() && source.charAt(pos) != '\n') pos++;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = pos++;
                while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) pos++;
                tokens.add(new Token(Type.ID, source.substring(start, pos), line));
                continue;
            }
            if (Character.isDigit(c)) {
                int start = pos++;
                while (pos < source.length() && Character.isDigit(source.charAt(pos))) pos++;
                tokens.add(new Token(Type.NUM, source.substring(start, pos), line));
                continue;
            }
            if (pos + 1 < source.length()) {
                String pair = source.substring(pos, pos + 2);
                if (Set.of("==", "!=", "<=", ">=", "&&", "||").contains(pair)) {
                    tokens.add(new Token(Type.SYM, pair, line)); pos += 2; continue;
                }
            }
            if ("+-*/%=!<>(){};,".indexOf(c) >= 0) {
                tokens.add(new Token(Type.SYM, String.valueOf(c), line)); pos++; continue;
            }
            throw new TraceException("Line " + line + ": unsupported character '" + c + "'");
        }
        tokens.add(new Token(Type.EOF, "<end>", line));
        return tokens;
    }

    private interface Expression { int eval(Runtime runtime); }
    private interface Statement { void execute(Runtime runtime); }
    private record Function(List<String> parameters, List<Statement> body, int line) {}
    private record Program(Map<String, Function> functions, List<Statement> main) {}
    private record NumberExpr(int value) implements Expression {
        public int eval(Runtime runtime) { return value; }
    }
    private record VariableExpr(String name) implements Expression {
        public int eval(Runtime runtime) { return runtime.get(name); }
    }
    private record UnaryExpr(String op, Expression right) implements Expression {
        public int eval(Runtime runtime) {
            int r = right.eval(runtime);
            return switch (op) { case "-" -> -r; case "!" -> r == 0 ? 1 : 0; default -> throw new AssertionError(op); };
        }
    }
    private record BinaryExpr(Expression left, String op, Expression right, int line) implements Expression {
        public int eval(Runtime runtime) {
            int l = left.eval(runtime);
            if (op.equals("&&")) return l != 0 && right.eval(runtime) != 0 ? 1 : 0;
            if (op.equals("||")) return l != 0 || right.eval(runtime) != 0 ? 1 : 0;
            int r = right.eval(runtime);
            return switch (op) {
                case "+" -> l + r; case "-" -> l - r; case "*" -> l * r;
                case "/" -> { if (r == 0) throw new TraceException("Line " + line + ": division by zero"); yield l / r; }
                case "%" -> { if (r == 0) throw new TraceException("Line " + line + ": remainder by zero"); yield l % r; }
                case "==" -> l == r ? 1 : 0; case "!=" -> l != r ? 1 : 0;
                case "<" -> l < r ? 1 : 0; case "<=" -> l <= r ? 1 : 0;
                case ">" -> l > r ? 1 : 0; case ">=" -> l >= r ? 1 : 0;
                default -> throw new AssertionError(op);
            };
        }
    }
    private record CallExpr(String name, List<Expression> arguments, int line) implements Expression {
        public int eval(Runtime runtime) {
            List<Integer> values = new ArrayList<>();
            for (Expression expression : arguments) values.add(expression.eval(runtime));
            return runtime.call(name, values, line);
        }
    }
    private record Declare(String name, Expression expression, int line) implements Statement {
        public void execute(Runtime runtime) {
            int value = expression.eval(runtime);
            runtime.declare(name, value, line);
            runtime.record(line, "declare " + name + " = " + value);
        }
    }
    private record Assign(String name, Expression expression, int line) implements Statement {
        public void execute(Runtime runtime) {
            int value = expression.eval(runtime);
            runtime.assign(name, value);
            runtime.record(line, "assign " + name + " = " + value);
        }
    }
    private record Print(Expression expression, int line) implements Statement {
        public void execute(Runtime runtime) {
            int value = expression.eval(runtime);
            runtime.output.append(value).append('\n');
            runtime.record(line, "print " + value);
        }
    }
    private record Conditional(Expression condition, List<Statement> yes, List<Statement> no, int line) implements Statement {
        public void execute(Runtime runtime) {
            boolean outcome = condition.eval(runtime) != 0;
            runtime.record(line, "if condition is " + outcome);
            runtime.block(outcome ? yes : no);
        }
    }
    private record Loop(Expression condition, List<Statement> body, int line) implements Statement {
        public void execute(Runtime runtime) {
            while (true) {
                boolean outcome = condition.eval(runtime) != 0;
                runtime.record(line, "while condition is " + outcome);
                if (!outcome) break;
                runtime.block(body);
            }
        }
    }
    private static final class ReturnSignal extends RuntimeException {
        final int value;
        ReturnSignal(int value) { super(null, null, false, false); this.value = value; }
    }
    private record Return(Expression expression, int line) implements Statement {
        public void execute(Runtime runtime) {
            int value = expression.eval(runtime);
            runtime.record(line, "return " + value);
            throw new ReturnSignal(value);
        }
    }

    private static final class Parser {
        final List<Token> tokens;
        int index;
        Parser(List<Token> tokens) { this.tokens = tokens; }
        Token peek() { return tokens.get(index); }
        Token previous() { return tokens.get(index - 1); }
        Token next() { return tokens.get(index++); }
        boolean match(String value) { if (peek().value().equals(value)) { next(); return true; } return false; }
        Token expect(String value) {
            if (!match(value)) throw error("expected '" + value + "', found '" + peek().value() + "'");
            return previous();
        }
        String identifier() {
            if (peek().type() != Type.ID || Set.of("int", "if", "else", "while", "return", "fun", "print").contains(peek().value()))
                throw error("expected an identifier, found '" + peek().value() + "'");
            return next().value();
        }
        TraceException error(String message) { return new TraceException("Line " + peek().line() + ": " + message); }
        Program program() {
            Map<String, Function> functions = new LinkedHashMap<>();
            List<Statement> main = new ArrayList<>();
            while (peek().type() != Type.EOF) {
                if (match("fun")) {
                    int line = previous().line();
                    String name = identifier();
                    expect("(");
                    List<String> parameters = new ArrayList<>();
                    if (!peek().value().equals(")")) {
                        do { parameters.add(identifier()); } while (match(","));
                    }
                    expect(")");
                    if (new HashSet<>(parameters).size() != parameters.size()) throw error("duplicate function parameter");
                    List<Statement> body = block();
                    if (functions.putIfAbsent(name, new Function(List.copyOf(parameters), body, line)) != null)
                        throw error("duplicate function '" + name + "'");
                } else main.add(statement(false));
            }
            return new Program(functions, main);
        }
        List<Statement> block() {
            expect("{");
            List<Statement> body = new ArrayList<>();
            while (!peek().value().equals("}")) {
                if (peek().type() == Type.EOF) throw error("expected '}' before end of file");
                body.add(statement(true));
            }
            expect("}");
            return List.copyOf(body);
        }
        Statement statement(boolean inBlock) {
            int line = peek().line();
            if (match("int")) {
                String name = identifier(); expect("="); Expression expr = expression(); expect(";");
                return new Declare(name, expr, line);
            }
            if (match("print")) {
                expect("("); Expression expr = expression(); expect(")"); expect(";");
                return new Print(expr, line);
            }
            if (match("if")) {
                expect("("); Expression cond = expression(); expect(")");
                List<Statement> yes = block();
                List<Statement> no = match("else") ? block() : List.of();
                return new Conditional(cond, yes, no, line);
            }
            if (match("while")) {
                expect("("); Expression cond = expression(); expect(")");
                return new Loop(cond, block(), line);
            }
            if (match("return")) {
                if (!inBlock) throw error("return must appear inside a function body");
                Expression expr = expression(); expect(";");
                return new Return(expr, line);
            }
            String name = identifier(); expect("="); Expression expr = expression(); expect(";");
            return new Assign(name, expr, line);
        }
        Expression expression() { return or(); }
        Expression or() { Expression e = and(); while (match("||")) { String op = previous().value(); int line = previous().line(); e = new BinaryExpr(e,op,and(),line); } return e; }
        Expression and() { Expression e = equality(); while (match("&&")) { String op = previous().value(); int line = previous().line(); e = new BinaryExpr(e,op,equality(),line); } return e; }
        Expression equality() { Expression e = compare(); while (peek().value().equals("==") || peek().value().equals("!=")) { String op = next().value(); int line=previous().line(); e=new BinaryExpr(e,op,compare(),line); } return e; }
        Expression compare() { Expression e = add(); while (Set.of("<", "<=", ">", ">=").contains(peek().value())) { String op=next().value(); int line=previous().line(); e=new BinaryExpr(e,op,add(),line); } return e; }
        Expression add() { Expression e= multiply(); while (peek().value().equals("+") || peek().value().equals("-")) { String op=next().value(); int line=previous().line(); e=new BinaryExpr(e,op,multiply(),line); } return e; }
        Expression multiply() { Expression e=unary(); while (Set.of("*", "/", "%").contains(peek().value())) { String op=next().value(); int line=previous().line(); e=new BinaryExpr(e,op,unary(),line); } return e; }
        Expression unary() { if (match("-") || match("!")) return new UnaryExpr(previous().value(), unary()); return primary(); }
        Expression primary() {
            if (match("(")) { Expression e=expression(); expect(")"); return e; }
            if (peek().type()==Type.NUM) {
                Token t=next();
                try { return new NumberExpr(Integer.parseInt(t.value())); }
                catch (NumberFormatException ex) { throw new TraceException("Line " + t.line() + ": integer literal out of range"); }
            }
            int line = peek().line(); String name=identifier();
            if (match("(")) {
                List<Expression> args = new ArrayList<>();
                if (!peek().value().equals(")")) { do { args.add(expression()); } while(match(",")); }
                expect(")"); return new CallExpr(name,List.copyOf(args),line);
            }
            return new VariableExpr(name);
        }
    }

    private static final class Frame {
        final String name;
        final Deque<Map<String,Integer>> scopes = new ArrayDeque<>();
        Frame(String name) { this.name=name; scopes.push(new LinkedHashMap<>()); }
        Map<String,Integer> snapshot() {
            Map<String,Integer> result = new LinkedHashMap<>();
            for (Iterator<Map<String,Integer>> it=scopes.descendingIterator();it.hasNext();) result.putAll(it.next());
            return result;
        }
    }

    private static final class Runtime {
        final Program program;
        final int maxSteps;
        final List<Step> steps=new ArrayList<>();
        final StringBuilder output=new StringBuilder();
        final Deque<Frame> stack=new ArrayDeque<>();
        Runtime(Program program,int maxSteps) { this.program=program; this.maxSteps=maxSteps; stack.push(new Frame("main")); }
        Frame current() { return stack.peek(); }
        void record(int line,String action) {
            if (steps.size()>=maxSteps) throw new TraceException("Execution stopped after " + maxSteps + " steps (possible infinite loop)");
            StringJoiner names=new StringJoiner(" -> ");
            for (Iterator<Frame> it=stack.descendingIterator();it.hasNext();) names.add(it.next().name);
            steps.add(new Step(steps.size()+1,line,action,names.toString(),current().snapshot(),output.toString()));
        }
        void declare(String name,int value,int line) {
            if (current().scopes.peek().containsKey(name)) throw new TraceException("Line " + line + ": duplicate variable '"+name+"'");
            current().scopes.peek().put(name,value);
        }
        int get(String name) {
            for (Map<String,Integer> scope:current().scopes) if(scope.containsKey(name)) return scope.get(name);
            throw new TraceException("Undefined variable '"+name+"' in " + current().name);
        }
        void assign(String name,int value) {
            for (Map<String,Integer> scope:current().scopes) if(scope.containsKey(name)) {scope.put(name,value);return;}
            throw new TraceException("Undefined variable '"+name+"' in " + current().name);
        }
        void block(List<Statement> body) {
            current().scopes.push(new LinkedHashMap<>());
            try { for (Statement stmt:body) stmt.execute(this); }
            finally { current().scopes.pop(); }
        }
        int call(String name,List<Integer> arguments,int line) {
            Function function=program.functions.get(name);
            if(function==null) throw new TraceException("Line " + line + ": undefined function '"+name+"'");
            if(function.parameters.size()!=arguments.size()) throw new TraceException("Line " + line + ": function '"+name+"' expects " + function.parameters.size() + " arguments");
            if(stack.size()>=64) throw new TraceException("Call stack depth limit (64) reached");
            Frame frame=new Frame(name);
            for(int i=0;i<arguments.size();i++) frame.scopes.peek().put(function.parameters.get(i),arguments.get(i));
            stack.push(frame);
            try {
                record(line,"call " + name + arguments);
                for(Statement statement:function.body) statement.execute(this);
                throw new TraceException("Function '"+name+"' finished without return");
            } catch(ReturnSignal signal) {return signal.value;}
            finally {stack.pop();}
        }
        Result run() {
            for(Statement statement:program.main) statement.execute(this);
            return new Result(steps,output.toString(),current().snapshot());
        }
    }

    /** Executes source and creates immutable snapshots after every meaningful operation. */
    public static Result trace(String source) { return trace(source,5000); }
    public static Result trace(String source,int maxSteps) {
        Objects.requireNonNull(source,"source");
        if(maxSteps<1) throw new IllegalArgumentException("maxSteps must be >= 1");
        return new Runtime(new Parser(lex(source)).program(),maxSteps).run();
    }
}
