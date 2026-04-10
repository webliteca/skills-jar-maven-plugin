import java.util.jar.JarFile

File buildDir = new File(basedir, "target")
File skillsJar = new File(buildDir, "simple-lib-1.0.0-SNAPSHOT-skills.jar")

assert skillsJar.isFile(), "skills JAR not found: ${skillsJar}"

// Verify JAR contents
JarFile jf = new JarFile(skillsJar)
try {
    assert jf.getEntry("skill/SKILL.md") != null, "skill/SKILL.md not found in JAR"
    assert jf.getEntry("META-INF/skill-library.properties") != null, "skill-library.properties not found in JAR"

    // Read and verify metadata
    Properties props = new Properties()
    props.load(jf.getInputStream(jf.getEntry("META-INF/skill-library.properties")))
    assert props.getProperty("groupId") == "com.example.it", "Wrong groupId: ${props.getProperty('groupId')}"
    assert props.getProperty("artifactId") == "simple-lib", "Wrong artifactId: ${props.getProperty('artifactId')}"
    assert props.getProperty("version") == "1.0.0-SNAPSHOT", "Wrong version: ${props.getProperty('version')}"
} finally {
    jf.close()
}

// Verify the skills JAR is installed in the local repo
String localRepoPath = "com/example/it/simple-lib/1.0.0-SNAPSHOT"
File localRepoDir = new File(localRepositoryPath, localRepoPath)
if (localRepoDir.isDirectory()) {
    boolean found = localRepoDir.listFiles().any { it.name.contains("skills") }
    assert found, "skills JAR not found in local repository"
}
