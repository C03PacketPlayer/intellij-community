// "Replace with 'New<T, U>'" "false"
// ACTION: Compiler warning 'TYPEALIAS_EXPANSION_DEPRECATION' options
// ACTION: Convert to block body
// ACTION: Do not show return expression hints
// ACTION: Introduce import alias
// ACTION: Remove explicit type specification

@Deprecated("Use New", replaceWith = ReplaceWith("New<T, U>"))
class Old<T, U>

@Deprecated("Use New1", replaceWith = ReplaceWith("New1"))
class Old1

@Deprecated("Use New2", replaceWith = ReplaceWith("New2"))
class Old2

typealias OOO = Old<Old1, Old2>

class New<T, U>
class New1
class New2

fun foo(): <caret>OOO? = null