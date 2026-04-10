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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates a skill folder against the Agent Skills format specification.
 */
public class SkillValidator {

    /** Allowed entries at the root of a skill directory. */
    private static final Set<String> ALLOWED_ROOT_ENTRIES = new HashSet<>(Arrays.asList(
            "SKILL.md", "references", "assets", "scripts", "state", "LICENSE", "README.md"
    ));

    /** Pattern for valid skill names: lowercase, alphanumeric + hyphens, 3-64 chars. */
    private static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$");

    /** Default maximum skill directory size in bytes (10 MB). */
    private static final long DEFAULT_MAX_SIZE = 10L * 1024 * 1024;

    /** Warn threshold for skill directory size in bytes (5 MB). */
    private static final long WARN_SIZE_THRESHOLD = 5L * 1024 * 1024;

    /** Maximum recommended lines for SKILL.md body. */
    private static final int MAX_BODY_LINES = 500;

    /** Minimum recommended description length. */
    private static final int MIN_DESCRIPTION_LENGTH = 50;

    /** Maximum description length. */
    private static final int MAX_DESCRIPTION_LENGTH = 1024;

    /** Pattern to match Markdown relative links. */
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile(
            "\\[([^\\]]*)]\\(([^)]+)\\)"
    );

    private final FrontmatterParser frontmatterParser;

    public SkillValidator() {
        this.frontmatterParser = new FrontmatterParser();
    }

    public SkillValidator(FrontmatterParser frontmatterParser) {
        this.frontmatterParser = frontmatterParser;
    }

    /**
     * Validates a skill directory.
     *
     * @param skillDir the skill directory to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validate(File skillDir) {
        ValidationResult result = new ValidationResult();

        if (!skillDir.isDirectory()) {
            result.addError("Skill directory does not exist: " + skillDir.getAbsolutePath());
            return result;
        }

        // 1. Structure checks
        validateStructure(skillDir, result);

        // 2. SKILL.md checks
        File skillMd = new File(skillDir, "SKILL.md");
        if (skillMd.isFile()) {
            validateSkillMd(skillMd, result);
        }

        // 3. Size check
        validateSize(skillDir, result);

        // 4. Reference checks
        validateReferences(skillDir, skillMd, result);

        return result;
    }

    private void validateStructure(File skillDir, ValidationResult result) {
        File skillMd = new File(skillDir, "SKILL.md");
        if (!skillMd.isFile()) {
            result.addError("SKILL.md not found at: " + skillDir.getAbsolutePath());
            return;
        }

        // Check for unexpected root entries
        File[] rootFiles = skillDir.listFiles();
        if (rootFiles != null) {
            for (File f : rootFiles) {
                if (!ALLOWED_ROOT_ENTRIES.contains(f.getName())) {
                    result.addWarning("Unexpected entry at skill root: " + f.getName()
                            + " (allowed: " + String.join(", ", ALLOWED_ROOT_ENTRIES) + ")");
                }
            }
        }
    }

    private void validateSkillMd(File skillMd, ValidationResult result) {
        try {
            FrontmatterParser.ParseResult parsed = frontmatterParser.parse(skillMd.toPath());

            // Frontmatter presence
            if (!parsed.hasFrontmatter()) {
                result.addError("SKILL.md is missing YAML frontmatter (expected --- delimiters at the top of the file)");
                return;
            }

            // Required fields
            String name = parsed.getString("name");
            if (name == null || name.trim().isEmpty()) {
                result.addError("Frontmatter is missing required field: name");
            } else {
                validateSkillName(name, result);
            }

            String description = parsed.getString("description");
            if (description == null || description.trim().isEmpty()) {
                result.addError("Frontmatter is missing required field: description");
            } else {
                if (description.length() > MAX_DESCRIPTION_LENGTH) {
                    result.addError("Frontmatter 'description' exceeds " + MAX_DESCRIPTION_LENGTH + " characters ("
                            + description.length() + " chars)");
                } else if (description.length() < MIN_DESCRIPTION_LENGTH) {
                    result.addWarning("Frontmatter 'description' is very short (" + description.length()
                            + " chars) — consider being more specific so AI tools can match it reliably");
                }
            }

            // Body checks
            String body = parsed.getBody();
            if (body == null || body.trim().isEmpty()) {
                result.addError("SKILL.md body is empty (the content after frontmatter must not be empty)");
            } else {
                long lineCount = body.lines().count();
                if (lineCount > MAX_BODY_LINES) {
                    result.addWarning("SKILL.md body is " + lineCount + " lines (recommended: <" + MAX_BODY_LINES
                            + "). Consider moving detailed content to references/");
                }
            }
        } catch (IOException e) {
            result.addError("Failed to read SKILL.md: " + e.getMessage());
        }
    }

    private void validateSkillName(String name, ValidationResult result) {
        if (!SKILL_NAME_PATTERN.matcher(name).matches()) {
            StringBuilder msg = new StringBuilder("Invalid skill name: '").append(name).append("'. ");
            if (name.length() < 3) {
                msg.append("Name must be at least 3 characters.");
            } else if (name.length() > 64) {
                msg.append("Name must be at most 64 characters.");
            } else if (!name.equals(name.toLowerCase())) {
                msg.append("Name must be lowercase.");
            } else {
                msg.append("Name must contain only lowercase letters, digits, and hyphens, and must start/end with a letter or digit.");
            }
            result.addError(msg.toString());
        }
    }

    private void validateSize(File skillDir, ValidationResult result) {
        try {
            AtomicLong totalSize = new AtomicLong(0);
            Files.walkFileTree(skillDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    totalSize.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }
            });

            long size = totalSize.get();
            if (size > DEFAULT_MAX_SIZE) {
                result.addError("Skill directory size (" + formatSize(size) + ") exceeds maximum of "
                        + formatSize(DEFAULT_MAX_SIZE));
            } else if (size > WARN_SIZE_THRESHOLD) {
                result.addWarning("Skill directory size (" + formatSize(size) + ") exceeds recommended threshold of "
                        + formatSize(WARN_SIZE_THRESHOLD));
            }
        } catch (IOException e) {
            result.addWarning("Could not calculate skill directory size: " + e.getMessage());
        }
    }

    private void validateReferences(File skillDir, File skillMd, ValidationResult result) {
        File referencesDir = new File(skillDir, "references");
        if (!referencesDir.isDirectory()) {
            return;
        }

        if (!skillMd.isFile()) {
            return;
        }

        try {
            String content = new String(Files.readAllBytes(skillMd.toPath()), StandardCharsets.UTF_8);

            // Find all relative links in SKILL.md
            Set<String> linkedPaths = new HashSet<>();
            Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(content);
            while (matcher.find()) {
                String link = matcher.group(2);
                // Only consider relative links (not http/https)
                if (!link.startsWith("http://") && !link.startsWith("https://") && !link.startsWith("#")) {
                    linkedPaths.add(link);
                }
            }

            // Check that all linked files exist
            for (String link : linkedPaths) {
                File target = new File(skillDir, link);
                if (!target.exists()) {
                    result.addWarning("Broken reference in SKILL.md: " + link + " (file not found)");
                }
            }

            // Check for orphan files in references/
            Set<String> referenceFiles = new HashSet<>();
            Files.walkFileTree(referencesDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String rel = skillDir.toPath().relativize(file).toString().replace(File.separatorChar, '/');
                    referenceFiles.add(rel);
                    return FileVisitResult.CONTINUE;
                }
            });

            for (String refFile : referenceFiles) {
                if (!linkedPaths.contains(refFile)) {
                    result.addWarning("Orphan reference file not linked from SKILL.md: " + refFile);
                }
            }
        } catch (IOException e) {
            result.addWarning("Could not validate references: " + e.getMessage());
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Holds the results of a skill validation: errors and warnings.
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String message) {
            errors.add(message);
        }

        public void addWarning(String message) {
            warnings.add(message);
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean isValid() {
            return !hasErrors();
        }
    }
}
