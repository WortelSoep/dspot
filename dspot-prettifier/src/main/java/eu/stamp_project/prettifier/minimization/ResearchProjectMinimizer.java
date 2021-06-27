package eu.stamp_project.prettifier.minimization;

import eu.stamp_project.dspot.common.automaticbuilder.AutomaticBuilder;
import eu.stamp_project.dspot.common.compilation.DSpotCompiler;
import eu.stamp_project.dspot.common.miscellaneous.AmplificationHelper;
import eu.stamp_project.dspot.common.miscellaneous.DSpotUtils;
import eu.stamp_project.dspot.common.test_framework.TestFramework;
import eu.stamp_project.dspot.selector.pitmutantscoreselector.AbstractParser;
import eu.stamp_project.dspot.selector.pitmutantscoreselector.AbstractPitResult;
import eu.stamp_project.dspot.selector.pitmutantscoreselector.PitXMLResultParser;
import eu.stamp_project.prettifier.Main;
import eu.stamp_project.prettifier.output.report.ReportJSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtLoop;
import spoon.reflect.declaration.CtNamedElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Wessel Oosterbroek.
 * w.oosterbroek@student.tudelft.nl
 * on 23/05/2021
 */
public class ResearchProjectMinimizer implements Minimizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralMinimizer.class);

    //Logging
    private List<Long> timesMinimizationInMillis = new ArrayList<>();

    private AutomaticBuilder builder;

    private String classpathClassesProject;

    private String absolutePathToProjectRoot;

    private String absolutePathToTestClasses;

    private AbstractParser parser;
    private CtType<?> testClass;
    private CtType<?> currentTestClass;

    private long startTime;

    List<AbstractPitResult> pitResultBeforeMinimization = null;

    public ResearchProjectMinimizer(CtType<?> testClass,
                              AutomaticBuilder automaticBuilder,
                              String pathToProjectRoot,
                              String classpathProject,
                              String absolutePathToTestClasses) {
        this.testClass = testClass;
        this.parser = new PitXMLResultParser();
        this.builder = automaticBuilder;
        this.classpathClassesProject = classpathProject;
        this.absolutePathToProjectRoot = pathToProjectRoot;
        this.absolutePathToTestClasses = absolutePathToTestClasses;
    }

    /**
     * Minimize the given amplified test case.
     * @param amplifiedTestToBeMinimized the amplified test method to be minimized by this minimizer.
     * @return The minimized method.
     */
    @Override
    public CtMethod<?> minimize(CtMethod<?> amplifiedTestToBeMinimized) {
        CtMethod<?> clone = amplifiedTestToBeMinimized.clone();
        this.startTime = System.currentTimeMillis();
        this.currentTestClass = cloneAndRemoveAllTestsButTheGivenOne(amplifiedTestToBeMinimized);

        //Reset pitResult, we only calculate this a single time when we need it,
        pitResultBeforeMinimization = null;

        //First step: Everything else except for needed declarations are removed.
        removeUnnecessaryStatementsBroad(clone);
        if (checkMethodEquivalance(clone, amplifiedTestToBeMinimized)) {
            printResult(clone);
            return clone;
        }

        //Second Step: Keep all statements that interact with (indirectly) used objects.
        clone = amplifiedTestToBeMinimized.clone();
        removeUnnecessaryStatements(clone);
        if (checkMethodEquivalance(clone, amplifiedTestToBeMinimized)) {
            printResult(clone);
            return clone;
        }

        //Third step: only remove unused objects.
        clone = amplifiedTestToBeMinimized.clone();
        removeUnnecessaryStatementsSafe(clone);
        if (checkMethodEquivalance(clone, amplifiedTestToBeMinimized)) {
            printResult(clone);
            return clone;
        }

        //All steps failed, return original test case.
        clone = amplifiedTestToBeMinimized.clone();
        printResult(clone);
        return clone;
    }

    /**
     * Checks if two given test methods are equivalant. (Are identical or cover the same mutants)
     * @param clone Changed test method
     * @param testToBeAmplified Original test method.
     * @return true if the cloned test case covers the same mutants as the testToBeAmplified or if it is unchanged, false otherwise.
     */
    private boolean checkMethodEquivalance(CtMethod<?> clone, CtMethod<?> testToBeAmplified) {
        //We didn't change the method, no reason to check it again.
        if (clone.equals(testToBeAmplified))
            return true;

        if (pitResultBeforeMinimization == null) {
            pitResultBeforeMinimization = printCompileAndRunPit(currentTestClass);
        }

        try {
            if (runTestCase(clone, pitResultBeforeMinimization)) {
                return true;
            }
        } catch (Exception e) {
            //Pit throws an exception if the test case fails/doesn't run anymore.
            return false;
        }

        return false;
    }

    /**
     * Prints minimizing result to console.
     * @param prettifiedTest The test that was minimized.
     */
    private void printResult(CtMethod<?> prettifiedTest) {
        final long elapsedTime = System.currentTimeMillis() - this.startTime;
        this.timesMinimizationInMillis.add(elapsedTime);
        LOGGER.info("Reduce {}, {} statements to {} statements in {} ms.",
                prettifiedTest.getSimpleName(),
                prettifiedTest.getBody().getStatements().size(),
                prettifiedTest.getBody().getStatements().size(),
                elapsedTime
        );
        this.testClass.getPackage().removeType(this.currentTestClass);;
    }

    /**
     * Source: PitMutantMinimizer, by Benjamin DANGLOT.
     * @return cloned test class that contains onl the specific test provided.
     */
    private CtType<?> cloneAndRemoveAllTestsButTheGivenOne(CtMethod<?> amplifiedTestToBeMinimized) {
        CtType<?> testClone = testClass.clone();
        testClass.getPackage().addType(testClone);
        TestFramework.getAllTest(testClone).stream().filter(test -> !test.equals(amplifiedTestToBeMinimized))
                .forEach(testClone::removeMethod);
        return testClone;
    }

    /**
     * Removes all statements, expect for necessary declarations.
     * @param test The test of which the statement need to be removed.
     */
    private void removeUnnecessaryStatementsBroad(CtMethod<?> test) {
        List<CtStatement> statements = test.getBody().getStatements();
        List<CtInvocation<?>> assertions = getAssertions(test);

        //Map all relations between references
        ArrayList<List<String>> pools = new ArrayList<>();
        for (int i = statements.size() - 1; i >= 0; i--) {
            CtStatement curStatement = statements.get(i);

            List<String> neededReferences = curStatement.getElements(new TypeFilter<>(CtVariableReference.class)).stream()
                .map(CtReference::getSimpleName).collect(Collectors.toList());
            List<String> variables = curStatement.getElements(new TypeFilter<>(CtVariable.class)).stream()
                .map(CtNamedElement::getSimpleName).collect(Collectors.toList());
            //Not a declaration, we don't care about this statement.
            if (variables.size() == 0) {
                continue;
            }

            neededReferences.addAll(variables);

            for (int x = i - 1; x >= 0; x--) {
                CtStatement otherStatement = statements.get(x);
                //Only look for declarations, not CtVariableReference
                List<String> varsOther = otherStatement.getElements(new TypeFilter<>(CtVariable.class)).stream()
                    .map(CtNamedElement::getSimpleName).collect(Collectors.toList());

                if (!Collections.disjoint(neededReferences, varsOther)) {
                    variables.addAll(varsOther);
                }
            }
            if (!variables.isEmpty())
                pools.add(variables);
        }

        //Check which statements are relevant for assertions.
        List<String> usedReferences = new ArrayList<>();
        for (int i = assertions.size() - 1; i >= 0; i--) {
            CtInvocation<?> curAssertion = assertions.get(i);
            usedReferences.addAll(
                    curAssertion.getElements(new TypeFilter<>(CtVariableReference.class)).stream()
                .map(CtReference::getSimpleName).collect(Collectors.toList()));
            for (List<String> pool : pools) {
                //First element of the pool contains the actual variable that is being assigned.
                if (usedReferences.contains(pool.get(0))) {
                    usedReferences.addAll(pool);
                }
            }
        }

        List<CtStatement> unnecessaryStatements = getNotDeclarations(test, usedReferences);
        removeStatements(test, unnecessaryStatements);
    }

    /**
     * Removes all unused objects and objects which only use necessary variables when declared.
     * @param test The test of which the statement need to be removed.
     */
    private void removeUnnecessaryStatements(CtMethod<?> test) {
        List<CtStatement> statements = test.getBody().getStatements();
        List<CtInvocation<?>> assertions = getAssertions(test);

        //Map all relations between references
        ArrayList<List<String>> pools = new ArrayList<>();
        ArrayList<Integer> marked = new ArrayList<>();
        for (int i = statements.size() - 1; i >= 0; i--) {
            CtStatement curStatement = statements.get(i);

            //Mark statement if it contains a variable declaration (because we later want to ignore these statements if they only interact with needed variables when declared)
            if (curStatement.getElements(new TypeFilter<>(CtVariable.class)).size() > 0) {
                marked.add(pools.size());
            }

            //Get references in this statement.
            List<String> variables = curStatement.getElements(new TypeFilter<>(CtVariable.class)).stream()
                .map(CtNamedElement::getSimpleName).collect(Collectors.toList());
            variables.addAll(curStatement.getElements(new TypeFilter<>(CtVariableReference.class)).stream()
                             .map(CtReference::getSimpleName).collect(Collectors.toList()));

            for (int x = i - 1; x >= 0; x--) {
                CtStatement otherStatement = statements.get(x);

                List<String> varsOther = otherStatement.getElements(new TypeFilter<>(CtVariableReference.class)).stream()
                    .map(CtReference::getSimpleName).collect(Collectors.toList());

                //Exclude other statements that contain a variable declaration (as we only want to include them if we need the variable being declared)
                //Not if it is being declared using other variables that are used in the assert statement.
                if (otherStatement.getElements(new TypeFilter<>(CtVariable.class)).size() > 0 
                    && otherStatement.getElements(new TypeFilter<>(CtLoop.class)).size() == 0) {
                    continue;
                }

                if (otherStatement.getElements(new TypeFilter<>(CtLoop.class)).size() > 0) {
                    varsOther = new ArrayList<>();
                    for (CtLoop loop : otherStatement.getElements(new TypeFilter<>(CtLoop.class))) {
                        varsOther.addAll(loop.getBody().getElements(new TypeFilter<>(CtVariableReference.class)).stream()
                                         .map(CtReference::getSimpleName).collect(Collectors.toList()));
                    }
                }

                if (!Collections.disjoint(variables, varsOther)) {
                    variables.addAll(varsOther);
                }
            }
            pools.add(variables);
        }

        //Check which statements are relevant for assertions.
        List<String> usedReferences = new ArrayList<>();
        for (int i = assertions.size() - 1; i >= 0; i--) {
            CtInvocation<?> curAssertion = assertions.get(i);
            usedReferences.addAll(
                    curAssertion.getElements(new TypeFilter<>(CtVariableReference.class)).stream()
                .map(CtReference::getSimpleName).collect(Collectors.toList()));

            for (int x = 0; x < pools.size(); x++) {
                if (marked.contains(x)) {
                    if (!usedReferences.contains(pools.get(x).get(0))) {
                        continue;
                    }
                }
                if (!Collections.disjoint(usedReferences, pools.get(x))) {
                    usedReferences.addAll(pools.get(x));
                }
            }
        }

        List<CtStatement> unnecessaryStatements = getUnusedStatements(test, usedReferences);
        removeStatements(test, unnecessaryStatements);
    }

    /**
     * Removes only the statements which have no reference to (indirectly) variables whatsoever.
     * @param test The test of which the statement need to be removed.
     */
    private void removeUnnecessaryStatementsSafe(CtMethod<?> test) {
        List<CtStatement> statements = test.getBody().getStatements();
        List<CtInvocation<?>> assertions = getAssertions(test);

        //Map all relations between references
        ArrayList<List<String>> pools = new ArrayList<>();
        for (int i = statements.size() - 1; i >= 0; i--) {
            CtStatement curStatement = statements.get(i);

            List<String> variables = curStatement.getElements(new TypeFilter<>(CtVariableReference.class)).stream()
                .map(CtReference::getSimpleName).collect(Collectors.toList());
            variables.addAll(curStatement.getElements(new TypeFilter<>(CtVariable.class)).stream()
                             .map(CtNamedElement::getSimpleName).collect(Collectors.toList()));
            for (int x = i - 1; x >= 0; x--) {
                CtStatement otherStatement = statements.get(x);

                List<String> varsOther = otherStatement.getElements(new TypeFilter<>(CtVariableReference.class)).stream()
                    .map(CtReference::getSimpleName).collect(Collectors.toList());
                varsOther.addAll(otherStatement.getElements(new TypeFilter<>(CtVariable.class)).stream()
                                 .map(CtNamedElement::getSimpleName).collect(Collectors.toList()));

                if (!Collections.disjoint(variables, varsOther)) {
                    variables.addAll(varsOther);
                }
            }
            pools.add(variables);
        }

        //Check which statements are relevant for assertions.
        List<String> usedReferences = new ArrayList<>();
        for (int i = assertions.size() - 1; i >= 0; i--) {
            CtInvocation<?> curAssertion = assertions.get(i);
            usedReferences.addAll(
                    curAssertion.getElements(new TypeFilter<>(CtVariableReference.class)).stream()
                .map(CtReference::getSimpleName).collect(Collectors.toList()));

            for (List<String> pool : pools) {
                if (!Collections.disjoint(usedReferences, pool)) {
                    usedReferences.addAll(pool);
                }
            }
        }

        List<CtStatement> unnecessaryStatements = getUnusedStatements(test, usedReferences);
        removeStatements(test, unnecessaryStatements);
    }

    /**
     * Removes given statements from the body of a test case.
     * @param test The test of which the statements need to be removed.
     * @param statements The statements that need to be removed.
     */
    private void removeStatements(CtMethod<?> test, List<CtStatement> statements) {
        for (CtStatement statement : statements) {
            test.getBody().removeStatement(statement);
        }
    }

    /**
     * Gets all Junit assertions in a given test case.
     * @param test The test case
     * @return all assertions in the test case
     */
    private List<CtInvocation<?>> getAssertions(CtMethod<?> test) {
        return
                test.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                    @Override
                    public boolean matches(CtInvocation<?> element) {
                        return TestFramework.get().isAssert(element);
                    }
                });
    }

    /**
     * Given a test and a list that contains the name of variable references, return all statements that do not contain any of these references.
     * @param test The test case
     * @param usedReferences The references that the statements should not contain.
     * @return A list of all statements in the test case that do not contain the provided references.
     */
    private List<CtStatement> getUnusedStatements(CtMethod<?> test, List<String> usedReferences) {
        List<CtStatement> statements = test.getBody().getStatements();
        List<CtStatement> unusedStatements =  new ArrayList<>();

        for (int i = statements.size() - 1; i >= 0; i--) {
            CtStatement curStatement = statements.get(i);

            List<String> variables = curStatement.getElements(new TypeFilter<>(CtVariable.class)).stream()
                .map(CtNamedElement::getSimpleName).collect(Collectors.toList());
            
            if (variables.size() > 0 && Collections.disjoint(usedReferences, variables)) {
                unusedStatements.add(curStatement);
                continue;
            }

            variables.addAll(curStatement.getElements(new TypeFilter<>(CtVariableReference.class)).stream()
                             .map(CtReference::getSimpleName).collect(Collectors.toList()));
            if (Collections.disjoint(usedReferences, variables)) {
                unusedStatements.add(curStatement);
            }
        }
        return unusedStatements;
    }

    /**
     * Returns a list of statements that are either not declarations or declarations whose identifier is not in the usedReferences list.
     * Note that the usedReferences list can contain the same string multiple times.
     *
     * @param test The test case that needs to be considered.
     * @param usedReferences A list of used references, e.g. variables for which we need to keep the declarations.
     *
     * @return A list of statements that are either not declarations or declarations whose identifier is not in the usedReferences list.
     */
    private List<CtStatement> getNotDeclarations(CtMethod<?> test, List<String> usedReferences) {
        List<CtStatement> statements = test.getBody().getStatements();
        List<CtStatement> unusedStatements =  new ArrayList<>();

        for (int i = statements.size() - 1; i >= 0; i--) {
            CtStatement curStatement = statements.get(i);
            if (TestFramework.get().isAssert(curStatement)) {
                continue;
            }

            //Get a list of all declarations in this statement.
            List<String> variables = curStatement.getElements(new TypeFilter<>(CtVariable.class)).stream()
                .map(CtNamedElement::getSimpleName).collect(Collectors.toList());
            //If usedReferences does not contain one of these declarations we mark the statement.
             if (Collections.disjoint(usedReferences, variables)) {
                unusedStatements.add(curStatement);
            }
        }
        return unusedStatements;
    }

    /**
     * Returns true if mutation score did not get worse compared to the result before minimization, return false if the results did get worse.
     * @param minimizedTest The (minimized test) to be tested.
     * @param pitResultBeforeMinimization The pit result of the original test, before minization.
     * @return True if the mutation score did not get worse, false if it did get worse.
     */
    public boolean runTestCase(CtMethod<?> minimizedTest, List<AbstractPitResult> pitResultBeforeMinimization) {
        CtType<?> clone = this.testClass.clone();
        this.testClass.getPackage().addType(clone);
        clone.setParent(this.testClass.getParent());
        clone.addMethod(minimizedTest);

        //Remove all other tests from the created class, except the one we are interested in.
        TestFramework.getAllTest(clone).stream().filter(test -> !test.equals(minimizedTest))
                .forEach(clone::removeMethod);

        return printCompileAndRunPit(clone).equals(pitResultBeforeMinimization);
    }

    /**
     * Source: PitMutantMinimizer, by Benjamin DANGLOT.
     * @param testClass
     * @return
     */
    List<AbstractPitResult> printCompileAndRunPit(CtType<?> testClass) {
        DSpotUtils.printCtTypeToGivenDirectory(testClass, new File(DSpotCompiler.getPathToAmplifiedTestSrc()));
        final String classpath = this.builder
                .buildClasspath()
                + AmplificationHelper.PATH_SEPARATOR +
                this.classpathClassesProject
                + AmplificationHelper.PATH_SEPARATOR + DSpotUtils.getAbsolutePathToDSpotDependencies();
        DSpotCompiler.compile(
                DSpotCompiler.getPathToAmplifiedTestSrc(),
                classpath,
                new File(this.absolutePathToTestClasses)
        );

        this.builder.runPit(testClass);
        return parser.parseAndDelete(
                this.absolutePathToProjectRoot + this.builder.getOutputDirectoryPit()
        );
    }

    /**
     * Update results report with information about the minimization.
     * @param report the json report that contains information about the minimization.
     */
    @Override
    public void updateReport(ReportJSON report) {
        report.researchProjectJSON.medianTimeMinimizationInMillis = Main.getMedian(this.timesMinimizationInMillis);
    }
}
