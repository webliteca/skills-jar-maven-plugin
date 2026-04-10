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

import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses YAML frontmatter from SKILL.md files.
 * Frontmatter is delimited by {@code ---} on the first line and a closing {@code ---}.
 */
public class FrontmatterParser {

    private static final String DELIMITER = "---";

    /**
     * Result of parsing a file with optional YAML frontmatter.
     */
    public static class ParseResult {
        private final Map<String, Object> frontmatter;
        private final String body;
        private final boolean hasFrontmatter;

        public ParseResult(Map<String, Object> frontmatter, String body, boolean hasFrontmatter) {
            this.frontmatter = frontmatter != null
                    ? Collections.unmodifiableMap(new LinkedHashMap<>(frontmatter))
                    : Collections.emptyMap();
            this.body = body;
            this.hasFrontmatter = hasFrontmatter;
        }

        public Map<String, Object> getFrontmatter() {
            return frontmatter;
        }

        public String getBody() {
            return body;
        }

        public boolean hasFrontmatter() {
            return hasFrontmatter;
        }

        /**
         * Returns the value of a frontmatter field as a String, or null if absent.
         */
        public String getString(String key) {
            Object val = frontmatter.get(key);
            return val != null ? val.toString() : null;
        }
    }

    /**
     * Parses the given file for YAML frontmatter.
     */
    public ParseResult parse(Path file) throws IOException {
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        return parse(content);
    }

    /**
     * Parses the given string content for YAML frontmatter.
     */
    public ParseResult parse(String content) {
        if (content == null || content.isEmpty()) {
            return new ParseResult(null, "", false);
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.trim().equals(DELIMITER)) {
                // No frontmatter — entire content is the body
                return new ParseResult(null, content, false);
            }

            // Read until closing delimiter
            StringBuilder yamlBuilder = new StringBuilder();
            StringBuilder bodyBuilder = new StringBuilder();
            boolean foundClosing = false;
            String line;
            int lineNumber = 1; // first line was the opening delimiter

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().equals(DELIMITER)) {
                    foundClosing = true;
                    break;
                }
                yamlBuilder.append(line).append('\n');
            }

            if (!foundClosing) {
                // Opening delimiter but no closing — treat entire content as body (no frontmatter)
                return new ParseResult(null, content, false);
            }

            // Everything after the closing delimiter is the body
            while ((line = reader.readLine()) != null) {
                bodyBuilder.append(line).append('\n');
            }

            // Parse the YAML
            String yamlContent = yamlBuilder.toString().trim();
            Map<String, Object> frontmatter;
            if (yamlContent.isEmpty()) {
                frontmatter = Collections.emptyMap();
            } else {
                Yaml yaml = new Yaml();
                Object parsed = yaml.load(yamlContent);
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) parsed;
                    frontmatter = map;
                } else {
                    // YAML parsed to something other than a map — treat as invalid
                    return new ParseResult(null, content, false);
                }
            }

            String body = bodyBuilder.toString();
            // Remove leading newline if body starts with one
            if (body.startsWith("\n")) {
                body = body.substring(1);
            }

            return new ParseResult(frontmatter, body, true);

        } catch (IOException e) {
            // Should not happen with StringReader
            throw new RuntimeException("Unexpected IOException parsing string content", e);
        } catch (Exception e) {
            // YAML parse error — treat as no valid frontmatter
            return new ParseResult(null, content, false);
        }
    }
}
