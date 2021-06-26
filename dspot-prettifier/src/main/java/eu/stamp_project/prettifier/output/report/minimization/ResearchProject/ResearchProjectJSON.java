package eu.stamp_project.prettifier.output.report.minimization.ResearchProject;

import eu.stamp_project.prettifier.output.report.minimization.MinimizationOfAssertionsJSON;

import java.util.ArrayList;
import java.util.List;

/**
 * created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 06/03/19
 */
public class ResearchProjectJSON extends MinimizationOfAssertionsJSON {
    public double medianLevelPitRuns;
    public int twoPitRuns;
    public int threePitRuns;
    public int fourPitRuns;
    public int fail;
    public List<Integer> statementCount;
    public List<Integer> step1;
    public List<Integer> step1Nochange;
    public List<Integer> step1overall;
    public List<Integer> step2;
    public List<Integer> step2NoChange;
    public List<Integer> step2overall;
    public List<Integer> step3;
    public List<Integer> step3NoChange;
    public List<Integer> step3overall;
    public List<Integer> failList;
}
