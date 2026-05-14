package com.yang.repository.impl;

import com.yang.repository.ImageRepository;
import javafx.scene.image.Image;
import org.springframework.stereotype.Repository;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 图片数据访问层，封装图片加载和 LRU 缓存管理 */
@Repository
public class ImageRepositoryImpl implements ImageRepository {

    private static final int CACHE_MAX_SIZE = 500;

    /** 线程安全的 LRU 缓存，超过容量自动淘汰最久未访问的条目 */
    private final Map<String, Image> imageCache = Collections.synchronizedMap(
            new LinkedHashMap<>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            });

    /** 3 线程的守护线程池，用于异步加载缩略图 */
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("Image Loader");
        return thread;
    });

    /** 加载缩略图，优先从缓存获取 */
    @Override
    public Image loadThumbnail(File file, int thumbSize) {
        String filePath = file.getAbsolutePath();
        Image cached = imageCache.get(filePath);
        if (cached != null) return cached;

        try {
            Image img = new Image(file.toURI().toString(), thumbSize, thumbSize, true, true, false);
            if (!img.isError()) {
                imageCache.put(filePath, img);
                return img;
            }
        } catch (Exception e) {
        }
        return null;
    }

    /** 加载完整尺寸图片（幻灯片用），不缓存 */
    @Override
    public Image loadFullImage(File file) {
        try {
            Image image = new Image(file.toURI().toString(), false);
            if (!image.isError()) {
                return image;
            }
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public Image getCachedImage(String filePath) {
        return imageCache.get(filePath);
    }

    @Override
    public void cacheImage(String filePath, Image image) {
        imageCache.put(filePath, image);
    }

    @Override
    public void clearCache() {
        imageCache.clear();
    }

    @Override
    public ExecutorService getExecutor() {
        return imageExecutor;
    }

    @Override
    public void shutdown() {
        imageExecutor.shutdown();
    }
}
