package com.yang.service;

import java.io.File;
import java.util.List;

/**
 * 目录服务业务逻辑层
 * 封装目录树构建、子目录加载等业务逻辑
 */
public interface DirectoryService {

    /**
     * 获取目录下所有可见子目录（过滤系统目录）
     * @param parent 父目录
     * @return 排序后的子目录列表
     */
    List<File> listChildDirectories(File parent);

    /**
     * 获取系统根目录列表（如 C:\、D:\ 等）
     */
    File[] getSystemRoots();

    /**
     * 获取用户图片目录
     * @return Pictures 目录，不存在返回 null
     */
    File getPicturesDirectory();

    /**
     * 判断目录是否为系统目录（应过滤）
     */
    boolean isSystemDirectory(File dir);
}
