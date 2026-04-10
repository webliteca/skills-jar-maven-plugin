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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lists which dependencies have skill bundles available, without installing anything.
 * Supports human-readable (text) and machine-readable (json) output formats.
 */
@Mojo(name = "list", requiresDependencyResolution = ResolutionScope.RUNTIME, requiresProject = true)
public class ListSkillsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> remoteRepositories;

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
     * GAV patterns to include (comma-separated).
     */
    @Parameter(property = "includes")
    private String includes;

    /**
     * GAV patterns to exclude (comma-separated).
     */
    @Parameter(property = "excludes")
    private String excludes;

    /**
     * Output format: "text" (default) or "json".
     */
    @Parameter(property = "format", defaultValue = "text")
    private String format;

    /**
     * Classifier to look for when resolving skill JARs.
     */
    @Parameter(property = "classifier", defaultValue = PackageSkillMojo.DEFAULT_CLASSIFIER)
    private String classifier;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<String> allowedScopes = new HashSet<>(Arrays.asList(scope.split("\\s*,\\s*")));
        List<String[]> dependencies = collectDependencies(allowedScopes);

        getLog().info("Scanning " + dependencies.size() + " dependencies for skill bundles...");

        List<String> includePatterns = parsePatterns(includes);
        List<String> excludePatterns = parsePatterns(excludes);

        SkillResolver resolver = new SkillResolver(repoSystem, repoSession, remoteRepositories, classifier, getLog());
        List<SkillBundle> bundles = resolver.resolveSkills(dependencies, includePatterns, excludePatterns);

        // Determine which deps had no skill
        Set<String> gavWithSkill = new HashSet<>();
        for (SkillBundle b : bundles) {
            gavWithSkill.add(b.getGav());
        }
        List<String> depsWithoutSkill = new ArrayList<>();
        for (String[] dep : dependencies) {
            String gav = dep[0] + ":" + dep[1] + ":" + dep[2];
            if (!gavWithSkill.contains(gav)) {
                depsWithoutSkill.add(gav);
            }
        }

        if ("json".equalsIgnoreCase(format)) {
            printJsonOutput(bundles, depsWithoutSkill);
        } else {
            printTextOutput(bundles, depsWithoutSkill);
        }
    }

    private void printTextOutput(List<SkillBundle> bundles, List<String> depsWithoutSkill) {
        if (bundles.isEmpty()) {
            getLog().info("No dependencies have skill bundles.");
        } else {
            getLog().info("");
            getLog().info("Dependencies with skills:");
            for (SkillBundle bundle : bundles) {
                StringBuilder sb = new StringBuilder("  ");
                sb.append(bundle.getGav());
                if (bundle.getSkillName() != null) {
                    sb.append(" -> ").append(bundle.getSkillName());
                }
                sb.append(" (SKILL.md");
                if (bundle.getReferenceCount() > 0) {
                    sb.append(", ").append(bundle.getReferenceCount()).append(" references");
                }
                if (bundle.getExampleCount() > 0) {
                    sb.append(", ").append(bundle.getExampleCount()).append(" examples");
                }
                sb.append(")");
                getLog().info(sb.toString());
            }
        }

        if (!depsWithoutSkill.isEmpty()) {
            getLog().info("");
            getLog().info("Dependencies without skills (" + depsWithoutSkill.size() + "): "
                    + String.join(", ", depsWithoutSkill));
        }
    }

    private void printJsonOutput(List<SkillBundle> bundles, List<String> depsWithoutSkill) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"projectGav\": \"").append(escape(project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion())).append("\",\n");

        // Dependencies with skills
        json.append("  \"dependenciesWithSkills\": [\n");
        for (int i = 0; i < bundles.size(); i++) {
            SkillBundle bundle = bundles.get(i);
            json.append("    {\n");
            json.append("      \"gav\": \"").append(escape(bundle.getGav())).append("\",\n");
            json.append("      \"skillName\": \"").append(escape(bundle.getSkillName())).append("\",\n");
            json.append("      \"summary\": {");
            json.append("\"skillMd\": true");
            json.append(", \"referenceCount\": ").append(bundle.getReferenceCount());
            json.append(", \"exampleCount\": ").append(bundle.getExampleCount());
            json.append("}\n");
            json.append("    }");
            if (i < bundles.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ],\n");

        // Dependencies without skills
        json.append("  \"dependenciesWithoutSkills\": [");
        for (int i = 0; i < depsWithoutSkill.size(); i++) {
            if (i > 0) json.append(", ");
            json.append("\"").append(escape(depsWithoutSkill.get(i))).append("\"");
        }
        json.append("]\n");

        json.append("}\n");

        // Print to stdout for machine consumption
        System.out.println(json);
    }

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

    private static String escape(String s) {
        if (s == null) return "null";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
