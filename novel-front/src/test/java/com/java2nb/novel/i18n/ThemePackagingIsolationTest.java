package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ThemePackagingIsolationTest {

    private static final Set<String> SUPPORTED_THEMES = Set.of("green", "orange", "dark", "blue");

    @Test
    void packagedResourcesContainOnlyRuntimeBaseAndSelectedTheme() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        String theme = System.getProperty("theme.name", "green");

        assertThat(theme).isIn(SUPPORTED_THEMES);
        assertOverlayEquals(
            module.resolve("src/main/resources/templates"),
            repository.resolve("templates").resolve(theme).resolve("html"),
            module.resolve("target/classes/templates")
        );
        assertOverlayEquals(
            module.resolve("src/main/resources/static"),
            repository.resolve("templates").resolve(theme).resolve("static"),
            module.resolve("target/classes/static")
        );
    }

    @Test
    void lifecycleClearsPreviousThemeBeforeCopyingResources() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        String pom = Files.readString(module.resolve("pom.xml"), StandardCharsets.UTF_8);
        int clearExecution = pom.indexOf("<id>clear-theme-output</id>");
        int packageExecution = pom.indexOf("<id>package-distribution</id>");

        assertThat(clearExecution).isGreaterThanOrEqualTo(0).isLessThan(packageExecution);
        assertThat(pom).contains(
            "<phase>generate-resources</phase>",
            "${project.build.outputDirectory}/templates",
            "${project.build.outputDirectory}/static"
        );
    }

    @Test
    void distributionNormalizesCopiedScriptsWithoutMutatingSources() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        String pom = Files.readString(module.resolve("pom.xml"), StandardCharsets.UTF_8);
        int legacyBuild = pom.indexOf("<!--    <build>");
        String activePom = legacyBuild < 0 ? pom : pom.substring(0, legacyBuild);
        int copyToDistribution = activePom.indexOf(
            "<copy todir=\"${project.build.directory}/build/bin\">");
        int normalizeDistribution = activePom.indexOf(
            "<fixcrlf srcdir=\"${project.build.directory}/build/bin\" eol=\"unix\"/>");

        assertThat(copyToDistribution).isGreaterThanOrEqualTo(0).isLessThan(normalizeDistribution);
        assertThat(activePom).doesNotContain(
            "<fixcrlf srcdir=\"${basedir}/src/main/build/scripts\"");
    }

    @Test
    void distributionCopiesDockerfileBeforeCreatingArchive() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        String pom = Files.readString(module.resolve("pom.xml"), StandardCharsets.UTF_8);
        int legacyBuild = pom.indexOf("<!--    <build>");
        String activePom = legacyBuild < 0 ? pom : pom.substring(0, legacyBuild);
        int dockerCopy = activePom.indexOf(
            "<copy file=\"${basedir}/src/main/build/docker/Dockerfile\"");
        int createArchive = activePom.indexOf(
            "<zip destfile='${project.build.directory}/build/${project.artifactId}.zip'>");

        assertThat(dockerCopy).isGreaterThanOrEqualTo(0).isLessThan(createArchive);
    }

    @Test
    void distributionMergesRuntimeBaseBeforeEveryThemeOverlay() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        String pom = Files.readString(module.resolve("pom.xml"), StandardCharsets.UTF_8);
        int legacyBuild = pom.indexOf("<!--    <build>");
        String activePom = legacyBuild < 0 ? pom : pom.substring(0, legacyBuild);
        int runtimeHtml = activePom.indexOf(
            "<fileset dir=\"${basedir}/src/main/resources/templates\"/>");
        int overlayHtml = activePom.indexOf(
            "<fileset dir=\"${basedir}/../templates/@{name}/html\"/>");
        int runtimeStatic = activePom.indexOf(
            "<fileset dir=\"${basedir}/src/main/resources/static\"/>");
        int overlayStatic = activePom.indexOf(
            "<fileset dir=\"${basedir}/../templates/@{name}/static\"/>");

        assertThat(runtimeHtml).isGreaterThanOrEqualTo(0).isLessThan(overlayHtml);
        assertThat(runtimeStatic).isGreaterThanOrEqualTo(0).isLessThan(overlayStatic);
        assertThat(activePom).contains(
            "${project.build.directory}/build/templates",
            "<merge-theme name=\"green\"/>",
            "<merge-theme name=\"orange\"/>",
            "<merge-theme name=\"dark\"/>",
            "<merge-theme name=\"blue\"/>"
        );
    }

    @Test
    void distributionConfigReferencesEnvironmentInsteadOfLiteralApiSecrets() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        String config = Files.readString(
            module.resolve("src/main/build/config/application.yml"), StandardCharsets.UTF_8);

        assertThat(config)
            .contains("${OPENAI_API_KEY:disabled}")
            .doesNotContainPattern("sk-[A-Za-z0-9_-]{20,}");
    }

    private void assertOverlayEquals(Path runtimeRoot, Path themeRoot, Path packagedRoot) throws Exception {
        Set<String> expected = relativeFiles(runtimeRoot);
        expected.addAll(relativeFiles(themeRoot));
        assertThat(relativeFiles(packagedRoot)).containsExactlyInAnyOrderElementsOf(expected);
    }

    private Set<String> relativeFiles(Path root) throws Exception {
        Set<String> files = new HashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                .map(root::relativize)
                .map(path -> path.toString().replace('\\', '/'))
                .forEach(files::add);
        }
        return files;
    }
}
