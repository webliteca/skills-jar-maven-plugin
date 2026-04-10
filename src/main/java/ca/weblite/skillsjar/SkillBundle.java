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

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Model representing a skill bundle — its metadata and file structure.
 */
public class SkillBundle {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String skillName;
    private final String description;
    private final File jarFile;
    private final Map<String, Object> frontmatter;
    private final int referenceCount;
    private final int exampleCount;

    private SkillBundle(Builder builder) {
        this.groupId = builder.groupId;
        this.artifactId = builder.artifactId;
        this.version = builder.version;
        this.skillName = builder.skillName;
        this.description = builder.description;
        this.jarFile = builder.jarFile;
        this.frontmatter = builder.frontmatter != null
                ? Collections.unmodifiableMap(builder.frontmatter)
                : Collections.emptyMap();
        this.referenceCount = builder.referenceCount;
        this.exampleCount = builder.exampleCount;
    }

    public String getGroupId() { return groupId; }
    public String getArtifactId() { return artifactId; }
    public String getVersion() { return version; }
    public String getSkillName() { return skillName; }
    public String getDescription() { return description; }
    public File getJarFile() { return jarFile; }
    public Map<String, Object> getFrontmatter() { return frontmatter; }
    public int getReferenceCount() { return referenceCount; }
    public int getExampleCount() { return exampleCount; }

    /**
     * Returns the GAV coordinate string.
     */
    public String getGav() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String groupId;
        private String artifactId;
        private String version;
        private String skillName;
        private String description;
        private File jarFile;
        private Map<String, Object> frontmatter;
        private int referenceCount;
        private int exampleCount;

        public Builder groupId(String groupId) { this.groupId = groupId; return this; }
        public Builder artifactId(String artifactId) { this.artifactId = artifactId; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder skillName(String skillName) { this.skillName = skillName; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder jarFile(File jarFile) { this.jarFile = jarFile; return this; }
        public Builder frontmatter(Map<String, Object> frontmatter) { this.frontmatter = frontmatter; return this; }
        public Builder referenceCount(int referenceCount) { this.referenceCount = referenceCount; return this; }
        public Builder exampleCount(int exampleCount) { this.exampleCount = exampleCount; return this; }

        public SkillBundle build() {
            return new SkillBundle(this);
        }
    }
}
