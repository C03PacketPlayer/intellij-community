// "Create local variable 'abc'" "true"
// ACTION: Add '@JvmOverloads' annotation to function 'testMethod'
// ACTION: Create local variable 'abc'
// ACTION: Create parameter 'abc'
// ACTION: Do not show return expression hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Rename reference
// WITH_STDLIB

class Test {
    fun outer() {
        fun testMethod(x:Int = <caret>abc) {

        }
    }
}