package eu.stamp_project.dspot.common.report.output.selector.extendedcoverage.json;

import eu.stamp_project.dspot.selector.extendedcoverageselector.CoverageImprovement;
import eu.stamp_project.dspot.selector.extendedcoverageselector.ExtendedCoverage;

import java.util.ArrayList;
import java.util.List;

public class TestClassJSON implements eu.stamp_project.dspot.common.report.output.selector.TestClassJSON {


    private final CoverageImprovement amplifiedCoverage;
    private List<TestCaseJSON> testCases;
    private final ExtendedCoverage initialCoverage;

    public TestClassJSON(ExtendedCoverage initialCoverage, CoverageImprovement amplifiedCoverage) {
        this.initialCoverage = initialCoverage;
        this.amplifiedCoverage = amplifiedCoverage;
    }

    public boolean addTestCase(TestCaseJSON testCaseJSON) {
        if (this.testCases == null) {
            this.testCases = new ArrayList<>();
        }
        return this.testCases.add(testCaseJSON);
    }

    public List<TestCaseJSON> getTestCases() {
        return this.testCases;
    }

    public ExtendedCoverage getInitialCoverage() {
        return initialCoverage;
    }

    public CoverageImprovement getAmplifiedCoverage() {
        return amplifiedCoverage;
    }
}
