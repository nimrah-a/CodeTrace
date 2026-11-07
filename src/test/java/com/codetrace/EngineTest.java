package com.codetrace;

import java.util.*;

/** Executable no-dependency regression suite: java -ea -cp out com.codetrace.EngineTest. */
public final class EngineTest {
    private static int assertions;
    private EngineTest() {}
    private static void equal(Object expected,Object actual) {
        assertions++;
        if(!Objects.equals(expected,actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }
    private static void yes(boolean condition) {
        assertions++;
        if(!condition) throw new AssertionError("condition failed");
    }
    private static void fails(String source,String fragment) {
        assertions++;
        try {Engine.trace(source); throw new AssertionError("expected failure: " + fragment);}
        catch(Engine.TraceException ex) {
            if(!ex.getMessage().contains(fragment)) throw new AssertionError("unexpected message: "+ex.getMessage());
        }
    }
    private static int run(String source,String name) { return Engine.trace(source).finalVariables().get(name); }
    public static void main(String[] args) {
        equal(7, run("int result=1+2*3;", "result"));
        equal(9, run("int result=(1+2)*3;", "result"));
        equal(-2, run("int result=-5+3;", "result"));
        equal(1, run("int result=2 < 3 && 4 != 5;", "result"));
        equal(1, run("int result=1 || 1/0;", "result"));
        equal(0, run("int result=0 && 1/0;", "result"));
        equal(5, run("int x=5; if(x<4){x=9;} else {x=x;} ","x"));
        equal(15, run("int x=0; int i=1; while(i<=5){x=x+i;i=i+1;}","x"));
        equal(10, run("int x=10; if(1){ int x=3; print(x); }","x"));
        equal("3\n10\n",Engine.trace("int x=10; if(1){ int x=3; print(x); } print(x);").output());
        equal("120\n",Engine.trace("fun fact(n){if(n<=1){return 1;}else{return n*fact(n-1);}} print(fact(5));").output());
        equal("13\n",Engine.trace("fun fib(n){if(n<=1){return n;}else{return fib(n-1)+fib(n-2);}} print(fib(7));").output());
        equal("4\n",Engine.trace("fun add(a,b){return a+b;} print(add(2,2));").output());
        Engine.Result trace=Engine.trace("int a=1; a=a+1; print(a);");
        equal(3,trace.steps().size());
        equal(1,trace.steps().get(0).variables().get("a"));
        equal(2,trace.steps().get(1).variables().get("a"));
        equal(1,trace.steps().get(0).variables().get("a")); // snapshots immutable
        equal("2\n",trace.output());
        equal(1,trace.steps().get(3-1).line()); // single-line program
        yes(trace.steps().get(0).number()==1);
        equal("main",trace.steps().get(0).callStack());
        Engine.Result recursive = Engine.trace("fun f(n){if(n==0){return 0;}return f(n-1);}print(f(3));");
        yes(recursive.steps().stream().anyMatch(s -> s.callStack().equals("main -> f -> f")));
        fails("int x = 1 / 0;","division by zero");
        fails("int x = 1 % 0;","remainder by zero");
        fails("int x = y;","Undefined variable");
        fails("x = 3;","Undefined variable");
        fails("int x = 3; int x = 4;","duplicate variable");
        fails("fun f(a,a){return a;} print(f(2,3));","duplicate function parameter");
        fails("fun f(a){return a;} print(f());","expects 1 arguments");
        fails("print(missing(1));","undefined function");
        fails("fun f(){int x=1;} print(f());","finished without return");
        fails("int x = 2147483648;","integer literal out of range");
        fails("int x = 1","expected ';'");
        fails("if (1) { int x=3;","expected '}'");
        fails("int x = 1; @","unsupported character");
        fails("return 2;","return must appear inside a function");
        fails("int x=0; while(1){x=x+1;}","Execution stopped after 5000 steps");
        fails("fun f(){return f();}print(f());","Call stack depth limit");
        try { Engine.trace("int x=1;",0); throw new AssertionError("expected maxSteps failure"); }
        catch(IllegalArgumentException expected) { assertions++; }
        Random random = new Random(2026);
        for(int i=0;i<200;i++) {
            int a=random.nextInt(100),b=random.nextInt(100);
            equal(a+b,run("int result="+a+"+"+b+";","result"));
            equal(a*b,run("int result="+a+"*"+b+";","result"));
            equal(a<=b?1:0,run("int result="+a+"<="+b+";","result"));
        }
        System.out.println("PASS: " + assertions + " regression assertions");
    }
}
