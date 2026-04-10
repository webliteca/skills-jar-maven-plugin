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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Utilities for creating and extracting skill JAR files.
 */
public class JarUtils {

    /** Prefix for skill content inside the JAR. */
    public static final String SKILL_PREFIX = "skill/";

    /** Path for library metadata inside the JAR. */
    public static final String METADATA_PATH = "META-INF/skill-library.properties";

    /** Maximum allowed size per JAR entry (10 MB). */
    public static final long MAX_ENTRY_SIZE = 10L * 1024 * 1024;

    /** Maximum allowed total JAR size (50 MB). */
    public static final long MAX_JAR_SIZE = 50L * 1024 * 1024;

    /** Maximum allowed number of entries in a skill JAR. */
    public static final int MAX_ENTRY_COUNT = 10_000;

    /**
     * Creates a skill JAR from a skill directory.
     *
     * @param skillDir  the skill folder (containing SKILL.md, references/, etc.)
     * @param outputJar the target JAR file
     * @param metadata  optional metadata properties to include as skill-library.properties (may be null)
     * @throws IOException if JAR creation fails
     */
    public static void createSkillJar(File skillDir, File outputJar, Properties metadata) throws IOException {
        if (!skillDir.isDirectory()) {
            throw new IOException("Skill directory does not exist: " + skillDir.getAbsolutePath());
        }

        outputJar.getParentFile().mkdirs();

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Created-By", "skills-jar-plugin");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar), manifest)) {
            // Add metadata if present
            if (metadata != null && !metadata.isEmpty()) {
                jos.putNextEntry(new JarEntry(METADATA_PATH));
                metadata.store(jos, "Skill Library Metadata");
                jos.closeEntry();
            }

            // Walk the skill directory and add all files under skill/ prefix
            Path skillPath = skillDir.toPath();
            List<Path> files = new ArrayList<>();
            Files.walkFileTree(skillPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    files.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });

            for (Path file : files) {
                String relativePath = skillPath.relativize(file).toString().replace(File.separatorChar, '/');
                String entryName = SKILL_PREFIX + relativePath;

                jos.putNextEntry(new JarEntry(entryName));
                Files.copy(file, jos);
                jos.closeEntry();
            }
        }
    }

    /**
     * Extracts skill content from a skill JAR into a target directory.
     * Only entries under the {@code skill/} prefix are extracted.
     * Includes zip-slip protection and size limit enforcement.
     *
     * @param jarFile   the skill JAR file
     * @param targetDir the directory to extract into
     * @return list of extracted file paths (relative to targetDir)
     * @throws IOException          if extraction fails
     * @throws SecurityException    if zip-slip or size limit violations are detected
     */
    public static List<String> extractSkillJar(File jarFile, File targetDir) throws IOException {
        if (!jarFile.isFile()) {
            throw new IOException("JAR file does not exist: " + jarFile.getAbsolutePath());
        }

        long jarSize = jarFile.length();
        if (jarSize > MAX_JAR_SIZE) {
            throw new SecurityException("JAR file exceeds maximum allowed size (" + MAX_JAR_SIZE + " bytes): "
                    + jarFile.getAbsolutePath() + " (" + jarSize + " bytes)");
        }

        targetDir.mkdirs();
        Path targetPath = targetDir.toPath().toRealPath();
        List<String> extractedFiles = new ArrayList<>();
        int entryCount = 0;
        long totalSize = 0;

        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String name = entry.getName();

                // Only extract entries under skill/ prefix
                if (!name.startsWith(SKILL_PREFIX)) {
                    continue;
                }

                // Strip the skill/ prefix for extraction
                String relativePath = name.substring(SKILL_PREFIX.length());
                if (relativePath.isEmpty()) {
                    continue; // skip the directory entry itself
                }

                // Security: zip-slip protection
                validateEntryPath(relativePath, targetPath);

                entryCount++;
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw new SecurityException("JAR contains too many entries (>" + MAX_ENTRY_COUNT + ")");
                }

                File outputFile = new File(targetDir, relativePath);

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                    continue;
                }

                // Ensure parent directories exist
                outputFile.getParentFile().mkdirs();

                // Extract with size limit enforcement
                long entrySize = 0;
                try (OutputStream os = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        entrySize += len;
                        totalSize += len;
                        if (entrySize > MAX_ENTRY_SIZE) {
                            throw new SecurityException("JAR entry exceeds maximum allowed size (" + MAX_ENTRY_SIZE
                                    + " bytes): " + name);
                        }
                        if (totalSize > MAX_JAR_SIZE) {
                            throw new SecurityException("Total extracted content exceeds maximum allowed size ("
                                    + MAX_JAR_SIZE + " bytes)");
                        }
                        os.write(buffer, 0, len);
                    }
                }

                extractedFiles.add(relativePath);
            }
        }

        return extractedFiles;
    }

    /**
     * Reads the skill-library.properties from a skill JAR.
     *
     * @param jarFile the skill JAR
     * @return the properties, or null if not present
     */
    public static Properties readMetadata(File jarFile) throws IOException {
        try (JarFile jf = new JarFile(jarFile)) {
            ZipEntry entry = jf.getEntry(METADATA_PATH);
            if (entry == null) {
                return null;
            }
            Properties props = new Properties();
            try (InputStream is = jf.getInputStream(entry)) {
                props.load(is);
            }
            return props;
        }
    }

    /**
     * Validates that an entry path is safe for extraction (zip-slip protection).
     */
    static void validateEntryPath(String relativePath, Path targetDir) throws IOException {
        // Reject paths containing ..
        if (relativePath.contains("..")) {
            throw new SecurityException("JAR entry contains path traversal (..): " + relativePath);
        }

        // Reject absolute paths
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw new SecurityException("JAR entry contains absolute path: " + relativePath);
        }

        // Verify resolved path is within target directory
        Path resolvedPath = targetDir.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(targetDir)) {
            throw new SecurityException("JAR entry resolves outside target directory: " + relativePath);
        }
    }
}
