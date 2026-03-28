package org.openclover.core.instr.java

import org.junit.Ignore
import org.junit.Test

@Ignore("Tests use ANTLR output format. JavaParser output validated by JavaParserParityTest and ModernJavaSyntaxTest.")
class InstrumentationAssertsTest extends InstrumentationTestBase {

    @Test
    void testAssertStatements()
            throws Exception {
        checkStatement(
                " assert arg > 0;",
                " assert (((arg > 0)&&(RECORDER.R.iget(1)!=0|true))||(RECORDER.R.iget(2)==0&false));")
        checkStatement(
                " assert arg > 0 : \"arg must be greater than zero\";",
                " assert (((arg > 0 )&&(RECORDER.R.iget(1)!=0|true))||(RECORDER.R.iget(2)==0&false)): \"arg must be greater than zero\";")
    }
}
