package com.yang.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
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
    void shouldUseProgressiveRender_600个文件返回true() {
        assertThat(strategy.shouldUseProgressiveRender(600)).isTrue();
    }

    @Test
    void shouldUseProgressiveRender_超过600返回true() {
        assertThat(strategy.shouldUseProgressiveRender(1000)).isTrue();
    }

    @Test
    void shouldUseProgressiveRender_599个文件返回false() {
        assertThat(strategy.shouldUseProgressiveRender(599)).isFalse();
    }

    @Test
    void shouldUseProgressiveRender_少量文件返回false() {
        assertThat(strategy.shouldUseProgressiveRender(10)).isFalse();
    }

    @Test
    void shouldUseProgressiveRender_零个文件返回false() {
        assertThat(strategy.shouldUseProgressiveRender(0)).isFalse();
    }
}
