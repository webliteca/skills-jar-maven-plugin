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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontmatterParserTest {

    private FrontmatterParser parser;

    @BeforeEach
    void setUp() {
        parser = new FrontmatterParser();
    }

    @Test
    void parsesValidFrontmatter() {
        String content = "---\nname: my-skill\ndescription: A test skill\n---\n\n# Body\n\nSome content.\n";
        FrontmatterParser.ParseResult result = parser.parse(content);

        assertThat(result.hasFrontmatter()).isTrue();
        assertThat(result.getString("name")).isEqualTo("my-skill");
        assertThat(result.getString("description")).isEqualTo("A test skill");
        assertThat(result.getBody()).contains("# Body");
        assertThat(result.getBody()).contains("Some content.");
    }

    @Test
    void parsesEmptyFrontmatter() {
        String content = "---\n---\n\n# Body content\n";
        FrontmatterParser.ParseResult result = parser.parse(content);

        assertThat(result.hasFrontmatter()).isTrue();
        assertThat(result.getFrontmatter()).isEmpty();
        assertThat(result.getBody()).contains("# Body content");
    }

    @Test
    void parsesComplexFrontmatter() {
        String content = "---\nname: complex-skill\ndescription: A complex skill\nkeywords:\n  - java\n  - maven\nversion: \"1.0\"\n---\n\nBody here.\n";
        FrontmatterParser.ParseResult result = parser.parse(content);

        assertThat(result.hasFrontmatter()).isTrue();
        assertThat(result.getString("name")).isEqualTo("complex-skill");
        assertThat(result.getString("version")).isEqualTo("1.0");
        assertThat(result.getFrontmatter().get("keywords")).asList().containsExactly("java", "maven");
    }

    @Test
    void rejectsContentWithoutFrontmatter() {
        String content = "# Just a regular Markdown file\n\nNo frontmatter here.\n";
        FrontmatterParser.ParseResult result = parser.parse(content);

        assertThat(result.hasFrontmatter()).isFalse();
        assertThat(result.getFrontmatter()).isEmpty();
        assertThat(result.getBody()).isEqualTo(content);
    }

    @Test
    void rejectsMissingClosingDelimiter() {
        String content = "---\nname: broken\n# No closing delimiter\nBody content\n";
        FrontmatterParser.ParseResult result = parser.parse(content);

        assertThat(result.hasFrontmatter()).isFalse();
        assertThat(result.getBody()).isEqualTo(content);
    }

    @Test
    void handlesEmptyContent() {
        FrontmatterParser.ParseResult result = parser.parse("");

        assertThat(result.hasFrontmatter()).isFalse();
        assertThat(result.getFrontmatter()).isEmpty();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void handlesNullContent() {
        FrontmatterParser.ParseResult result = parser.parse((String) null);

        assertThat(result.hasFrontmatter()).isFalse();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void rejectsInvalidYaml() {
        String content = "---\n: invalid: yaml: [broken\n---\n\nBody\n";
        FrontmatterParser.ParseResult result = parser.parse(content);

        // Invalid YAML should result in no frontmatter
        assertThat(result.hasFrontmatter()).isFalse();
    }

    @Test
    void getStringReturnsNullForMissingKey() {
        String content = "---\nname: test-skill\n---\n\nBody\n";
        FrontmatterParser.ParseResult result = parser.parse(content);

        assertThat(result.getString("nonexistent")).isNull();
    }

    @Test
    void handlesBodyWithoutTrailingNewline() {
        String content = "---\nname: test\n---\nBody without trailing newline";
        FrontmatterParser.ParseResult result = parser.parse(content);

        assertThat(result.hasFrontmatter()).isTrue();
        assertThat(result.getBody()).contains("Body without trailing newline");
    }
}
