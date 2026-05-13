package com.yang.repository;

import javafx.scene.image.Image;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 图片数据访问层
 * 封装图片加载、缩略图生成和缓存管理
 */
public interface ImageRepository {

    /**
     * 加载图片缩略图（异步友好，使用后台线程池）
     * @param file 图片文件
     * @param thumbSize 缩略图尺寸
     * @return 缩略图 Image，加载失败返回 null
     */
    Image loadThumbnail(File file, int thumbSize);

    /**
     * 加载完整图片（用于幻灯片）
     * @param file 图片文件
     * @return 完整 Image，加载失败返回 null
     */
    Image loadFullImage(File file);

    /**
     * 从缓存获取图片，如果不存在则返回 null
     */
    Image getCachedImage(String filePath);

    /**
     * 将图片放入缓存
     */
    void cacheImage(String filePath, Image image);

    /**
     * 清空图片缓存
     */
    void clearCache();

    /**
     * 获取目录下所有图片文件路径（已排序）
     */
    List<String> getImagePaths(File directory);

    /**
     * 获取后台图片加载线程池
     */
    ExecutorService getExecutor();

    /**
     * 关闭线程池
     */
    void shutdown();
}
