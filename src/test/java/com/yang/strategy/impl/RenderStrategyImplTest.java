package com.yang.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenderStrategyImplTest {

    private RenderStrategyImpl strategy;

    @BeforeEach
    void setUp() {
        strategy = new RenderStrategyImpl();
    }

    // === shouldUseProgressiveRender ===

    @Test
    @DisplayName("shouldUseProgressiveRender: 50个文件返回true")
    void shouldUseProgressiveRender_50FilesReturnsTrue() {
        assertThat(strategy.shouldUseProgressiveRender(50)).isTrue();
    }

    @Test
    @DisplayName("shouldUseProgressiveRender: 超过50返回true")
    void shouldUseProgressiveRender_moreThan50ReturnsTrue() {
        assertThat(strategy.shouldUseProgressiveRender(1000)).isTrue();
    }

    @Test
    @DisplayName("shouldUseProgressiveRender: 49个文件返回false")
    void shouldUseProgressiveRender_49FilesReturnsFalse() {
        assertThat(strategy.shouldUseProgressiveRender(49)).isFalse();
    }

    @Test
    @DisplayName("shouldUseProgressiveRender: 少量文件返回false")
    void shouldUseProgressiveRender_fewFilesReturnsFalse() {
        assertThat(strategy.shouldUseProgressiveRender(10)).isFalse();
    }

    @Test
    @DisplayName("shouldUseProgressiveRender: 零个文件返回false")
    void shouldUseProgressiveRender_zeroFilesReturnsFalse() {
        assertThat(strategy.shouldUseProgressiveRender(0)).isFalse();
    }
}
