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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Validates a skill folder against the Agent Skills format specification.
 * Can be used standalone or as a CI quality gate.
 */
@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY)
public class VerifySkillMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Path to the skill directory to validate.
     * If not set, auto-detects using convention: {@code ${basedir}/skills/${artifactId}/}
     * then falls back to {@code ${basedir}/skills/}.
     */
    @Parameter(property = "skillDirectory")
    private File skillDirectory;

    /**
     * Whether to fail the build on validation errors.
     */
    @Parameter(property = "failOnValidationError", defaultValue = "true")
    private boolean failOnValidationError;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File skillDir = resolveSkillDirectory();

        if (skillDir == null) {
            getLog().info("No skill directory found — skipping verification.");
            getLog().info("Looked in: " + getConventionDir().getAbsolutePath()
                    + " and " + getFallbackDir().getAbsolutePath());
            return;
        }

        getLog().info("Validating skill directory: " + skillDir.getAbsolutePath());

        SkillValidator validator = new SkillValidator();
        SkillValidator.ValidationResult result = validator.validate(skillDir);

        // Report warnings
        for (String warning : result.getWarnings()) {
            getLog().warn(warning);
        }

        // Report errors
        for (String error : result.getErrors()) {
            getLog().error(error);
        }

        if (result.isValid()) {
            getLog().info("Skill validation passed"
                    + (result.hasWarnings() ? " with " + result.getWarnings().size() + " warning(s)" : "")
                    + ".");
        } else {
            String msg = "Skill validation failed with " + result.getErrors().size() + " error(s).";
            if (failOnValidationError) {
                throw new MojoFailureException(msg);
            } else {
                getLog().warn(msg);
            }
        }
    }

    /**
     * Resolves the skill directory using the convention chain:
     * 1. Explicit {@code skillDirectory} parameter
     * 2. {@code ${basedir}/skills/${artifactId}/}
     * 3. {@code ${basedir}/skills/}
     * 4. {@code null} if none found
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
            return fallbackDir;
        }

        return null;
    }

    private File getConventionDir() {
        return new File(project.getBasedir(), "skills/" + project.getArtifactId());
    }

    private File getFallbackDir() {
        return new File(project.getBasedir(), "skills");
    }
}
