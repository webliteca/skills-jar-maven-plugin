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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks the current project's dependency tree, resolves skill JARs for each dependency,
 * and unpacks them into a local skills directory for AI tools to discover.
 */
@Mojo(name = "install", requiresDependencyResolution = ResolutionScope.RUNTIME, requiresProject = true)
public class InstallSkillsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> remoteRepositories;

    /**
     * Directory where skills will be installed.
     */
    @Parameter(property = "installDirectory", defaultValue = "${project.basedir}/.claude/skills")
    private File installDirectory;

    /**
     * Whether to resolve transitive dependencies or just direct ones.
     */
    @Parameter(property = "transitive", defaultValue = "true")
    private boolean transitive;

    /**
     * Dependency scopes to consider (comma-separated).
     */
    @Parameter(property = "scope", defaultValue = "compile,runtime")
    private String scope;

    /**
     * GAV patterns to include (comma-separated). If empty, all dependencies are included.
     */
    @Parameter(property = "includes")
    private String includes;

    /**
     * GAV patterns to exclude (comma-separated).
     */
    @Parameter(property = "excludes")
    private String excludes;

    /**
     * Whether to overwrite existing installed skills.
     */
    @Parameter(property = "overwrite", defaultValue = "true")
    private boolean overwrite;

    /**
     * If true, resolve and report what would be installed without writing anything.
     */
    @Parameter(property = "dryRun", defaultValue = "false")
    private boolean dryRun;

    /**
     * Classifier to look for when resolving skill JARs.
     */
    @Parameter(property = "classifier", defaultValue = PackageSkillMojo.DEFAULT_CLASSIFIER)
    private String classifier;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<String> allowedScopes = new HashSet<>(Arrays.asList(scope.split("\\s*,\\s*")));

        // Collect dependencies
        List<String[]> dependencies = collectDependencies(allowedScopes);
        getLog().info("Scanning " + dependencies.size() + " dependencies for skill bundles...");

        // Parse include/exclude filters
        List<String> includePatterns = parsePatterns(includes);
        List<String> excludePatterns = parsePatterns(excludes);

        // Resolve skills
        SkillResolver resolver = new SkillResolver(repoSystem, repoSession, remoteRepositories, classifier, getLog());
        List<SkillBundle> bundles = resolver.resolveSkills(dependencies, includePatterns, excludePatterns);

        int noSkillCount = dependencies.size() - bundles.size();

        if (bundles.isEmpty()) {
            getLog().info("No skill bundles found among " + dependencies.size() + " dependencies.");
            return;
        }

        if (dryRun) {
            getLog().info("[DRY RUN] Would install " + bundles.size() + " skill(s):");
            for (SkillBundle bundle : bundles) {
                getLog().info("  " + bundle.getGav() + " -> " + bundle.getSkillName());
            }
            return;
        }

        // Install
        try {
            SkillInstaller installer = new SkillInstaller(installDirectory, overwrite, getLog());
            int installed = installer.installAll(bundles);
            getLog().info("Installed " + installed + " skill(s) from " + bundles.size()
                    + " dependencies (" + noSkillCount + " dependencies had no skill).");
            getLog().info("Skills directory: " + installDirectory.getAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to install skills: " + e.getMessage(), e);
        }
    }

    /**
     * Collects dependencies from the project's resolved artifacts.
     */
    private List<String[]> collectDependencies(Set<String> allowedScopes) {
        List<String[]> deps = new ArrayList<>();
        Set<Artifact> artifacts;

        if (transitive) {
            artifacts = project.getArtifacts();
        } else {
            artifacts = project.getDependencyArtifacts();
        }

        if (artifacts == null) {
            return deps;
        }

        for (Artifact artifact : artifacts) {
            if (allowedScopes.contains(artifact.getScope())) {
                deps.add(new String[]{
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        artifact.getVersion()
                });
            }
        }

        return deps;
    }

    private List<String> parsePatterns(String patterns) {
        if (patterns == null || patterns.trim().isEmpty()) {
            return null;
        }
        return Arrays.asList(patterns.split("\\s*,\\s*"));
    }
}
