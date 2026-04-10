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
package ca.weblite.skillsjar.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JarUtilsTest {

    @Test
    void createsSkillJar(@TempDir Path tempDir) throws IOException {
        // Create a minimal skill directory
        Path skillDir = tempDir.resolve("skill");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"), "# Test Skill".getBytes(StandardCharsets.UTF_8));

        Path refsDir = skillDir.resolve("references");
        Files.createDirectories(refsDir);
        Files.write(refsDir.resolve("api.md"), "# API".getBytes(StandardCharsets.UTF_8));

        File outputJar = tempDir.resolve("output.jar").toFile();

        Properties metadata = new Properties();
        metadata.setProperty("groupId", "com.example");
        metadata.setProperty("artifactId", "test-lib");
        metadata.setProperty("version", "1.0.0");

        JarUtils.createSkillJar(skillDir.toFile(), outputJar, metadata);

        assertThat(outputJar).exists();

        // Verify JAR contents
        try (JarFile jf = new JarFile(outputJar)) {
            assertThat(jf.getEntry("skill/SKILL.md")).isNotNull();
            assertThat(jf.getEntry("skill/references/api.md")).isNotNull();
            assertThat(jf.getEntry(JarUtils.METADATA_PATH)).isNotNull();
        }
    }

    @Test
    void createsSkillJarWithoutMetadata(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("skill");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"), "# Test".getBytes(StandardCharsets.UTF_8));

        File outputJar = tempDir.resolve("output.jar").toFile();
        JarUtils.createSkillJar(skillDir.toFile(), outputJar, null);

        try (JarFile jf = new JarFile(outputJar)) {
            assertThat(jf.getEntry("skill/SKILL.md")).isNotNull();
            assertThat(jf.getEntry(JarUtils.METADATA_PATH)).isNull();
        }
    }

    @Test
    void extractsSkillJar(@TempDir Path tempDir) throws IOException {
        // Create a skill JAR
        Path skillDir = tempDir.resolve("skill");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"), "# Test Skill\nContent".getBytes(StandardCharsets.UTF_8));

        File jarFile = tempDir.resolve("test.jar").toFile();
        JarUtils.createSkillJar(skillDir.toFile(), jarFile, null);

        // Extract it
        File extractDir = tempDir.resolve("extracted").toFile();
        List<String> extracted = JarUtils.extractSkillJar(jarFile, extractDir);

        assertThat(extracted).contains("SKILL.md");
        assertThat(new File(extractDir, "SKILL.md")).exists();
        String content = new String(Files.readAllBytes(new File(extractDir, "SKILL.md").toPath()), StandardCharsets.UTF_8);
        assertThat(content).contains("# Test Skill");
    }

    @Test
    void rejectsZipSlipPath(@TempDir Path tempDir) throws IOException {
        assertThatThrownBy(() ->
                JarUtils.validateEntryPath("../../etc/passwd", tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void rejectsAbsolutePath(@TempDir Path tempDir) throws IOException {
        assertThatThrownBy(() ->
                JarUtils.validateEntryPath("/etc/passwd", tempDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("absolute path");
    }

    @Test
    void acceptsValidRelativePath(@TempDir Path tempDir) throws IOException {
        // Should not throw
        JarUtils.validateEntryPath("references/api.md", tempDir);
        JarUtils.validateEntryPath("SKILL.md", tempDir);
    }

    @Test
    void rejectsZipSlipInExtraction(@TempDir Path tempDir) throws IOException {
        // Create a malicious JAR with a zip-slip entry
        File maliciousJar = tempDir.resolve("malicious.jar").toFile();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(maliciousJar), manifest)) {
            // Add a zip-slip entry
            jos.putNextEntry(new JarEntry("skill/../../evil.txt"));
            jos.write("malicious content".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }

        File extractDir = tempDir.resolve("extracted").toFile();
        assertThatThrownBy(() -> JarUtils.extractSkillJar(maliciousJar, extractDir))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void readsMetadata(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("skill");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"), "# Test".getBytes(StandardCharsets.UTF_8));

        Properties metadata = new Properties();
        metadata.setProperty("groupId", "com.example");
        metadata.setProperty("artifactId", "test-lib");
        metadata.setProperty("version", "2.0.0");

        File jarFile = tempDir.resolve("test.jar").toFile();
        JarUtils.createSkillJar(skillDir.toFile(), jarFile, metadata);

        Properties read = JarUtils.readMetadata(jarFile);
        assertThat(read).isNotNull();
        assertThat(read.getProperty("groupId")).isEqualTo("com.example");
        assertThat(read.getProperty("artifactId")).isEqualTo("test-lib");
        assertThat(read.getProperty("version")).isEqualTo("2.0.0");
    }

    @Test
    void returnsNullMetadataWhenAbsent(@TempDir Path tempDir) throws IOException {
        Path skillDir = tempDir.resolve("skill");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"), "# Test".getBytes(StandardCharsets.UTF_8));

        File jarFile = tempDir.resolve("test.jar").toFile();
        JarUtils.createSkillJar(skillDir.toFile(), jarFile, null);

        Properties read = JarUtils.readMetadata(jarFile);
        assertThat(read).isNull();
    }

    @Test
    void rejectsNonexistentSkillDirectory(@TempDir Path tempDir) {
        File nonexistent = tempDir.resolve("nonexistent").toFile();
        File outputJar = tempDir.resolve("output.jar").toFile();

        assertThatThrownBy(() -> JarUtils.createSkillJar(nonexistent, outputJar, null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void rejectsNonexistentJarForExtraction(@TempDir Path tempDir) {
        File nonexistent = tempDir.resolve("nonexistent.jar").toFile();
        File extractDir = tempDir.resolve("extracted").toFile();

        assertThatThrownBy(() -> JarUtils.extractSkillJar(nonexistent, extractDir))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("does not exist");
    }
}
