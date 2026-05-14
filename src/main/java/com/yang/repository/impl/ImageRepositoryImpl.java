package com.yang.repository.impl;

import com.yang.repository.ImageRepository;
import javafx.scene.image.Image;
import org.springframework.stereotype.Repository;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片数据访问层实现
 * 封装图片加载、缩略图生成和 LRU 缓存管理
 * 提供线程安全的图片缓存和异步加载能力
 */
@Repository
public class ImageRepositoryImpl implements ImageRepository {

    /**
     * 图片缓存最大容量
     * 当缓存超过此数量时，最久未使用的图片将被淘汰
     */
    private static final int CACHE_MAX_SIZE = 500;

    /**
     * 图片缓存（线程安全的 LRU 缓存）
     * 使用 LinkedHashMap 实现 LRU（最近最少使用）淘汰策略：
     * - accessOrder=true：按访问顺序排序，最近访问的排在末尾
     * - 重写 removeEldestEntry：当缓存超过最大容量时自动移除最久未访问的条目
     * - Collections.synchronizedMap：保证多线程环境下的线程安全
     */
    private final Map<String, Image> imageCache = Collections.synchronizedMap(
            new LinkedHashMap<>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            });

    /**
     * 图片加载线程池
     * 固定 3 个线程，用于异步加载图片缩略图
     * 设置为守护线程，不会阻止 JVM 退出
     */
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);  // 守护线程，JVM 退出时自动终止
        thread.setName("Image Loader");
        return thread;
    });

    /**
     * 加载图片缩略图
     * 先检查缓存，如果缓存命中则直接返回；否则从文件系统加载并缓存
     * @param file      图片文件
     * @param thumbSize 缩略图尺寸（宽高相同，单位像素）
     * @return 加载成功返回 Image 对象，失败或缓存未命中返回 null
     */
    @Override
    public Image loadThumbnail(File file, int thumbSize) {
        String filePath = file.getAbsolutePath();

        // 先从缓存中查找，命中则直接返回
        Image cached = imageCache.get(filePath);
        if (cached != null) return cached;

        try {
            // 从文件系统加载图片
            // 参数：URI, 宽度, 高度, 保持宽高比, 平滑缩放, 后台加载
            Image img = new Image(file.toURI().toString(), thumbSize, thumbSize, true, true, false);
            if (!img.isError()) {
                // 加载成功，存入缓存
                imageCache.put(filePath, img);
                return img;
            }
        } catch (Exception e) {
            // 加载失败返回 null
        }
        return null;
    }

    /**
     * 加载完整尺寸的图片（用于幻灯片展示）
     * 不进行缩放，加载原始分辨率的图片
     * 注意：此方法不使用缓存，因为大图片占用内存较多
     * @param file 图片文件
     * @return 加载成功返回 Image 对象，失败返回 null
     */
    @Override
    public Image loadFullImage(File file) {
        try {
            // 参数 false 表示不使用后台加载（同步加载）
            Image image = new Image(file.toURI().toString(), false);
            if (!image.isError()) {
                return image;
            }
        } catch (Exception e) {
            // 加载失败返回 null
        }
        return null;
    }

    /**
     * 从缓存中获取已加载的图片
     * @param filePath 图片文件的绝对路径（作为缓存键）
     * @return 缓存中的 Image 对象，如果未缓存则返回 null
     */
    @Override
    public Image getCachedImage(String filePath) {
        return imageCache.get(filePath);
    }

    /**
     * 将图片存入缓存
     * @param filePath 图片文件的绝对路径（作为缓存键）
     * @param image    要缓存的 Image 对象
     */
    @Override
    public void cacheImage(String filePath, Image image) {
        imageCache.put(filePath, image);
    }

    /**
     * 清空图片缓存
     * 释放所有缓存的图片内存
     */
    @Override
    public void clearCache() {
        imageCache.clear();
    }

    /**
     * 获取图片加载线程池
     * 供 Service 层提交异步图片加载任务
     * @return 图片加载线程池
     */
    @Override
    public ExecutorService getExecutor() {
        return imageExecutor;
    }

    /**
     * 关闭线程池
     * 应用退出时调用，停止接受新任务并等待已有任务完成
     */
    @Override
    public void shutdown() {
        imageExecutor.shutdown();
    }

}
