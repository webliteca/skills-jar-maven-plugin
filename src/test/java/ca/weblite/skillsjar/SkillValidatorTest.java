/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.weblite.skillsjar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillValidatorTest {

    private SkillValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SkillValidator();
    }

    @Test
    void validatesMinimalSkill() {
        File skillDir = getFixture("minimal-skill");
        SkillValidator.ValidationResult result = validator.validate(skillDir);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void validatesSkillWithReferences() {
        File skillDir = getFixture("skill-with-references");
        SkillValidator.ValidationResult result = validator.validate(skillDir);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void rejectsNoFrontmatter() {
        File skillDir = getFixture("invalid-no-frontmatter");
        SkillValidator.ValidationResult result = validator.validate(skillDir);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("missing YAML frontmatter"));
    }

    @Test
    void rejectsBadName() {
        File skillDir = getFixture("invalid-bad-name");
        SkillValidator.ValidationResult result = validator.validate(skillDir);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Invalid skill name"));
    }

    @Test
    void rejectsNonexistentDirectory() {
        File nonexistent = new File("/nonexistent/directory");
        SkillValidator.ValidationResult result = validator.validate(nonexistent);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("does not exist"));
    }

    @Test
    void rejectsMissingSkillMd(@TempDir Path tempDir) {
        // Empty directory — no SKILL.md
        SkillValidator.ValidationResult result = validator.validate(tempDir.toFile());

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("SKILL.md not found"));
    }

    @Test
    void rejectsEmptyBody(@TempDir Path tempDir) throws IOException {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.write(skillMd, "---\nname: empty-body\ndescription: A skill with an empty body that should fail validation checks.\n---\n".getBytes(StandardCharsets.UTF_8));

        SkillValidator.ValidationResult result = validator.validate(tempDir.toFile());

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("body is empty"));
    }

    @Test
    void rejectsMissingDescription(@TempDir Path tempDir) throws IOException {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.write(skillMd, "---\nname: no-desc\n---\n\nBody content here.\n".getBytes(StandardCharsets.UTF_8));

        SkillValidator.ValidationResult result = validator.validate(tempDir.toFile());

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("missing required field: description"));
    }

    @Test
    void rejectsMissingName(@TempDir Path tempDir) throws IOException {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.write(skillMd, "---\ndescription: A valid description that is long enough to pass the length check.\n---\n\nBody content here.\n".getBytes(StandardCharsets.UTF_8));

        SkillValidator.ValidationResult result = validator.validate(tempDir.toFile());

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("missing required field: name"));
    }

    @Test
    void warnsOnShortDescription(@TempDir Path tempDir) throws IOException {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.write(skillMd, "---\nname: short-desc\ndescription: Too short\n---\n\nBody content.\n".getBytes(StandardCharsets.UTF_8));

        SkillValidator.ValidationResult result = validator.validate(tempDir.toFile());

        // Short description is a warning, not an error
        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("very short"));
    }

    @Test
    void warnsOnUnexpectedRootFile(@TempDir Path tempDir) throws IOException {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.write(skillMd, "---\nname: unexpected-files\ndescription: A skill that has unexpected files at the root for validation testing.\n---\n\nBody.\n".getBytes(StandardCharsets.UTF_8));

        // Create an unexpected file
        Files.write(tempDir.resolve("unexpected.txt"), "unexpected".getBytes(StandardCharsets.UTF_8));

        SkillValidator.ValidationResult result = validator.validate(tempDir.toFile());

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("Unexpected entry"));
    }

    @Test
    void warnsOnOrphanReferenceFile(@TempDir Path tempDir) throws IOException {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.write(skillMd, "---\nname: orphan-refs\ndescription: A skill with orphan reference files that are not linked from SKILL.md for testing.\n---\n\nBody content.\n".getBytes(StandardCharsets.UTF_8));

        // Create a reference file that isn't linked
        Path refsDir = tempDir.resolve("references");
        Files.createDirectories(refsDir);
        Files.write(refsDir.resolve("orphan.md"), "orphan".getBytes(StandardCharsets.UTF_8));

        SkillValidator.ValidationResult result = validator.validate(tempDir.toFile());

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("Orphan reference"));
    }

    @Test
    void warnsOnBrokenLink(@TempDir Path tempDir) throws IOException {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.write(skillMd, ("---\nname: broken-links\ndescription: A skill with broken reference links for testing the validator warning detection.\n---\n\n"
                + "See [missing doc](references/nonexistent.md) for info.\n").getBytes(StandardCharsets.UTF_8));

        Path refsDir = tempDir.resolve("references");
        Files.createDirectories(refsDir);

        SkillValidator.ValidationResult result = validator.validate(tempDir.toFile());

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("Broken reference"));
    }

    @Test
    void rejectsNameTooShort(@TempDir Path tempDir) throws IOException {
        Path skillMd = tempDir.resolve("SKILL.md");
        Files.write(skillMd, "---\nname: ab\ndescription: A skill with a name that is too short and should be rejected by the validator.\n---\n\nBody.\n".getBytes(StandardCharsets.UTF_8));

        SkillValidator.ValidationResult result = validator.validate(tempDir.toFile());

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Invalid skill name") && e.contains("at least 3"));
    }

    private File getFixture(String name) {
        File fixture = new File("src/test/resources/fixtures/" + name);
        if (!fixture.isDirectory()) {
            // Try relative to working directory
            fixture = new File(System.getProperty("user.dir"), "src/test/resources/fixtures/" + name);
        }
        return fixture;
    }
}
