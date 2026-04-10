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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Properties;

/**
 * Packages a skill folder into a {@code *-skills.jar} and attaches it as a
 * secondary artifact to the current Maven build.
 */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE)
public class PackageSkillMojo extends AbstractMojo {

    /** Default classifier for skill JAR artifacts. */
    public static final String DEFAULT_CLASSIFIER = "skills";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Path to the skill directory to package.
     * If not set, auto-detects using convention: {@code ${basedir}/skills/${artifactId}/}
     * then falls back to {@code ${basedir}/skills/}.
     */
    @Parameter(property = "skillDirectory")
    private File skillDirectory;

    /**
     * Classifier for the skill JAR artifact.
     */
    @Parameter(property = "classifier", defaultValue = DEFAULT_CLASSIFIER)
    private String classifier;

    /**
     * Whether to skip (rather than fail) if the skill directory doesn't exist.
     */
    @Parameter(property = "skipIfMissing", defaultValue = "true")
    private boolean skipIfMissing;

    /**
     * Whether to fail the build on validation errors.
     */
    @Parameter(property = "failOnValidationError", defaultValue = "true")
    private boolean failOnValidationError;

    /**
     * Whether to include library metadata (groupId, artifactId, version) in the JAR.
     */
    @Parameter(property = "includeLibraryMetadata", defaultValue = "true")
    private boolean includeLibraryMetadata;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File skillDir = resolveSkillDirectory();

        if (skillDir == null) {
            if (skipIfMissing) {
                getLog().info("No skill directory found — skipping packaging.");
                getLog().info("Looked in: " + getConventionDir().getAbsolutePath()
                        + " and " + getFallbackDir().getAbsolutePath());
                return;
            } else {
                throw new MojoFailureException("No skill directory found. Looked in: "
                        + getConventionDir().getAbsolutePath() + " and " + getFallbackDir().getAbsolutePath());
            }
        }

        getLog().info("Packaging skill directory: " + skillDir.getAbsolutePath());

        // Validate
        SkillValidator validator = new SkillValidator();
        SkillValidator.ValidationResult validation = validator.validate(skillDir);

        for (String warning : validation.getWarnings()) {
            getLog().warn(warning);
        }

        if (validation.hasErrors()) {
            for (String error : validation.getErrors()) {
                getLog().error(error);
            }
            if (failOnValidationError) {
                throw new MojoFailureException("Skill validation failed with "
                        + validation.getErrors().size() + " error(s). Fix the errors or set failOnValidationError=false.");
            } else {
                getLog().warn("Continuing despite " + validation.getErrors().size() + " validation error(s).");
            }
        }

        // Build metadata
        Properties metadata = null;
        if (includeLibraryMetadata) {
            metadata = new Properties();
            metadata.setProperty("groupId", project.getGroupId());
            metadata.setProperty("artifactId", project.getArtifactId());
            metadata.setProperty("version", project.getVersion());
            metadata.setProperty("packagedAt", Instant.now().toString());
        }

        // Create JAR
        String jarName = project.getArtifactId() + "-" + project.getVersion() + "-" + classifier + ".jar";
        File outputJar = new File(project.getBuild().getDirectory(), jarName);

        try {
            JarUtils.createSkillJar(skillDir, outputJar, metadata);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create skill JAR: " + e.getMessage(), e);
        }

        getLog().info("Created skill JAR: " + outputJar.getAbsolutePath());

        // Attach as secondary artifact
        projectHelper.attachArtifact(project, "jar", classifier, outputJar);
        getLog().info("Attached skill JAR with classifier '" + classifier + "'.");
    }

    /**
     * Resolves the skill directory using the convention chain:
     * 1. Explicit {@code skillDirectory} parameter
     * 2. {@code ${basedir}/skills/${artifactId}/}
     * 3. Scan {@code ${basedir}/skills/} for a single subdirectory containing SKILL.md
     * 4. {@code ${basedir}/skills/} itself
     * 5. {@code null} if none found
     */
    File resolveSkillDirectory() {
        if (skillDirectory != null && skillDirectory.isDirectory()) {
            return skillDirectory;
        }

        File conventionDir = getConventionDir();
        if (conventionDir.isDirectory()) {
            return conventionDir;
        }

        File fallbackDir = getFallbackDir();
        if (fallbackDir.isDirectory()) {
            File scanned = scanForSingleSkillSubdirectory(fallbackDir);
            if (scanned != null) {
                return scanned;
            }
            return fallbackDir;
        }

        return null;
    }

    /**
     * Scans a directory for a single subdirectory that contains a SKILL.md file.
     * Returns that subdirectory if exactly one is found, otherwise {@code null}.
     */
    static File scanForSingleSkillSubdirectory(File parent) {
        File[] children = parent.listFiles();
        if (children == null) {
            return null;
        }
        File match = null;
        for (File child : children) {
            if (child.isDirectory() && new File(child, "SKILL.md").isFile()) {
                if (match != null) {
                    // More than one subdirectory with SKILL.md — ambiguous
                    return null;
                }
                match = child;
            }
        }
        return match;
    }

    private File getConventionDir() {
        return new File(project.getBasedir(), "skills/" + project.getArtifactId());
    }

    private File getFallbackDir() {
        return new File(project.getBasedir(), "skills");
    }
}
