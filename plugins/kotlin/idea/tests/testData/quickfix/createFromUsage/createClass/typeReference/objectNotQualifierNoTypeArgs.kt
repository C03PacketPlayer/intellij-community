// "Create object 'A'" "false"
// ACTION: Convert to block body
// ACTION: Create annotation 'A'
// ACTION: Create class 'A'
// ACTION: Create enum 'A'
// ACTION: Create interface 'A'
// ACTION: Create type parameter 'A' in function 'foo'
// ACTION: Do not show return expression hints
// ACTION: Remove explicit type specification
// ERROR: Unresolved reference: A
package p

internal fun foo(): <caret>A = throw Throwable("")