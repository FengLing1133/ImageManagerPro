package com.yang.service.impl;

import com.yang.repository.impl.FileRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationServiceImplTest {

    private NavigationServiceImpl service;

    @TempDir
    File tempDir;

    private File dirA;
    private File dirB;
    private File dirC;

    @BeforeEach
    void setUp() {
        service = new NavigationServiceImpl(new FileRepositoryImpl());
        dirA = new File(tempDir, "a");
        dirB = new File(tempDir, "b");
        dirC = new File(tempDir, "c");
        dirA.mkdir();
        dirB.mkdir();
        dirC.mkdir();
    }

    // === navigateTo ===

    @Test
    @DisplayName("navigateTo: 有效目录返回true")
    void navigateTo_validDirectoryReturnsTrue() {
        assertThat(service.navigateTo(dirA)).isTrue();
        assertThat(service.getCurrentDirectory()).isEqualTo(dirA);
    }

    @Test
    @DisplayName("navigateTo: null返回false")
    void navigateTo_nullReturnsFalse() {
        assertThat(service.navigateTo(null)).isFalse();
    }

    @Test
    @DisplayName("navigateTo: 非目录返回false")
    void navigateTo_nonDirectoryReturnsFalse() {
        File file = new File(tempDir, "file.txt");
        assertThat(service.navigateTo(file)).isFalse();
    }

    @Test
    @DisplayName("navigateTo: 相同目录返回false")
    void navigateTo_sameDirectoryReturnsFalse() {
        service.navigateTo(dirA);
        assertThat(service.navigateTo(dirA)).isFalse();
    }

    @Test
    @DisplayName("navigateTo: 将当前目录压入后退栈")
    void navigateTo_pushesCurrentDirToBackStack() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);

        assertThat(service.hasBackHistory()).isTrue();
        assertThat(service.goBack()).isEqualTo(dirA);
    }

    @Test
    @DisplayName("navigateTo: 清空前进栈")
    void navigateTo_clearsForwardStack() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();
        assertThat(service.hasForwardHistory()).isTrue();

        service.navigateTo(dirC);
        assertThat(service.hasForwardHistory()).isFalse();
    }

    // === goUp ===

    @Test
    @DisplayName("goUp: 返回父目录")
    void goUp_returnsParentDirectory() {
        service.navigateTo(dirA);
        File parent = service.goUp();

        assertThat(parent).isEqualTo(tempDir);
        assertThat(service.getCurrentDirectory()).isEqualTo(tempDir);
    }

    @Test
    @DisplayName("goUp: 当前目录为null返回null")
    void goUp_nullCurrentDirectoryReturnsNull() {
        assertThat(service.goUp()).isNull();
    }

    @Test
    @DisplayName("goUp: 将当前目录压入后退栈")
    void goUp_pushesCurrentDirToBackStack() {
        service.navigateTo(dirA);
        service.goUp();

        assertThat(service.hasBackHistory()).isTrue();
    }

    // === goBack ===

    @Test
    @DisplayName("goBack: 返回上一个目录")
    void goBack_returnsPreviousDirectory() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);

        File result = service.goBack();

        assertThat(result).isEqualTo(dirA);
        assertThat(service.getCurrentDirectory()).isEqualTo(dirA);
    }

    @Test
    @DisplayName("goBack: 空栈返回null")
    void goBack_emptyStackReturnsNull() {
        assertThat(service.goBack()).isNull();
    }

    @Test
    @DisplayName("goBack: 将当前目录压入前进栈")
    void goBack_pushesCurrentDirToForwardStack() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();

        assertThat(service.hasForwardHistory()).isTrue();
        assertThat(service.goForward()).isEqualTo(dirB);
    }

    // === goForward ===

    @Test
    @DisplayName("goForward: 返回下一个目录")
    void goForward_returnsNextDirectory() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();

        File result = service.goForward();

        assertThat(result).isEqualTo(dirB);
        assertThat(service.getCurrentDirectory()).isEqualTo(dirB);
    }

    @Test
    @DisplayName("goForward: 空栈返回null")
    void goForward_emptyStackReturnsNull() {
        assertThat(service.goForward()).isNull();
    }

    @Test
    @DisplayName("goForward: 将当前目录压入后退栈")
    void goForward_pushesCurrentDirToBackStack() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();
        service.goForward();

        assertThat(service.hasBackHistory()).isTrue();
    }

    // === hasBackHistory / hasForwardHistory ===

    @Test
    @DisplayName("初始状态: 无历史记录")
    void initialState_noHistory() {
        assertThat(service.hasBackHistory()).isFalse();
        assertThat(service.hasForwardHistory()).isFalse();
    }

    @Test
    @DisplayName("hasBackHistory: 导航后有历史")
    void hasBackHistory_afterNavigationReturnsTrue() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        assertThat(service.hasBackHistory()).isTrue();
    }

    @Test
    @DisplayName("hasForwardHistory: 后退后有历史")
    void hasForwardHistory_afterGoBackReturnsTrue() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();
        assertThat(service.hasForwardHistory()).isTrue();
    }

    // === setCurrentDirectory ===

    @Test
    @DisplayName("setCurrentDirectory: 直接设置不记录历史")
    void setCurrentDirectory_setsWithoutRecordingHistory() {
        service.setCurrentDirectory(dirA);

        assertThat(service.getCurrentDirectory()).isEqualTo(dirA);
        assertThat(service.hasBackHistory()).isFalse();
    }

    // === clearHistory ===

    @Test
    @DisplayName("clearHistory: 清空所有历史")
    void clearHistory_clearsAllHistory() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();

        service.clearHistory();

        assertThat(service.hasBackHistory()).isFalse();
        assertThat(service.hasForwardHistory()).isFalse();
    }

    // === resolvePath ===

    @Test
    @DisplayName("resolvePath: 有效目录返回File")
    void resolvePath_validDirectoryReturnsFile() {
        File result = service.resolvePath(tempDir.getAbsolutePath());

        assertThat(result).isEqualTo(tempDir);
    }

    @Test
    @DisplayName("resolvePath: null返回null")
    void resolvePath_nullReturnsNull() {
        assertThat(service.resolvePath(null)).isNull();
    }

    @Test
    @DisplayName("resolvePath: 空字符串返回null")
    void resolvePath_emptyStringReturnsNull() {
        assertThat(service.resolvePath("")).isNull();
        assertThat(service.resolvePath("   ")).isNull();
    }

    @Test
    @DisplayName("resolvePath: 不存在的路径返回null")
    void resolvePath_nonExistentPathReturnsNull() {
        assertThat(service.resolvePath("/nonexistent/path")).isNull();
    }

    @Test
    @DisplayName("resolvePath: 文件路径返回null")
    void resolvePath_filePathReturnsNull() throws Exception {
        File file = new File(tempDir, "file.txt");
        file.createNewFile();
        assertThat(service.resolvePath(file.getAbsolutePath())).isNull();
    }

    // === 综合场景 ===

    @Test
    @DisplayName("综合场景: 多次导航后退前进")
    void integration_multipleNavigateBackForward() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.navigateTo(dirC);

        // 后退两次
        assertThat(service.goBack()).isEqualTo(dirB);
        assertThat(service.goBack()).isEqualTo(dirA);

        // 前进一次
        assertThat(service.goForward()).isEqualTo(dirB);

        // 导航到新目录，清空前进栈
        service.navigateTo(dirA);
        assertThat(service.hasForwardHistory()).isFalse();
    }
}
