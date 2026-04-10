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

import ca.weblite.skillsjar.util.JarUtils;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Installs skill bundles from resolved JARs into a local skills directory.
 * Manages a manifest file to track installed skills.
 */
public class SkillInstaller {

    private static final String MANIFEST_FILE = ".skill-manifest.json";

    private final File installDirectory;
    private final boolean overwrite;
    private final Log log;

    public SkillInstaller(File installDirectory, boolean overwrite, Log log) {
        this.installDirectory = installDirectory;
        this.overwrite = overwrite;
        this.log = log;
    }

    /**
     * Installs a list of skill bundles.
     *
     * @param bundles the skill bundles to install
     * @return the number of skills actually installed
     */
    public int installAll(List<SkillBundle> bundles) throws IOException {
        installDirectory.mkdirs();

        Map<String, ManifestEntry> manifest = readManifest();
        int installed = 0;

        for (SkillBundle bundle : bundles) {
            String targetDirName = bundle.getArtifactId();
            File targetDir = new File(installDirectory, targetDirName);

            String checksum = computeChecksum(bundle.getJarFile());
            ManifestEntry existing = manifest.get(targetDirName);

            if (existing != null && !overwrite) {
                log.info("Skipping " + bundle.getGav() + " (already installed, overwrite=false)");
                continue;
            }

            if (existing != null && checksum.equals(existing.checksum)) {
                log.info("Skipping " + bundle.getGav() + " (unchanged)");
                continue;
            }

            // Clean up previous installation
            if (existing != null && targetDir.isDirectory()) {
                cleanPreviousInstallation(targetDir, existing.files);
            }

            // Extract
            try {
                List<String> extractedFiles = JarUtils.extractSkillJar(bundle.getJarFile(), targetDir);
                log.info("Installed skill from " + bundle.getGav() + " -> " + targetDir.getAbsolutePath()
                        + " (" + extractedFiles.size() + " files)");

                manifest.put(targetDirName, new ManifestEntry(
                        bundle.getGav(),
                        checksum,
                        Instant.now().toString(),
                        extractedFiles
                ));
                installed++;
            } catch (SecurityException e) {
                log.warn("Skipping " + bundle.getGav() + " due to security violation: " + e.getMessage());
            }
        }

        writeManifest(manifest);
        return installed;
    }

    /**
     * Removes files from a previous installation that are tracked in the manifest.
     */
    private void cleanPreviousInstallation(File targetDir, List<String> previousFiles) {
        if (previousFiles == null) {
            return;
        }
        for (String file : previousFiles) {
            File f = new File(targetDir, file);
            if (f.isFile()) {
                f.delete();
            }
        }
        // Clean up empty directories (bottom-up)
        deleteEmptyDirs(targetDir);
    }

    private void deleteEmptyDirs(File dir) {
        if (!dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteEmptyDirs(child);
                }
            }
        }
        children = dir.listFiles();
        if (children != null && children.length == 0) {
            dir.delete();
        }
    }

    /**
     * Computes a SHA-256 checksum for a file.
     */
    private String computeChecksum(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file.toPath());
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    /**
     * Reads the skill manifest from the install directory.
     */
    Map<String, ManifestEntry> readManifest() {
        File manifestFile = new File(installDirectory, MANIFEST_FILE);
        if (!manifestFile.isFile()) {
            return new LinkedHashMap<>();
        }

        try {
            String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
            return parseManifest(content);
        } catch (IOException e) {
            log.warn("Could not read skill manifest: " + e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Writes the skill manifest to the install directory.
     */
    void writeManifest(Map<String, ManifestEntry> manifest) throws IOException {
        File manifestFile = new File(installDirectory, MANIFEST_FILE);
        installDirectory.mkdirs();

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        Iterator<Map.Entry<String, ManifestEntry>> it = manifest.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ManifestEntry> entry = it.next();
            ManifestEntry me = entry.getValue();
            json.append("  ").append(jsonString(entry.getKey())).append(": {\n");
            json.append("    \"gav\": ").append(jsonString(me.gav)).append(",\n");
            json.append("    \"checksum\": ").append(jsonString(me.checksum)).append(",\n");
            json.append("    \"installedAt\": ").append(jsonString(me.installedAt)).append(",\n");
            json.append("    \"files\": [");
            for (int i = 0; i < me.files.size(); i++) {
                if (i > 0) json.append(", ");
                json.append(jsonString(me.files.get(i)));
            }
            json.append("]\n");
            json.append("  }");
            if (it.hasNext()) json.append(",");
            json.append("\n");
        }
        json.append("}\n");

        Files.write(manifestFile.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses manifest JSON (simple parser — no external JSON library needed for this shape).
     */
    static Map<String, ManifestEntry> parseManifest(String json) {
        Map<String, ManifestEntry> result = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return result;
        }

        // Simple state-machine parser for our known JSON shape
        // This is intentionally simple since we control the format
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return result;
        }

        // Use basic string operations to parse the known structure
        int pos = 1; // skip opening brace
        while (pos < trimmed.length()) {
            // Find next key
            int keyStart = trimmed.indexOf('"', pos);
            if (keyStart < 0) break;
            int keyEnd = trimmed.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String key = trimmed.substring(keyStart + 1, keyEnd);

            // Find the opening brace of the value object
            int objStart = trimmed.indexOf('{', keyEnd);
            if (objStart < 0) break;

            // Find the matching closing brace
            int braceDepth = 1;
            int objEnd = objStart + 1;
            while (objEnd < trimmed.length() && braceDepth > 0) {
                char c = trimmed.charAt(objEnd);
                if (c == '{') braceDepth++;
                else if (c == '}') braceDepth--;
                objEnd++;
            }

            String objJson = trimmed.substring(objStart, objEnd);
            ManifestEntry entry = parseManifestEntry(objJson);
            if (entry != null) {
                result.put(key, entry);
            }

            pos = objEnd;
        }

        return result;
    }

    private static ManifestEntry parseManifestEntry(String json) {
        String gav = extractJsonStringValue(json, "gav");
        String checksum = extractJsonStringValue(json, "checksum");
        String installedAt = extractJsonStringValue(json, "installedAt");
        List<String> files = extractJsonStringArray(json, "files");

        if (gav == null) return null;
        return new ManifestEntry(gav, checksum, installedAt, files);
    }

    private static String extractJsonStringValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;

        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx < 0) return null;

        int valStart = json.indexOf('"', colonIdx + 1);
        if (valStart < 0) return null;

        int valEnd = valStart + 1;
        while (valEnd < json.length()) {
            if (json.charAt(valEnd) == '"' && json.charAt(valEnd - 1) != '\\') break;
            valEnd++;
        }

        return json.substring(valStart + 1, valEnd);
    }

    private static List<String> extractJsonStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return result;

        int bracketStart = json.indexOf('[', idx);
        if (bracketStart < 0) return result;

        int bracketEnd = json.indexOf(']', bracketStart);
        if (bracketEnd < 0) return result;

        String arrayContent = json.substring(bracketStart + 1, bracketEnd);
        int pos = 0;
        while (pos < arrayContent.length()) {
            int strStart = arrayContent.indexOf('"', pos);
            if (strStart < 0) break;
            int strEnd = strStart + 1;
            while (strEnd < arrayContent.length()) {
                if (arrayContent.charAt(strEnd) == '"' && arrayContent.charAt(strEnd - 1) != '\\') break;
                strEnd++;
            }
            result.add(arrayContent.substring(strStart + 1, strEnd));
            pos = strEnd + 1;
        }

        return result;
    }

    private static String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Manifest entry tracking an installed skill.
     */
    static class ManifestEntry {
        final String gav;
        final String checksum;
        final String installedAt;
        final List<String> files;

        ManifestEntry(String gav, String checksum, String installedAt, List<String> files) {
            this.gav = gav;
            this.checksum = checksum;
            this.installedAt = installedAt;
            this.files = files != null ? new ArrayList<>(files) : new ArrayList<>();
        }
    }
}
