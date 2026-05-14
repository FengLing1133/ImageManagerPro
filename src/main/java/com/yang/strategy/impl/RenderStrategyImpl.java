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

    /** 触发渐进式渲染的文件数量阈值 */
    private static final int PROGRESSIVE_THRESHOLD = 50;

    /** 卡片间距 */
    private static final Insets CARD_MARGIN = new Insets(5);

    /** 构建管线时间线 */
    private Timeline buildTimeline;

    /** 渲染管线时间线 */
    private Timeline renderTimeline;

    @Override
    public boolean shouldUseProgressiveRender(int fileCount) {
        return fileCount >= PROGRESSIVE_THRESHOLD;
    }

    @Override
    public void startBuildPipeline(Deque<Runnable> buildTasks, int batchSize, Runnable onComplete) {
        stopBuildPipeline();

        // 每帧处理一批构建任务，约60fps
        buildTimeline = new Timeline(new KeyFrame(Duration.millis(16), event -> {
            int built = 0;
            while (built < batchSize && !buildTasks.isEmpty()) {
                buildTasks.pollFirst().run();
                built++;
            }
            // 所有任务完成后停止并回调
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

        // 每帧替换一批占位节点为实际卡片
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

    /** 停止构建管线并释放资源 */
    private void stopBuildPipeline() {
        if (buildTimeline != null) {
            buildTimeline.stop();
            buildTimeline = null;
        }
    }

    /** 停止渲染管线并释放资源 */
    private void stopRenderPipeline() {
        if (renderTimeline != null) {
            renderTimeline.stop();
            renderTimeline = null;
        }
    }
}