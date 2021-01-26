package eu.stamp_project.dspot.selector;

import java.text.DecimalFormat;

public class ExtendedCoverageSelectorTest extends AbstractSelectorRemoveOverlapTest {

    @Override
    protected TestSelector getTestSelector() {
        return new ExtendedCoverageSelector(
                this.builder,
                this.configuration
        );
    }

    @Override
    protected String getContentReportFile() {
        return "Amplification results with 2 new tests.";
    }
}
