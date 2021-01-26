package eu.stamp_project.dspot.common.report.output.selector.extendedcoverage.json;

import eu.stamp_project.dspot.selector.extendedcoverageselector.CoverageImprovement;

public class TestCaseJSON {

    private final String name;
    private final int nbAssertionAdded;
    private final int nbInputAdded;
    private final CoverageImprovement coverage;

    public TestCaseJSON(String name, int nbAssertionAdded, int nbInputAdded, CoverageImprovement coverage) {
        this.name = name;
        this.nbAssertionAdded = nbAssertionAdded;
        this.nbInputAdded = nbInputAdded;
        this.coverage = coverage;
    }

    public String getName() {
        return name;
    }

    public int getNbAssertionAdded() {
        return nbAssertionAdded;
    }

    public int getNbInputAdded() {
        return nbInputAdded;
    }

    public CoverageImprovement getCoverage() {
        return coverage;
    }
}
