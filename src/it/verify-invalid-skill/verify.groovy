// This build should have failed due to invalid skill (no frontmatter)
File buildLog = new File(basedir, "build.log")
assert buildLog.isFile(), "build.log not found"

String log = buildLog.text
assert log.contains("missing YAML frontmatter"), "Expected validation error about missing frontmatter in build log"
