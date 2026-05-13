package com.yang.strategy.impl;

import com.yang.strategy.RenderStrategy;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.springframework.stereotype.Component;
import java.util.Deque;

/**
 * 渐进式渲染策略实现
 * 大目录分批构建和渲染，避免界面冻结
 */
@Component
public class RenderStrategyImpl implements RenderStrategy {

    private static final int PROGRESSIVE_THRESHOLD = 600;
    private static final Insets CARD_MARGIN = new Insets(5);

    private Timeline buildTimeline;
    private Timeline renderTimeline;

    @Override
    public boolean shouldUseProgressiveRender(int fileCount) {
        return fileCount >= PROGRESSIVE_THRESHOLD;
    }

    @Override
    public void startBuildPipeline(Deque<Runnable> buildTasks, int batchSize, Runnable onComplete) {
        stopBuildPipeline();
        buildTimeline = new Timeline(new KeyFrame(Duration.millis(16), event -> {
            int built = 0;
            while (built < batchSize && !buildTasks.isEmpty()) {
                buildTasks.pollFirst().run();
                built++;
            }
            if (buildTasks.isEmpty()) {
                buildTimeline.stop();
                if (onComplete != null) onComplete.run();
            }
        }));
        buildTimeline.setCycleCount(Animation.INDEFINITE);
        buildTimeline.play();
    }

    @Override
    public void startRenderPipeline(Deque<RenderTask> renderTasks, FlowPane targetPane, int batchSize) {
        stopRenderPipeline();
        renderTimeline = new Timeline(new KeyFrame(Duration.millis(16), event -> {
            int rendered = 0;
            while (rendered < batchSize && !renderTasks.isEmpty()) {
                RenderTask task = renderTasks.pollFirst();
                if (task != null && task.index() < targetPane.getChildren().size()) {
                    targetPane.getChildren().set(task.index(), task.card());
                    FlowPane.setMargin(task.card(), CARD_MARGIN);
                }
                rendered++;
            }
            if (renderTasks.isEmpty()) {
                renderTimeline.stop();
            }
        }));
        renderTimeline.setCycleCount(Animation.INDEFINITE);
        renderTimeline.play();
    }

    @Override
    public void stopAll() {
        stopBuildPipeline();
        stopRenderPipeline();
    }

    private void stopBuildPipeline() {
        if (buildTimeline != null) {
            buildTimeline.stop();
            buildTimeline = null;
        }
    }

    private void stopRenderPipeline() {
        if (renderTimeline != null) {
            renderTimeline.stop();
            renderTimeline = null;
        }
    }
}
