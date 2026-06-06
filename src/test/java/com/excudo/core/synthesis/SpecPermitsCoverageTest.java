package com.excudo.core.synthesis;

import com.excudo.core.synthesis.spec.CommandSpec;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertTrue;

/**
 * Meta-guard for the synthesis spec coverage matrix. Reflects over the
 * sealed {@code CommandSpec.permits} list at test time and asserts each
 * permits entry has a registered case in every coverage-required test
 * class (rewrite, retarget, JSON round-trip, mapper). If a new spec is
 * added to permits without a case appearing in all four, this test fails
 * with the exact list of missing entries -- forcing the coverage to
 * remain complete as the vocabulary grows.
 *
 * <p>Implementation: source-text scan rather than reflection so the
 * coverage check is independent of test-instance lifecycle. Each
 * coverage-required test class self-documents which specs it covers
 * via the {@code sampleOf()} switch or a similar enumeration; the
 * meta-test confirms every permits entry appears as a literal
 * {@code CommandSpec.XxxSpec} reference in each registry file.
 */
public class SpecPermitsCoverageTest {

    /** Coverage-required test files. Each one must reference every
     *  spec in {@link CommandSpec#getPermittedSubclasses()}. */
    private static final String[] COVERAGE_REGISTRIES = {
        "src/test/java/com/excudo/core/synthesis/SpecRewriterTest.java",
        "src/test/java/com/excudo/core/synthesis/RetargetToSlideTest.java",
        "src/test/java/com/excudo/core/synthesis/spec/CommandSpecJsonTest.java",
        "src/test/java/com/excudo/core/synthesis/spec/SpecToCommandMapperTest.java",
    };

    @Test
    public void everyPermitsEntry_coveredBy_eachRegistry() throws IOException {
        Set<String> permitNames = permittedSimpleNames();
        Set<String> failures = new TreeSet<>();

        for (String path : COVERAGE_REGISTRIES) {
            String body = readBody(Paths.get(path));
            for (String permitName : permitNames) {
                if (!body.contains("CommandSpec." + permitName)) {
                    failures.add(path + " :: missing reference to CommandSpec." + permitName);
                }
            }
        }

        assertTrue("Coverage matrix incomplete:\n  " + String.join("\n  ", failures)
            + "\nAdd a sample / @Test method that references "
            + "CommandSpec.<SpecName> in each listed file. Plan reference: "
            + "happy-wandering-octopus.md Phase 2.",
            failures.isEmpty());
    }

    private static Set<String> permittedSimpleNames() {
        Set<String> out = new HashSet<>();
        for (Class<?> raw : CommandSpec.class.getPermittedSubclasses()) {
            out.add(raw.getSimpleName());
        }
        return out;
    }

    private static String readBody(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
}
