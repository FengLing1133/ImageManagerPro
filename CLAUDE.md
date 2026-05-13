# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

ImageManagerPro 是一个基于 JavaFX + Spring Boot 的桌面图片管理器应用。支持目录浏览、图片缩略图预览、幻灯片播放，以及文件管理操作（复制、粘贴、删除、重命名）。

## 技术栈

- **Java 21** + **Spring Boot 4.0.6** + **JavaFX 25**
- **Maven** 构建，使用 **Lombok** 减少样板代码
- FXML 界面 + CSS 样式（`src/main/resources/style.css`）

## 构建与运行

```bash
# 编译项目
mvn clean compile

# 运行 JavaFX 应用
mvn javafx:run

# 运行测试
mvn test

# 打包 JAR
mvn clean package
```

## 架构

### 三层架构

项目采用三层架构，依赖方向：**Controller → Service → Repository**。

```
表示层 (controller/)          业务层 (service/)           数据层 (repository/)
┌──────────────────┐      ┌──────────────────────┐    ┌──────────────────┐
│ MainController    │─────→│ FileOperationService  │───→│ FileRepository   │
│ SlideShowController│     │ ImageService          │───→│ ImageRepository  │
│ VBoxFactory       │      │ DirectoryService      │    └──────────────────┘
│ AlterUtil         │      │ NavigationService     │
└──────────────────┘      └──────────────────────┘
                                ↑
                          策略层 (strategy/)
                          ┌──────────────────┐
                          │ RenderStrategy   │
                          └──────────────────┘
```

### 应用启动
- `ImageManagerProApplication` 继承 JavaFX `Application`，在 `init()` 阶段启动 Spring 上下文，通过 `FXMLLoader.setControllerFactory(springContext::getBean)` 实现 FXML 控制器的依赖注入。

### 包结构（`com.yang`）

- **controller/** — 表示层，FXML 控制器（Spring `@Component`）
  - `MainController` — 通过构造器注入 Service 接口，只负责 UI 绑定和事件处理
  - `SlideShowController` — 幻灯片窗口，支持缩放、平移、自动播放

- **service/** — 业务逻辑层（Spring `@Service` Bean，面向接口编程）
  - `FileOperationService` / `impl/FileOperationServiceImpl` — 文件增删改查
  - `ImageService` / `impl/ImageServiceImpl` — 图片加载、缓存管理
  - `DirectoryService` / `impl/DirectoryServiceImpl` — 目录树数据获取
  - `NavigationService` / `impl/NavigationServiceImpl` — 目录导航、历史栈
  - `DirectoryTreeService` — 目录树 UI 交互（与 TreeView 强耦合，保留原样）

- **repository/** — 数据访问层（Spring `@Repository` Bean）
  - `FileRepository` / `impl/FileRepositoryImpl` — 文件系统读写操作
  - `ImageRepository` / `impl/ImageRepositoryImpl` — 图片加载、LRU 缓存、线程池

- **strategy/** — 策略层
  - `RenderStrategy` / `impl/RenderStrategyImpl` — 渐进式渲染策略

- **util/** — UI 工具类
  - `VBoxFactory` — 创建文件/图片卡片式 VBox 节点
  - `AlterUtil` — Alert 弹窗封装

### 关键模式
- **线程池**：3 线程图片加载池（ImageRepositoryImpl）、2 线程目录扫描池（DirectoryTreeService），均为守护线程。
- **渐进式渲染**：大目录（600+ 文件）由 RenderStrategy 驱动分批构建（50/批）和渲染（50/批）。
- **LRU 图片缓存**：ImageRepositoryImpl 中的同步 `LinkedHashMap`，容量 200 条。
- **选中模型**：Ctrl+左键多选切换；左键/右键单选。右键菜单根据选中数量动态调整可用项。

### 资源文件
- FXML：`main.fxml`、`slideShow.fxml`
- 图标：`src/main/resources/icons/`（文件夹、文件、硬盘、前进、后退、播放）
- 样式：`src/main/resources/style.css`

## 编码规范

- 界面文字全部使用中文（窗口标题、标签、提示、右键菜单）
- Service/Repository 使用接口 + 实现类，面向接口编程
- Controller 通过构造器注入 Service 接口
- FXML 绑定使用 `@FXML` 注解
- 代码内使用中文注释说明逻辑
