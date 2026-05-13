package com.yang.service;

import java.io.File;

/**
 * 导航服务业务逻辑层
 * 封装目录导航、历史栈、路径解析等业务逻辑
 */
public interface NavigationService {

    /**
     * 导航到指定目录
     * @param dir 目标目录
     * @return 如果是新目录则返回 true，如果已在当前目录则返回 false
     */
    boolean navigateTo(File dir);

    /**
     * 返回上一级目录
     * @return 父目录，如果已在根目录则返回 null
     */
    File goUp();

    /**
     * 后退（从后退栈弹出，当前目录压入前进栈）
     * @return 上一个目录，如果后退栈为空返回 null
     */
    File goBack();

    /**
     * 前进（从前进栈弹出，当前目录压入后退栈）
     * @return 下一个目录，如果前进栈为空返回 null
     */
    File goForward();

    /**
     * 后退栈是否有历史
     */
    boolean hasBackHistory();

    /**
     * 前进栈是否有历史
     */
    boolean hasForwardHistory();

    /**
     * 撤销返回（从历史栈弹出）
     * @return 上一个目录，如果历史为空返回 null
     */
    File undoNavigation();

    /**
     * 获取当前目录
     */
    File getCurrentDirectory();

    /**
     * 设置当前目录（不记录历史）
     */
    void setCurrentDirectory(File dir);

    /**
     * 清空导航历史
     */
    void clearHistory();

    /**
     * 解析路径字符串为 File，验证是否为有效目录
     * @return 有效的目录 File，无效返回 null
     */
    File resolvePath(String path);
}
