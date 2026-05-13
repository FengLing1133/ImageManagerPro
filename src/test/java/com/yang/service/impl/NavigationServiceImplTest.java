package com.yang.service.impl;

import org.junit.jupiter.api.BeforeEach;
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
        service = new NavigationServiceImpl();
        dirA = new File(tempDir, "a");
        dirB = new File(tempDir, "b");
        dirC = new File(tempDir, "c");
        dirA.mkdir();
        dirB.mkdir();
        dirC.mkdir();
    }

    // === navigateTo ===

    @Test
    void navigateTo_有效目录返回true() {
        assertThat(service.navigateTo(dirA)).isTrue();
        assertThat(service.getCurrentDirectory()).isEqualTo(dirA);
    }

    @Test
    void navigateTo_null返回false() {
        assertThat(service.navigateTo(null)).isFalse();
    }

    @Test
    void navigateTo_非目录返回false() {
        File file = new File(tempDir, "file.txt");
        assertThat(service.navigateTo(file)).isFalse();
    }

    @Test
    void navigateTo_相同目录返回false() {
        service.navigateTo(dirA);
        assertThat(service.navigateTo(dirA)).isFalse();
    }

    @Test
    void navigateTo_将当前目录压入后退栈() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);

        assertThat(service.hasBackHistory()).isTrue();
        assertThat(service.goBack()).isEqualTo(dirA);
    }

    @Test
    void navigateTo_清空前进栈() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();
        assertThat(service.hasForwardHistory()).isTrue();

        service.navigateTo(dirC);
        assertThat(service.hasForwardHistory()).isFalse();
    }

    // === goUp ===

    @Test
    void goUp_返回父目录() {
        service.navigateTo(dirA);
        File parent = service.goUp();

        assertThat(parent).isEqualTo(tempDir);
        assertThat(service.getCurrentDirectory()).isEqualTo(tempDir);
    }

    @Test
    void goUp_当前目录为null返回null() {
        assertThat(service.goUp()).isNull();
    }

    @Test
    void goUp_将当前目录压入后退栈() {
        service.navigateTo(dirA);
        service.goUp();

        assertThat(service.hasBackHistory()).isTrue();
    }

    // === goBack ===

    @Test
    void goBack_返回上一个目录() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);

        File result = service.goBack();

        assertThat(result).isEqualTo(dirA);
        assertThat(service.getCurrentDirectory()).isEqualTo(dirA);
    }

    @Test
    void goBack_空栈返回null() {
        assertThat(service.goBack()).isNull();
    }

    @Test
    void goBack_将当前目录压入前进栈() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();

        assertThat(service.hasForwardHistory()).isTrue();
        assertThat(service.goForward()).isEqualTo(dirB);
    }

    // === goForward ===

    @Test
    void goForward_返回下一个目录() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();

        File result = service.goForward();

        assertThat(result).isEqualTo(dirB);
        assertThat(service.getCurrentDirectory()).isEqualTo(dirB);
    }

    @Test
    void goForward_空栈返回null() {
        assertThat(service.goForward()).isNull();
    }

    @Test
    void goForward_将当前目录压入后退栈() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();
        service.goForward();

        assertThat(service.hasBackHistory()).isTrue();
    }

    // === hasBackHistory / hasForwardHistory ===

    @Test
    void 初始状态无历史() {
        assertThat(service.hasBackHistory()).isFalse();
        assertThat(service.hasForwardHistory()).isFalse();
    }

    @Test
    void hasBackHistory_导航后有历史() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        assertThat(service.hasBackHistory()).isTrue();
    }

    @Test
    void hasForwardHistory_后退后有历史() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();
        assertThat(service.hasForwardHistory()).isTrue();
    }

    // === undoNavigation ===

    @Test
    void undoNavigation_弹出后退栈顶部() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);

        File result = service.undoNavigation();

        assertThat(result).isEqualTo(dirA);
    }

    @Test
    void undoNavigation_空栈返回null() {
        assertThat(service.undoNavigation()).isNull();
    }

    // === setCurrentDirectory ===

    @Test
    void setCurrentDirectory_直接设置不记录历史() {
        service.setCurrentDirectory(dirA);

        assertThat(service.getCurrentDirectory()).isEqualTo(dirA);
        assertThat(service.hasBackHistory()).isFalse();
    }

    // === clearHistory ===

    @Test
    void clearHistory_清空所有历史() {
        service.navigateTo(dirA);
        service.navigateTo(dirB);
        service.goBack();

        service.clearHistory();

        assertThat(service.hasBackHistory()).isFalse();
        assertThat(service.hasForwardHistory()).isFalse();
    }

    // === resolvePath ===

    @Test
    void resolvePath_有效目录返回File() {
        File result = service.resolvePath(tempDir.getAbsolutePath());

        assertThat(result).isEqualTo(tempDir);
    }

    @Test
    void resolvePath_null返回null() {
        assertThat(service.resolvePath(null)).isNull();
    }

    @Test
    void resolvePath_空字符串返回null() {
        assertThat(service.resolvePath("")).isNull();
        assertThat(service.resolvePath("   ")).isNull();
    }

    @Test
    void resolvePath_不存在的路径返回null() {
        assertThat(service.resolvePath("/nonexistent/path")).isNull();
    }

    @Test
    void resolvePath_文件路径返回null() throws Exception {
        File file = new File(tempDir, "file.txt");
        file.createNewFile();
        assertThat(service.resolvePath(file.getAbsolutePath())).isNull();
    }

    // === 综合场景 ===

    @Test
    void 综合_多次导航后退前进() {
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
