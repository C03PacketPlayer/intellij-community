// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
interface <caret>X {

}

open class A : X {

}

interface Y : X {

}

// FIR_COMPARISON