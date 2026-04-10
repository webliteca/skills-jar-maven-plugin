import java.util.jar.JarFile

// Verify module-a skills JAR
File moduleASkillsJar = new File(basedir, "module-a/target/module-a-1.0.0-SNAPSHOT-skills.jar")
assert moduleASkillsJar.isFile(), "module-a skills JAR not found: ${moduleASkillsJar}"

JarFile jfA = new JarFile(moduleASkillsJar)
try {
    assert jfA.getEntry("skill/SKILL.md") != null, "skill/SKILL.md not found in module-a JAR"
} finally {
    jfA.close()
}

// Verify module-b skills JAR
File moduleBSkillsJar = new File(basedir, "module-b/target/module-b-1.0.0-SNAPSHOT-skills.jar")
assert moduleBSkillsJar.isFile(), "module-b skills JAR not found: ${moduleBSkillsJar}"

JarFile jfB = new JarFile(moduleBSkillsJar)
try {
    assert jfB.getEntry("skill/SKILL.md") != null, "skill/SKILL.md not found in module-b JAR"
} finally {
    jfB.close()
}
