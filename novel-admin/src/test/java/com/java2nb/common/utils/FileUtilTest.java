package com.java2nb.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FileUtilTest {

    @TempDir
    Path uploadRoot;

    @Test
    void resolvesOnlyFilesInsideUploadRoot() {
        assertThat(FileUtil.resolveUnderRoot(uploadRoot.toString(), "2026/08/image.png"))
            .isEqualTo(uploadRoot.resolve("2026/08/image.png").toAbsolutePath().normalize());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> FileUtil.resolveUnderRoot(uploadRoot.toString(), "../secret.txt"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> FileUtil.resolveUnderRoot(uploadRoot.toString(), "..\\secret.txt"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> FileUtil.resolveUnderRoot(uploadRoot.toString(), "/etc/passwd"));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> FileUtil.resolveUnderRoot(uploadRoot.toString(), "C:\\Windows\\win.ini"));
    }

    @Test
    void uploadAndDeleteStayInsideUploadRoot() throws Exception {
        FileUtil.uploadFile(new byte[]{1, 2, 3}, uploadRoot.toString(), "safe.bin");
        Path uploaded = uploadRoot.resolve("safe.bin");

        assertThat(Files.readAllBytes(uploaded)).containsExactly(1, 2, 3);
        assertThat(FileUtil.deleteFile(uploaded)).isTrue();
        assertThat(uploaded).doesNotExist();
    }
}
