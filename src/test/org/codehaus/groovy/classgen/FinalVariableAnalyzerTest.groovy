/*
 * Copyright 2003-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.groovy.classgen

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.Variable
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.builder.CompilerCustomizationBuilder

class FinalVariableAnalyzerTest extends GroovyTestCase {

    protected void assertFinals(final Map<String,Boolean> expectations, final String script) throws Exception {
        def cc = new CompilerConfiguration()
        CompilerCustomizationBuilder.withConfig(cc) {
            inline(phase: 'SEMANTIC_ANALYSIS') { source, context, classNode ->
                def analyzer = new AssertionFinalVariableAnalyzer(source, expectations)
                analyzer.visitClass(classNode)
            }
        }
        def shell = new GroovyShell(cc)
        shell.parse(script)
    }

    protected void assertFinalCompilationErrors(List<String> vars, final String script, boolean unInitialized=false) {
        Set<String> checked = []
        try {
            assertFinals [:], script
        } catch (MultipleCompilationErrorsException e) {
            vars.each { var ->
                if (!unInitialized) {
                    assert (e.message =~ "The variable \\[${var}\\] is declared final but is reassigned" ||
                            e.message =~ "Cannot assign a value to final variable '${var}'")
                } else {
                    assert e.message =~ "The variable \\[${var}\\] may be uninitialized"
                }
                checked << var
            }
        }
        assert (vars - checked).empty
    }

    void testVariableShouldBeEffectivelyFinal() {
        assertFinals x:true, 'def x = 1'
    }

    void testVariableDeclaredAsFinal() {
        assertFinals x: true, '''
            final x = 1
        '''
    }

    void testReassignedVarShouldNotBeFinal() {
        assertFinals x: false, '''
            def x = 1
            x = 2
        '''
    }

    void testUnassignedVarShouldBeConsideredFinal() {
        assertFinals x:true, '''def x'''
    }

    void testVariableReassignedInClosureShouldNotBeFinal() {
        assertFinals x:false, '''
            def x
            cl = { x=1 }
            cl()
        '''
    }

    void testVariableNotReassignedInClosureShouldBeFinal() {
        assertFinals x:true, '''
            def x = 1
            cl = { x }
            cl()
        '''
    }

    void testVariableInitializedInTwoStepsShouldBeFinal() {
        assertFinals x:true, '''
            def x
            x=1
        '''
    }

    void testVariableDeclaredInsideClosureShouldBeFinal() {
        assertFinals x:true, '''
            def cl = { def x = 1 }
        '''
    }

    void testParameterShouldBeConsideredFinal() {
        assertFinals x:true, '''
            def foo(int x) { x+1 }
       '''
    }

    void testParameterShouldNotBeConsideredFinal() {
        assertFinals x:false, '''
            def foo(int x) { x = x+1 }
       '''
    }

    void testReassignedFinalVariableShouldThrowCompilationError() {
        assertFinalCompilationErrors(['x'], '''
            final x = []
            x += [1]
        ''')
    }

    void testReassignedFinalParamererShouldThrowCompilationError() {
        assertFinalCompilationErrors(['x'], '''
            void foo(final int x) {
               x = 2
            }
        ''')
    }

    void testFinalVariableAssignedInIfBranchesShouldStillBeFinal() {
        assertFinals x:true,'''
            int x
            if (t) {
                x = 1
            }
        '''
    }

    void testFinalVariableAssignedInElseBranchesShouldStillBeFinal() {
        assertFinals x:true,'''
            int x
            if (t) {
                // nothing
            } else {
                x = 1
            }
        '''
    }

    void testFinalVariableAssignedInIfElseBranchesShouldStillBeFinal() {
        assertFinals x:true,'''
            int x
            if (t) {
                x = 1
            } else {
                x = 2
            }
        '''
    }

    void testFinalVariableAssignedInIfElseBranchesShouldNotBeFinal() {
        assertFinals x:false,'''
            int x
            if (t) {
                x = 1
                x = 2
            } else {
                x = 2
            }
        '''
    }

    void testFinalVariableAssignedInIfElseBranchesShouldNotBeFinal2() {
        assertFinals x:false,'''
            int x
            if (t) {
                x = 1
            } else {
                x = 1
                x = 2
            }
        '''
    }

    void testNestedIfShouldNotBeFinal() {
        assertFinals x:false,'''
            int x
            if (t1) {
                if (t2) {
                    x = 1
                }
            }
            if (t2) {
                if (t3) {
                    x = 1
                }
            }
        '''
    }

    void testPrePostfixShouldMakeVarNotFinal() {
        assertFinals x:false, y:false, z:false, o:false, '''
            def x = 0
            def y = 0
            def z = 0
            def o = 0
            x++
            ++y
            z--
            --o
        '''
    }

    void testPrePostfixShouldMakeUninitializedVarNotFinal() {
        assertFinals x:false, y:false, z:false, o:false, '''
            def x
            def y
            def z
            def o
            x++
            ++y
            z--
            --o
        '''
    }

    void testAssignmentInIfBooleanExpressionShouldFailCompilation() {
        assertFinalCompilationErrors(['a'], '''
            final a = 3

            if ((a = 4) == 4) {
              assert a == 3
            }
        ''')
    }

    void testDirectlyAssignedClosureSharedVariableShouldBeConsideredFinal() {
        assertFinals x:true, '''
            def x = 1
            def cl = { x }
            cl()
        '''
    }

    void testDelayedAssignedClosureSharedVariableShouldBeConsideredFinal() {
        assertFinals x:false, '''
            def x
            def cl = { x }
            x=1
        '''
    }

    void testShouldThrowCompilationErrorBecauseClosureSharedVariable() {
        assertFinalCompilationErrors(['x'], '''
            final x

            def cl = { x }
            x=1
        ''')
    }

    void testShouldBeCompileTimeErrorBecauseOfUninitializedVar() {
        assertFinalCompilationErrors(['x'], '''
            final x
            def y = x
            x = 1
        ''')
    }

    void testShouldConsiderThatXIsEffectivelyFinalWithIfElse() {
        assertFinals x:true, '''
            int x
            if (foo) {
              x=1
            } else {
              x=2
            }

        '''
    }

    void testShouldConsiderThatXIsNotEffectivelyFinalWithSubsequentIfs() {
        assertFinals x:false, '''
            int x
            if (foo) {
              x=1
            }
            if (!foo) {
              x=2
            }
        '''
    }

    void testShouldThrowCompileTimeErrorBecauseXIsNotInitialized() {
        assertFinalCompilationErrors(['x'], '''
            final x
        ''', true)
    }

    void testShouldThrowCompileTimeErrorBecauseXIsNotEffectivelyFinalWithSubsequentIfs() {
        assertFinalCompilationErrors(['x'], '''
            final x
            if (foo) {
              x=1
            }
            if (!foo) {
              x=2
            }
        ''', true)
    }

    @CompileStatic
    private static class AssertionFinalVariableAnalyzer extends FinalVariableAnalyzer {

        private final Set<Variable> variablesToCheck = []
        private final Map<String,Boolean> assertionsToCheck

        AssertionFinalVariableAnalyzer(final SourceUnit sourceUnit, final Map<String,Boolean> assertions) {
            super(sourceUnit)
            assertionsToCheck = assertions
        }

        @Override
        void visitVariableExpression(final VariableExpression expression) {
            super.visitVariableExpression(expression)
            if (assertionsToCheck.containsKey(expression.name)) {
                variablesToCheck << expression
                variablesToCheck << expression.accessedVariable
            }
        }

        @Override
        void visitClass(final ClassNode node) {
            super.visitClass(node)
            checkAssertions()
        }

        private void checkAssertions() {
            variablesToCheck.each { var ->
                def expectedValue = assertionsToCheck[var.name]
                assert isEffectivelyFinal(var) == expectedValue
            }
        }
    }
}
