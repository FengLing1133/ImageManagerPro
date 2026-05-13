package com.yang.strategy;

import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * 渲染策略接口
 * 决定如何将构建好的 UI 卡片渲染到 FlowPane
 */
public interface RenderStrategy {

    /**
     * 判断是否应该使用渐进式渲染
     * @param fileCount 文件数量
     * @return true 表示使用渐进式渲染，false 表示直接渲染
     */
    boolean shouldUseProgressiveRender(int fileCount);

    /**
     * 启动渐进式构建管道
     * @param buildTasks 待构建的任务队列
     * @param batchSize 每批构建数量
     * @param onComplete 每批构建完成后的回调
     */
    void startBuildPipeline(Deque<Runnable> buildTasks, int batchSize, Runnable onComplete);

    /**
     * 启动渐进式渲染管道
     * @param renderTasks 待渲染的任务队列
     * @param targetPane 目标 FlowPane
     * @param batchSize 每批渲染数量
     */
    void startRenderPipeline(Deque<RenderTask> renderTasks, FlowPane targetPane, int batchSize);

    /**
     * 停止所有渐进式管道
     */
    void stopAll();

    /**
     * 渲染任务：将一个 VBox 放到 FlowPane 的指定位置
     */
    record RenderTask(int index, VBox card) {}
}
