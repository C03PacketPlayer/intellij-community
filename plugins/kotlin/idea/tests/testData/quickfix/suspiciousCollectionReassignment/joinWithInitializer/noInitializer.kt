// "Join with initializer" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.SuspiciousCollectionReassignmentInspection
// ACTION: Do not show return expression hints
// ACTION: Replace overloaded operator with function call
// ACTION: Replace with ordinary assignment
// WITH_STDLIB
fun test(otherList: List<Int>) {
    var list: List<Int>
    list = createList()
    list <caret>+= otherList
}

fun createList(): List<Int> = listOf(1, 2, 3)
