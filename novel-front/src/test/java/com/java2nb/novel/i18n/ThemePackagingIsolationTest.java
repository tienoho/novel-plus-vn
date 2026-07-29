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
