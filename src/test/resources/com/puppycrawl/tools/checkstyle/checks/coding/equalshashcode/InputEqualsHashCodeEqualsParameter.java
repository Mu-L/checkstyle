/*
EqualsHashCode


*/

package com.puppycrawl.tools.checkstyle.checks.coding.equalshashcode;

public class InputEqualsHashCodeEqualsParameter {
    public static class TestClass1 {
        public boolean equals(String o) {
            return true;
        }
    }
    public static class TestClass2 {
        public int hashCode() { // violation 'without .* of 'equals()'.'
            return 1;
        }
        public boolean equals(String o) {
            return true;
        }
    }
    public static class TestClass3 {
        public boolean equals(Object o) { // violation 'without .* of 'hashCode()'.'
            return true;
        }
        public boolean equals(String o) {
            return false;
        }
    }
    public static class TestClass4 {
        public int hashCode() {
            return 1;
        }
        public boolean equals(Object o) {
            return true;
        }
        public boolean equals(String o) {
            return false;
        }
    }
    public static class TestClass5 {
        public int hashCode() {
            return 1;
        }
        public boolean equals(java.lang.Object o) {
            return true;
        }
    }
    public static class TestClass6 {
        public static int hashCode(int i) {
            return 1;
        }
        public boolean equals(Object o) { // violation 'without .* of 'hashCode()'.'
            return true;
        }
    }
    public static class TestClass7 {
        public int hashCode() { // violation 'without .* of 'equals()'.'
            return 1;
        }
        public static boolean equals(Object o, Object o2) {
            return true;
        }
    }
    public static class TestClass8 {
        public native int hashCode();
        public native boolean equals(Object o);
    }
    public static class TestClass9 {
        public native int hashCode(); // violation 'without .* of 'equals()'.'
    }
    public static class TestClass10 {
        public native boolean equals(Object o); // violation 'without .* of 'hashCode()'.'
    }
    public static abstract class TestClass11 {
        public abstract int hashCode();
        public abstract boolean equals(Object o);
    }
    public static abstract class TestClass12 {
        public int hashCode() { // violation 'without .* of 'equals()'.'
            return 1;
        }
        public abstract boolean equals(Object o);
    }
    public static abstract class TestClass13 {
        public abstract int hashCode();
        public boolean equals(java.lang.Object o) { // violation 'without .* of 'hashCode()'.'
            return true;
        }
    }
    public interface TestClass14 {
        public int hashCode();
        public boolean equals(Object o);
    }
    public interface TestClass15 {
        public boolean equals(Object o);
    }
    public interface TestClass16 {
        public int hashCode();
    }
    public class TestClass17 {
        public int hashCode() { // violation 'without .* of 'equals()'.'
            return 1;
        }
        public int hashCode(int val) {
            return 1;
        }
    }
}
