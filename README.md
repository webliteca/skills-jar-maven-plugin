# skills-jar-plugin

A Maven plugin that ships AI assistant skill bundles as secondary Maven artifacts — just like `-sources.jar` and `-javadoc.jar`, but for AI coding tools.

When a library publishes `foo-1.0-skills.jar`, any project that depends on `foo` can automatically fetch version-pinned, signed, transitive skill guidance for that library. No new registries, no new standards — just a Maven classifier convention that works today.

## For library authors

### 1. Add a skill folder

Create a `skills/<artifactId>/` directory in your project:

```
my-library/
├── pom.xml
├── src/
└── skills/
    └── my-library/
        ├── SKILL.md
        ├── references/
        │   └── api.md
        └── assets/
            └── examples/
```

The `SKILL.md` file requires YAML frontmatter:

```markdown
---
name: my-library
description: Guidance for using the my-library API, including configuration patterns and common pitfalls.
---

# my-library

Instructions for AI assistants working with this library...
```

### 2. Add the plugin to your build

```xml
<plugin>
    <groupId>ca.weblite</groupId>
    <artifactId>skills-jar-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>skills-jar</id>
            <phase>package</phase>
            <goals><goal>package</goal></goals>
        </execution>
    </executions>
</plugin>
```

Run `mvn install` and you'll get `my-library-1.0.0-skills.jar` installed alongside your main JAR. When you deploy to Maven Central, the skills JAR goes too.

### Directory auto-detection

The plugin looks for skill content in this order:
1. `${basedir}/skills/${artifactId}/` (convention)
2. `${basedir}/skills/` (fallback for single-skill repos)
3. If neither exists, the plugin skips silently (configurable via `skipIfMissing`).

## For developers consuming libraries

Run this in any Maven project to fetch skills for all dependencies:

```bash
mvn ca.weblite:skills-jar-plugin:install
```

This walks your dependency tree, downloads any available `*-skills.jar` artifacts, and unpacks them into `.claude/skills/`. AI tools that read from that directory (Claude Code, Cursor, etc.) will pick them up automatically.

To see what's available without installing:

```bash
mvn ca.weblite:skills-jar-plugin:list
```

### Gradle consumers

Gradle can resolve classifier artifacts from Maven repositories:

```groovy
dependencies {
    // Your normal dependency
    implementation 'com.example:my-library:1.0.0'
}

// Resolve the skills JAR
configurations {
    skills
}
dependencies {
    skills 'com.example:my-library:1.0.0:skills'
}
```

## For AI coding assistants

If you are an AI coding assistant working in a Maven project and want access to library-specific skills, run the following command (with user permission):

```bash
mvn ca.weblite:skills-jar-plugin:install
```

This fetches all available skills for the project's dependencies and installs them to `.claude/skills/`. Load these skills on your next session. No changes are made to `pom.xml` — this is a local workstation setup step.

## Goals reference

### `package`

Packages a skill folder into a `-skills.jar` and attaches it as a secondary artifact.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `skillDirectory` | auto-detect | Path to the skill folder |
| `classifier` | `skills` | Classifier for the attached artifact |
| `skipIfMissing` | `true` | Skip silently if no skill directory found |
| `failOnValidationError` | `true` | Fail the build on validation errors |
| `includeLibraryMetadata` | `true` | Include `skill-library.properties` in the JAR |

### `install`

Resolves skill JARs from the dependency tree and unpacks them locally.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `installDirectory` | `${basedir}/.claude/skills` | Where to unpack skills |
| `transitive` | `true` | Walk transitive dependencies |
| `scope` | `compile,runtime` | Dependency scopes to consider |
| `includes` | | GAV patterns to include (comma-separated) |
| `excludes` | | GAV patterns to exclude (comma-separated) |
| `overwrite` | `true` | Overwrite existing skills |
| `dryRun` | `false` | Report without writing |
| `classifier` | `skills` | Classifier to look for |

### `list`

Reports which dependencies have skill bundles, without installing anything.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `format` | `text` | Output format: `text` or `json` |
| `transitive` | `true` | Walk transitive dependencies |
| `scope` | `compile,runtime` | Dependency scopes to consider |
| `includes` | | GAV patterns to include |
| `excludes` | | GAV patterns to exclude |

### `verify`

Validates a skill folder against the format spec.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `skillDirectory` | auto-detect | Path to the skill folder |
| `failOnValidationError` | `true` | Fail on errors vs. warn |

```bash
mvn ca.weblite:skills-jar-plugin:verify
```

## Skill format

Inside the JAR, skill content lives under a `skill/` prefix:

```
*-skills.jar
├── META-INF/
│   ├── MANIFEST.MF
│   └── skill-library.properties   (groupId, artifactId, version, packagedAt)
└── skill/
    ├── SKILL.md                   (required: YAML frontmatter + body)
    ├── references/                (optional: detailed docs)
    ├── assets/                    (optional: images, examples)
    └── scripts/                   (optional: helper scripts)
```

The `SKILL.md` frontmatter requires:
- `name` — lowercase, alphanumeric + hyphens, 3-64 chars
- `description` — non-empty, under 1024 chars

See the [Anthropic Agent Skills format](https://docs.anthropic.com) for the authoritative specification.

## FAQ

**Why not a new standard?**
This uses Maven's existing classifier mechanism — the same one used for sources and javadoc JARs since Maven 2. No standards body, no new registry, no new protocol. Just a convention.

**What about Gradle?**
Gradle can consume Maven classifier artifacts natively. The `install` goal is Maven-specific, but the JAR format is universal. A Gradle plugin equivalent would be straightforward.

**What if my library targets multiple AI tools?**
`SKILL.md` follows the Anthropic Agent Skills format. If other tools adopt different formats, tool-specific variants can live in subdirectories. For now, most AI coding tools can consume Markdown-based skills.

**Is this secure?**
A skills JAR inherits the same trust model as the library itself: if you trust a GAV enough to put it in your `pom.xml`, you trust its skill. Maven Central signing and checksums protect skills the same way they protect JARs. The `install` goal includes zip-slip protection and size limits.

## Roadmap / non-goals

**In scope for future versions:**
- `update` goal to refresh skills from newer dependency versions
- IDE integration (auto-install on project import)
- Skills catalog / search

**Explicitly not in scope:**
- npm, Cargo, PyPI, or other package manager support (this is Maven-only)
- A central skill registry separate from Maven Central
- Skill format standardization across AI tools (we follow Anthropic's spec)

## License

Apache License 2.0 — see [LICENSE](LICENSE).
