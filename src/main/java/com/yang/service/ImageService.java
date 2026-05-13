package com.yang.service;

import javafx.scene.image.Image;
import java.io.File;
import java.util.List;

/**
 * 图片业务逻辑层
 * 封装图片加载、缓存管理、缩略图生成等业务逻辑
 */
public interface ImageService {

    /**
     * 加载图片缩略图（带缓存）
     * @param file 图片文件
     * @param thumbSize 缩略图尺寸
     * @return 缩略图 Image
     */
    Image loadThumbnail(File file, int thumbSize);

    /**
     * 加载完整图片（用于幻灯片）
     */
    Image loadFullImage(File file);

    /**
     * 获取目录下所有图片文件路径
     */
    List<String> getImagePaths(File directory);

    /**
     * 判断文件是否为图片
     */
    boolean isImageFile(File file);

    /**
     * 清空图片缓存
     */
    void clearCache();
}
