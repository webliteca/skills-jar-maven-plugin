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

import ca.weblite.skillsjar.util.JarUtils;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class InstallSkillsMojoTest {

    @Test
    void installsSkillBundle(@TempDir Path tempDir) throws IOException {
        // Create a skill JAR
        Path skillSrc = tempDir.resolve("skill-src");
        Files.createDirectories(skillSrc);
        Files.write(skillSrc.resolve("SKILL.md"),
                ("---\nname: test-skill\ndescription: A test skill for the installer.\n---\n\n# Test\n\nBody.\n")
                        .getBytes(StandardCharsets.UTF_8));

        File jarFile = tempDir.resolve("test-skill.jar").toFile();
        Properties metadata = new Properties();
        metadata.setProperty("groupId", "com.example");
        metadata.setProperty("artifactId", "test-lib");
        metadata.setProperty("version", "1.0.0");
        JarUtils.createSkillJar(skillSrc.toFile(), jarFile, metadata);

        // Create bundle
        SkillBundle bundle = SkillBundle.builder()
                .groupId("com.example")
                .artifactId("test-lib")
                .version("1.0.0")
                .skillName("test-skill")
                .jarFile(jarFile)
                .build();

        // Install
        File installDir = tempDir.resolve("skills").toFile();
        SkillInstaller installer = new SkillInstaller(installDir, true, new SystemStreamLog());
        int installed = installer.installAll(Arrays.asList(bundle));

        assertThat(installed).isEqualTo(1);
        assertThat(new File(installDir, "test-lib/SKILL.md")).exists();

        // Verify manifest
        File manifestFile = new File(installDir, ".skill-manifest.json");
        assertThat(manifestFile).exists();
        String manifestContent = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
        assertThat(manifestContent).contains("com.example:test-lib:1.0.0");
    }

    @Test
    void skipsUnchangedSkill(@TempDir Path tempDir) throws IOException {
        // Create and install a skill JAR
        Path skillSrc = tempDir.resolve("skill-src");
        Files.createDirectories(skillSrc);
        Files.write(skillSrc.resolve("SKILL.md"),
                ("---\nname: test-skill\ndescription: A test skill for the installer.\n---\n\n# Test\n\nBody.\n")
                        .getBytes(StandardCharsets.UTF_8));

        File jarFile = tempDir.resolve("test-skill.jar").toFile();
        JarUtils.createSkillJar(skillSrc.toFile(), jarFile, null);

        SkillBundle bundle = SkillBundle.builder()
                .groupId("com.example")
                .artifactId("test-lib")
                .version("1.0.0")
                .skillName("test-skill")
                .jarFile(jarFile)
                .build();

        File installDir = tempDir.resolve("skills").toFile();
        SkillInstaller installer = new SkillInstaller(installDir, true, new SystemStreamLog());

        // First install
        installer.installAll(Arrays.asList(bundle));

        // Second install — should be skipped (same checksum)
        int installed = installer.installAll(Arrays.asList(bundle));
        assertThat(installed).isEqualTo(0);
    }

    @Test
    void installsMultipleBundles(@TempDir Path tempDir) throws IOException {
        File installDir = tempDir.resolve("skills").toFile();
        SkillInstaller installer = new SkillInstaller(installDir, true, new SystemStreamLog());

        List<SkillBundle> bundles = Arrays.asList(
                createTestBundle(tempDir, "lib-a", "skill-a"),
                createTestBundle(tempDir, "lib-b", "skill-b")
        );

        int installed = installer.installAll(bundles);
        assertThat(installed).isEqualTo(2);
        assertThat(new File(installDir, "lib-a/SKILL.md")).exists();
        assertThat(new File(installDir, "lib-b/SKILL.md")).exists();
    }

    @Test
    void manifestParseRoundtrip() {
        String json = "{\n"
                + "  \"test-lib\": {\n"
                + "    \"gav\": \"com.example:test-lib:1.0.0\",\n"
                + "    \"checksum\": \"abc123\",\n"
                + "    \"installedAt\": \"2024-01-01T00:00:00Z\",\n"
                + "    \"files\": [\"SKILL.md\", \"references/api.md\"]\n"
                + "  }\n"
                + "}\n";

        Map<String, SkillInstaller.ManifestEntry> manifest = SkillInstaller.parseManifest(json);
        assertThat(manifest).containsKey("test-lib");

        SkillInstaller.ManifestEntry entry = manifest.get("test-lib");
        assertThat(entry.gav).isEqualTo("com.example:test-lib:1.0.0");
        assertThat(entry.checksum).isEqualTo("abc123");
        assertThat(entry.files).containsExactly("SKILL.md", "references/api.md");
    }

    @Test
    void filterMatchesPatterns() {
        assertThat(SkillResolver.matchesPattern("com.example:test:1.0", "com.example:*:*")).isTrue();
        assertThat(SkillResolver.matchesPattern("com.example:test:1.0", "org.other:*:*")).isFalse();
        assertThat(SkillResolver.matchesPattern("com.example:test:1.0", "*:test:*")).isTrue();
    }

    @Test
    void filterMatchesWithIncludesAndExcludes() {
        List<String> includes = Arrays.asList("com.example:*:*");
        List<String> excludes = Arrays.asList("com.example:excluded:*");

        assertThat(SkillResolver.matchesFilters("com.example:test:1.0", includes, excludes)).isTrue();
        assertThat(SkillResolver.matchesFilters("com.example:excluded:1.0", includes, excludes)).isFalse();
        assertThat(SkillResolver.matchesFilters("org.other:test:1.0", includes, excludes)).isFalse();
    }

    private SkillBundle createTestBundle(Path tempDir, String artifactId, String skillName) throws IOException {
        Path skillSrc = tempDir.resolve("src-" + artifactId);
        Files.createDirectories(skillSrc);
        Files.write(skillSrc.resolve("SKILL.md"),
                ("---\nname: " + skillName + "\ndescription: Test skill " + skillName + " for installer tests.\n---\n\n# " + skillName + "\n\nBody.\n")
                        .getBytes(StandardCharsets.UTF_8));

        File jarFile = tempDir.resolve(artifactId + "-skills.jar").toFile();
        JarUtils.createSkillJar(skillSrc.toFile(), jarFile, null);

        return SkillBundle.builder()
                .groupId("com.example")
                .artifactId(artifactId)
                .version("1.0.0")
                .skillName(skillName)
                .jarFile(jarFile)
                .build();
    }
}
