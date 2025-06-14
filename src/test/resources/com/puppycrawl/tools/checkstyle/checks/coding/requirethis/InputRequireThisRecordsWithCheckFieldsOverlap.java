/*
RequireThis
checkFields = true
checkMethods = (default)true
validateOnlyOverlapping = false


*/

// Java17
package com.puppycrawl.tools.checkstyle.checks.coding.requirethis;

public record InputRequireThisRecordsWithCheckFieldsOverlap(String a) {
    void method() {
        String a = "BUG";
        a = a.substring(0, 1); // ok, field 'a' is final and cannot be changed
    }

    void method2() {
        String x = a; // violation 'Reference to instance variable 'a' needs "this.".'
        String y = this.a; // ok, uses 'this'
        String a = this.a; // ok, local variable assigned from field
        a += a; // ok, local variable
    }
}
class InputRequireThisRecordsWithCheckFieldsClassTwo {
    public final String a;

    public InputRequireThisRecordsWithCheckFieldsClassTwo(String a) {
        this.a = a;
    }

    void method() {
        String a = "BUG";
        a = a.substring(0, 1); // ok, field 'a' is final and cannot be changed
    }

    void method2() {
        String x = a; // violation 'Reference to instance variable 'a' needs "this.".'
        String y = this.a; // ok, uses 'this'
        String a = this.a; // ok, local variable assigned from field
        a += a; // ok, local variable
    }

    public String a() {
        return a; // violation 'Reference to instance variable 'a' needs "this.".'
    }
}
