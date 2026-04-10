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

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
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
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageSkillMojoTest {

    @Mock
    private MavenProject project;

    @Mock
    private MavenProjectHelper projectHelper;

    @Mock
    private Build build;

    private PackageSkillMojo mojo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        mojo = new PackageSkillMojo();
        setField(mojo, "project", project);
        setField(mojo, "projectHelper", projectHelper);
        setField(mojo, "classifier", PackageSkillMojo.DEFAULT_CLASSIFIER);
        setField(mojo, "skipIfMissing", true);
        setField(mojo, "failOnValidationError", true);
        setField(mojo, "includeLibraryMetadata", true);

        when(project.getGroupId()).thenReturn("com.example");
        when(project.getArtifactId()).thenReturn("test-lib");
        when(project.getVersion()).thenReturn("1.0.0");
        when(project.getBasedir()).thenReturn(tempDir.toFile());
        when(project.getBuild()).thenReturn(build);
        when(build.getDirectory()).thenReturn(tempDir.resolve("target").toString());

        Files.createDirectories(tempDir.resolve("target"));
    }

    @Test
    void packagesSkillFromConventionDirectory() throws Exception {
        // Create skill at ${basedir}/skills/${artifactId}/
        Path skillDir = tempDir.resolve("skills/test-lib");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"),
                ("---\nname: test-lib\ndescription: A test library skill for testing the packaging process of the skill JAR plugin.\n---\n\n# Test Lib\n\nUsage docs.\n").getBytes(StandardCharsets.UTF_8));

        mojo.execute();

        File expectedJar = tempDir.resolve("target/test-lib-1.0.0-skills.jar").toFile();
        assertThat(expectedJar).exists();

        // Verify JAR contents
        try (JarFile jf = new JarFile(expectedJar)) {
            assertThat(jf.getEntry("skill/SKILL.md")).isNotNull();
            assertThat(jf.getEntry("META-INF/skill-library.properties")).isNotNull();
        }

        // Verify artifact was attached
        verify(projectHelper).attachArtifact(eq(project), eq("jar"), eq("skills"), any(File.class));
    }

    @Test
    void packagesSkillFromFallbackDirectory() throws Exception {
        // Create skill at ${basedir}/skills/ (no artifactId subdirectory)
        Path skillDir = tempDir.resolve("skills");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"),
                ("---\nname: test-lib\ndescription: A test library skill for testing the fallback directory packaging logic.\n---\n\n# Test\n\nDocs.\n").getBytes(StandardCharsets.UTF_8));

        mojo.execute();

        File expectedJar = tempDir.resolve("target/test-lib-1.0.0-skills.jar").toFile();
        assertThat(expectedJar).exists();
        verify(projectHelper).attachArtifact(eq(project), eq("jar"), eq("skills"), any(File.class));
    }

    @Test
    void skipsWhenNoSkillDirectory() throws Exception {
        // No skills directory at all
        mojo.execute();

        // Should not fail, should not attach anything
        verify(projectHelper, never()).attachArtifact(any(), any(), any(), any(File.class));
    }

    @Test
    void packagesWithCustomClassifier() throws Exception {
        setField(mojo, "classifier", "ai-skill");

        Path skillDir = tempDir.resolve("skills");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"),
                ("---\nname: test-lib\ndescription: A test library skill for testing the custom classifier packaging.\n---\n\n# Test\n\nDocs.\n").getBytes(StandardCharsets.UTF_8));

        mojo.execute();

        File expectedJar = tempDir.resolve("target/test-lib-1.0.0-ai-skill.jar").toFile();
        assertThat(expectedJar).exists();
        verify(projectHelper).attachArtifact(eq(project), eq("jar"), eq("ai-skill"), any(File.class));
    }

    @Test
    void packagesWithoutMetadata() throws Exception {
        setField(mojo, "includeLibraryMetadata", false);

        Path skillDir = tempDir.resolve("skills");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"),
                ("---\nname: test-lib\ndescription: A test library skill for testing packaging without metadata properties.\n---\n\n# Test\n\nDocs.\n").getBytes(StandardCharsets.UTF_8));

        mojo.execute();

        File expectedJar = tempDir.resolve("target/test-lib-1.0.0-skills.jar").toFile();
        try (JarFile jf = new JarFile(expectedJar)) {
            assertThat(jf.getEntry("META-INF/skill-library.properties")).isNull();
        }
    }

    @Test
    void usesExplicitSkillDirectory() throws Exception {
        Path customDir = tempDir.resolve("custom-skills");
        Files.createDirectories(customDir);
        Files.write(customDir.resolve("SKILL.md"),
                ("---\nname: test-lib\ndescription: A test library skill for testing explicit skill directory configuration.\n---\n\n# Custom\n\nDocs.\n").getBytes(StandardCharsets.UTF_8));

        setField(mojo, "skillDirectory", customDir.toFile());

        mojo.execute();

        verify(projectHelper).attachArtifact(eq(project), eq("jar"), eq("skills"), any(File.class));
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
