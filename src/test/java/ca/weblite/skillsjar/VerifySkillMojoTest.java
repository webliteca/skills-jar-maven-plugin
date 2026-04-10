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

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class VerifySkillMojoTest {

    @Mock
    private MavenProject project;

    private VerifySkillMojo mojo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        mojo = new VerifySkillMojo();
        setField(mojo, "project", project);
        setField(mojo, "failOnValidationError", true);

        when(project.getArtifactId()).thenReturn("test-lib");
        when(project.getBasedir()).thenReturn(tempDir.toFile());
    }

    @Test
    void resolvesConventionDirectory() throws Exception {
        Path skillDir = tempDir.resolve("skills/test-lib");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"),
                ("---\nname: test-lib\ndescription: A test skill for convention directory resolution.\n---\n\n# Test\n").getBytes(StandardCharsets.UTF_8));

        File resolved = mojo.resolveSkillDirectory();
        assertThat(resolved).isEqualTo(skillDir.toFile());
    }

    @Test
    void resolvesScannedSubdirectoryWhenArtifactIdMismatches() throws Exception {
        // artifactId is "test-lib" but skill lives at skills/other-name/
        Path skillDir = tempDir.resolve("skills/other-name");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"),
                ("---\nname: other-name\ndescription: A skill whose directory name differs from the Maven artifactId.\n---\n\n# Other\n").getBytes(StandardCharsets.UTF_8));

        File resolved = mojo.resolveSkillDirectory();
        assertThat(resolved).isEqualTo(skillDir.toFile());
    }

    @Test
    void fallsBackToSkillsDirWhenMultipleSubdirectoriesHaveSkillMd() throws Exception {
        Path skillDirA = tempDir.resolve("skills/skill-a");
        Path skillDirB = tempDir.resolve("skills/skill-b");
        Files.createDirectories(skillDirA);
        Files.createDirectories(skillDirB);
        Files.write(skillDirA.resolve("SKILL.md"),
                ("---\nname: skill-a\ndescription: First skill.\n---\n\n# A\n").getBytes(StandardCharsets.UTF_8));
        Files.write(skillDirB.resolve("SKILL.md"),
                ("---\nname: skill-b\ndescription: Second skill.\n---\n\n# B\n").getBytes(StandardCharsets.UTF_8));

        File resolved = mojo.resolveSkillDirectory();
        // Ambiguous — should fall back to skills/ itself
        assertThat(resolved).isEqualTo(tempDir.resolve("skills").toFile());
    }

    @Test
    void returnsNullWhenNoSkillDirectory() {
        File resolved = mojo.resolveSkillDirectory();
        assertThat(resolved).isNull();
    }

    @Test
    void verifiesSkillFromScannedSubdirectory() throws Exception {
        Path skillDir = tempDir.resolve("skills/my-skill");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"),
                ("---\nname: my-skill\ndescription: A skill discovered via subdirectory scan during verification.\n---\n\n# My Skill\n\nDocs.\n").getBytes(StandardCharsets.UTF_8));

        // Should not throw — valid skill found via scan
        mojo.execute();
    }

    @Test
    void verifyFailsOnInvalidSkillFromScannedSubdirectory() throws Exception {
        Path skillDir = tempDir.resolve("skills/bad-skill");
        Files.createDirectories(skillDir);
        // Missing required frontmatter fields
        Files.write(skillDir.resolve("SKILL.md"),
                ("# No frontmatter\n").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(org.apache.maven.plugin.MojoFailureException.class);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
