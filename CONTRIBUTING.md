# Contributing to skills-jar-plugin

## Prerequisites

- JDK 11 or later
- Maven 3.6.3 or later

## Building

```bash
mvn clean verify
```

This runs unit tests (via Surefire) and integration tests (via Maven Invoker Plugin).

## Running only unit tests

```bash
mvn test
```

## Running only integration tests

```bash
mvn verify -DskipTests
```

## Project structure

```
src/main/java/ca/weblite/skillsjar/
  ├── PackageSkillMojo.java       — @Mojo(name="package")
  ├── InstallSkillsMojo.java      — @Mojo(name="install")
  ├── ListSkillsMojo.java         — @Mojo(name="list")
  ├── VerifySkillMojo.java        — @Mojo(name="verify")
  ├── SkillBundle.java            — skill metadata model
  ├── SkillResolver.java          — dependency tree walker
  ├── SkillInstaller.java         — JAR extraction + manifest
  ├── SkillValidator.java         — format validation
  └── util/
      ├── FrontmatterParser.java  — YAML frontmatter parser
      └── JarUtils.java           — JAR creation/extraction
src/test/                         — unit tests + fixtures
src/it/                           — Maven Invoker integration tests
```

## Adding a new validation rule

1. Add the check to `SkillValidator.java`
2. Add positive and negative test cases in `SkillValidatorTest.java`
3. Add a test fixture in `src/test/resources/fixtures/` if needed

## Release process

> Not yet automated. Planned for v0.2.0.

1. Update version in `pom.xml` (remove `-SNAPSHOT`)
2. Update `CHANGELOG.md` — move items from `[Unreleased]` to the new version
3. Commit: `git commit -m "Release 0.1.0"`
4. Tag: `git tag v0.1.0`
5. Build and deploy: `mvn clean deploy` (requires OSSRH credentials and GPG signing)
6. Bump to next snapshot: update `pom.xml` to `0.2.0-SNAPSHOT`
7. Commit: `git commit -m "Prepare for next development iteration"`

<!-- TODO: Add GitHub Actions release workflow with GPG signing and OSSRH publishing -->
