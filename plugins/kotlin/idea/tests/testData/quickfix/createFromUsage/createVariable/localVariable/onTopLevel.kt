// "Create local variable 'foo'" "false"
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Create property 'foo'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

val t: Int = <caret>foo