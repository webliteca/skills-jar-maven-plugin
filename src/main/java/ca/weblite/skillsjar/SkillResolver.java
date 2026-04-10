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

import ca.weblite.skillsjar.util.FrontmatterParser;
import ca.weblite.skillsjar.util.JarUtils;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Resolves skill JAR artifacts from the dependency tree.
 */
public class SkillResolver {

    private final RepositorySystem repoSystem;
    private final RepositorySystemSession repoSession;
    private final List<RemoteRepository> remoteRepositories;
    private final String classifier;
    private final Log log;

    public SkillResolver(RepositorySystem repoSystem,
                         RepositorySystemSession repoSession,
                         List<RemoteRepository> remoteRepositories,
                         String classifier,
                         Log log) {
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
        this.remoteRepositories = remoteRepositories;
        this.classifier = classifier;
        this.log = log;
    }

    /**
     * Attempts to resolve the skill JAR for a single dependency.
     *
     * @param groupId    dependency groupId
     * @param artifactId dependency artifactId
     * @param version    dependency version
     * @return a SkillBundle if a skill JAR exists, or null if not found
     */
    public SkillBundle resolveSkill(String groupId, String artifactId, String version) {
        Artifact skillArtifact = new DefaultArtifact(groupId, artifactId, classifier, "jar", version);
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(skillArtifact);
        request.setRepositories(remoteRepositories);

        try {
            ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
            File jarFile = result.getArtifact().getFile();

            if (jarFile == null || !jarFile.isFile()) {
                log.info("No skill JAR found for " + groupId + ":" + artifactId + ":" + version);
                return null;
            }

            return buildSkillBundle(groupId, artifactId, version, jarFile);

        } catch (ArtifactResolutionException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Could not find artifact") || msg.contains("not found"))) {
                log.info("No skill JAR available for " + groupId + ":" + artifactId + ":" + version);
            } else {
                log.warn("Failed to resolve skill JAR for " + groupId + ":" + artifactId + ":" + version
                        + ": " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Resolves skills for a list of dependencies.
     *
     * @param dependencies list of dependencies as [groupId, artifactId, version] arrays
     * @param includes     optional include patterns (GAV patterns, null = include all)
     * @param excludes     optional exclude patterns (GAV patterns, null = exclude none)
     * @return list of resolved skill bundles
     */
    public List<SkillBundle> resolveSkills(List<String[]> dependencies, List<String> includes, List<String> excludes) {
        List<SkillBundle> bundles = new ArrayList<>();

        for (String[] dep : dependencies) {
            String groupId = dep[0];
            String artifactId = dep[1];
            String version = dep[2];
            String gav = groupId + ":" + artifactId + ":" + version;

            if (!matchesFilters(gav, includes, excludes)) {
                log.debug("Skipping " + gav + " (filtered out)");
                continue;
            }

            SkillBundle bundle = resolveSkill(groupId, artifactId, version);
            if (bundle != null) {
                bundles.add(bundle);
            }
        }

        return bundles;
    }

    /**
     * Builds a SkillBundle from a resolved skill JAR.
     */
    private SkillBundle buildSkillBundle(String groupId, String artifactId, String version, File jarFile) {
        SkillBundle.Builder builder = SkillBundle.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .jarFile(jarFile);

        try (JarFile jf = new JarFile(jarFile)) {
            // Read SKILL.md from jar
            JarEntry skillMdEntry = jf.getJarEntry(JarUtils.SKILL_PREFIX + "SKILL.md");
            if (skillMdEntry != null) {
                try (InputStream is = jf.getInputStream(skillMdEntry)) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    FrontmatterParser parser = new FrontmatterParser();
                    FrontmatterParser.ParseResult parsed = parser.parse(content);

                    if (parsed.hasFrontmatter()) {
                        builder.frontmatter(parsed.getFrontmatter());
                        builder.skillName(parsed.getString("name"));
                        builder.description(parsed.getString("description"));
                    }
                }
            }

            // Count references and examples
            int refCount = 0;
            int exampleCount = 0;
            for (JarEntry entry : Collections.list(jf.entries())) {
                String name = entry.getName();
                if (name.startsWith(JarUtils.SKILL_PREFIX + "references/") && !entry.isDirectory()) {
                    refCount++;
                }
                if (name.startsWith(JarUtils.SKILL_PREFIX + "assets/examples/") && entry.isDirectory()
                        && !name.equals(JarUtils.SKILL_PREFIX + "assets/examples/")) {
                    exampleCount++;
                }
            }
            builder.referenceCount(refCount);
            builder.exampleCount(exampleCount);

        } catch (IOException e) {
            log.warn("Could not read skill JAR contents for " + groupId + ":" + artifactId + ":" + version
                    + ": " + e.getMessage());
            builder.skillName(artifactId);
        }

        return builder.build();
    }

    /**
     * Checks whether a GAV matches include/exclude filters.
     */
    static boolean matchesFilters(String gav, List<String> includes, List<String> excludes) {
        if (includes != null && !includes.isEmpty()) {
            boolean matched = false;
            for (String include : includes) {
                if (matchesPattern(gav, include)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }

        if (excludes != null && !excludes.isEmpty()) {
            for (String exclude : excludes) {
                if (matchesPattern(gav, exclude)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Matches a GAV string against a simple wildcard pattern.
     * Supports {@code *} as a wildcard.
     */
    static boolean matchesPattern(String gav, String pattern) {
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
        return Pattern.matches(regex, gav);
    }
}
