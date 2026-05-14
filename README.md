# ImageManagerPro

一个基于 JavaFX + Spring Boot 的桌面图片管理器应用。支持目录浏览、图片缩略图预览、幻灯片播放，以及文件管理操作（复制、粘贴、删除、重命名）。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 编程语言 |
| Spring Boot | 4.0.6 | 依赖注入、应用上下文管理 |
| JavaFX | 25 | 桌面 GUI 框架 |
| Maven | 3.9+ | 构建工具 |
| Lombok | - | 减少样板代码 |
| JUnit 5 + Mockito | - | 单元测试 |

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

## 架构概览

项目采用经典的三层架构，依赖方向：**Controller → Service → Repository**，外加一个 **Strategy** 策略层处理渐进式渲染。

```
┌─────────────────────────────────────────────────────────────────────┐
│                        表示层 (controller/)                         │
│  MainController          SlideShowController                        │
│  ├─ 目录树交互            ├─ 图片缩放/平移                           │
│  ├─ 文件卡片展示          ├─ 自动播放                                │
│  ├─ 导航栏/路径输入        └─ 前后翻页                               │
│  ├─ 右键菜单/选择模型                                                │
│  └─ 状态栏                                                          │
├─────────────────────────────────────────────────────────────────────┤
│  工具类 (util/)                                                      │
│  VBoxFactory              AlterUtil                                  │
│  ├─ 文件/图片卡片创建      └─ Alert 弹窗封装                         │
│  ├─ 右键菜单构建                                                     │
│  └─ 悬停效果/选中逻辑                                                │
├─────────────────────────────────────────────────────────────────────┤
│                        业务层 (service/)                             │
│  FileOperationService     ImageService       DirectoryService        │
│  ├─ 批量删除               ├─ 缩略图加载      ├─ 子目录列表           │
│  ├─ 剪贴板管理             ├─ 原图加载         ├─ 系统根目录           │
│  ├─ 粘贴/重命名            ├─ 图片路径获取     ├─ 图片目录             │
│  └─ 目录统计               └─ 缓存管理         └─ 系统目录过滤         │
│  NavigationService        DirectoryTreeService                       │
│  ├─ 前进/后退历史栈        └─ TreeView 异步目录加载                   │
│  └─ 路径解析                                                          │
├─────────────────────────────────────────────────────────────────────┤
│  策略层 (strategy/)                                                  │
│  RenderStrategy                                                    │
│  └─ 渐进式渲染（50文件/批，Timeline 60fps 驱动）                     │
├─────────────────────────────────────────────────────────────────────┤
│                        数据层 (repository/)                          │
│  FileRepository           ImageRepository                           │
│  ├─ 文件列表/排序          ├─ LRU 缓存（500条）                      │
│  ├─ 删除/重命名/复制       ├─ 异步缩略图加载                         │
│  ├─ 图片检测               └─ 3线程守护线程池                         │
│  └─ 大小格式化                                                        │
└─────────────────────────────────────────────────────────────────────┘
```

### 关键设计模式

- **线程池**：3 线程图片加载池（ImageRepositoryImpl）、2 线程目录扫描池（DirectoryTreeService），均为守护线程
- **渐进式渲染**：大目录（600+ 文件）由 RenderStrategy 驱动分批构建（50/批）和渲染（50/批），防止 UI 卡顿
- **LRU 图片缓存**：ImageRepositoryImpl 中的同步 `LinkedHashMap`，容量 500 条
- **选中模型**：Ctrl+左键多选切换；左键/右键单选。右键菜单根据选中数量动态调整可用项

### 资源文件

- FXML 布局：`main.fxml`、`slideShow.fxml`
- 图标：`src/main/resources/icons/`（back、forward、play、folder、file、hard-drive）
- 样式：`src/main/resources/style.css`

---

## 项目分工（两人协作）

### 总体分工原则

根据项目的三层架构，两人按 **"前端交互 + UI"** 和 **"后端业务 + 数据"** 进行分工：

| 角色 | 成员 A — 前端/UI 工程师 | 成员 B — 后端/数据工程师 |
|------|------------------------|------------------------|
| 定位 | 表示层 + 工具层 + 策略层 | 业务层 + 数据层 |
| 关注点 | 用户交互、界面布局、视觉效果、渲染性能 | 业务逻辑、文件操作、数据缓存、线程安全 |

---

### 成员 A：前端/UI 工程师

> 负责所有与用户直接交互相关的代码，包括界面布局、事件处理、视觉样式和渲染策略。

#### 负责的源文件

| 文件 | 路径 | 职责说明 |
|------|------|---------|
| `MainController` | `controller/MainController.java` | 主窗口控制器：目录树交互、文件卡片展示、导航栏、路径输入、右键菜单触发、状态栏更新、幻灯片入口 |
| `SlideShowController` | `controller/SlideShowController.java` | 幻灯片控制器：图片缩放（0.1x~10x）、鼠标拖拽平移、自动播放（Timeline 1秒间隔）、前后翻页 |
| `VBoxFactory` | `util/VBoxFactory.java` | 卡片工厂：创建文件/图片卡片 VBox、异步缩略图加载触发、悬停动画（translateY + dropShadow）、文件名截断（18字符）、选中逻辑（Ctrl 多选）、右键菜单构建 |
| `AlterUtil` | `util/AlterUtil.java` | Alert 弹窗封装：Platform.runLater 线程安全弹窗 |
| `RenderStrategy` | `strategy/RenderStrategy.java` | 渐进式渲染接口定义 |
| `RenderStrategyImpl` | `strategy/impl/RenderStrategyImpl.java` | 渐进式渲染实现：Timeline 60fps 驱动、50文件阈值触发、分批构建和渲染管线 |
| `main.fxml` | `resources/main.fxml` | 主窗口布局：BorderPane + 工具栏 + SplitPane（TreeView + FlowPane）+ 状态栏 |
| `slideShow.fxml` | `resources/slideShow.fxml` | 幻灯片布局：BorderPane + ImageView + 控制按钮栏 |
| `style.css` | `resources/style.css` | 全局样式：灰色+青色主题、按钮/输入框/树视图/滚动条/右键菜单样式 |
| 图标资源 | `resources/icons/*.png` | 导航图标、文件夹/文件图标、硬盘图标、播放图标 |

#### 具体任务清单

**1. 主窗口控制器（MainController）**

- 管理 `TreeView` 目录树与 `FlowPane` 文件面板的联动
- 实现工具栏功能：后退/前进按钮、路径输入框回车跳转
- 实现文件懒加载：初始加载 120 个，滚动到底部追加 60 个
- 实现异步缩略图加载：通过 ImageService 提交到线程池
- 实现文件选中模型：Ctrl+左键多选切换、左键/右键单选
- 实现右键菜单：根据选中数量动态启用/禁用菜单项（删除、复制、重命名、粘贴）
- 实现快捷入口：磁盘根目录和图片文件夹的快捷 VBox
- 实现状态栏：显示目录图片数量、总大小、选中数量
- 实现幻灯片窗口的打开和数据传递
- 实现应用关闭时的资源清理（调用 shutdown）

**2. 幻灯片控制器（SlideShowController）**

- 实现图片列表接收和当前索引管理
- 实现前后翻页（循环：最后一张→第一张，第一张→最后一张）
- 实现鼠标滚轮缩放（范围 0.1x ~ 10x，以鼠标位置为中心）
- 实现鼠标拖拽平移
- 实现自适应窗口（只缩小不放大）
- 实现自动播放（Timeline 1秒间隔，支持播放/暂停切换）
- 实现异步图片加载和错误提示

**3. UI 工具类（VBoxFactory）**

- 创建基础 VBox 卡片节点（图标 + 文件名标签）
- 实现文件名智能截断（超过 18 字符加省略号）
- 实现异步文件卡片和图片卡片创建
- 实现悬停效果（Y 轴上移 + 阴影，超过 500 文件时禁用以保性能）
- 实现选中逻辑和视觉反馈
- 实现右键菜单构建（动态启用/禁用策略）
- 创建磁盘根目录和图片文件夹的快捷卡片

**4. 渐进式渲染策略（RenderStrategyImpl）**

- 实现阈值判断（50 文件以上启用渐进渲染）
- 实现构建管线：Timeline 60fps，每帧处理一批任务
- 实现渲染管线：Timeline 60fps，每帧渲染一批到 FlowPane
- 实现 stopAll() 管线停止和资源清理

**5. 界面资源**

- 维护 `main.fxml` 布局结构和控件绑定
- 维护 `slideShow.fxml` 布局结构和控件绑定
- 维护 `style.css` 全局视觉样式
- 管理图标资源文件

#### 依赖的接口（由成员 B 提供）

成员 A 的代码通过构造器注入调用以下 Service 接口，**无需关心内部实现**：

```java
// MainController 注入的 6 个依赖
FileOperationService   // 文件操作（删除、复制、粘贴、重命名、统计）
ImageService           // 图片加载（缩略图、原图、缓存）
DirectoryService       // 目录操作（子目录列表、根目录、图片目录）
NavigationService      // 导航历史（前进、后退、路径解析）
DirectoryTreeService   // 目录树 UI 交互
RenderStrategy         // 渐进式渲染

// SlideShowController 直接使用
ImageService           // 原图加载
AlterUtil              // 错误弹窗
```

---

### 成员 B：后端/数据工程师

> 负责所有业务逻辑和数据访问层的代码，确保文件操作的正确性、数据缓存的高效性和线程安全性。

#### 负责的源文件

| 文件 | 路径 | 职责说明 |
|------|------|---------|
| `FileOperationService` | `service/FileOperationService.java` | 文件操作接口定义 |
| `FileOperationServiceImpl` | `service/impl/FileOperationServiceImpl.java` | 文件操作实现：批量删除、剪贴板管理、粘贴、重命名、目录统计 |
| `ImageService` | `service/ImageService.java` | 图片服务接口定义 |
| `ImageServiceImpl` | `service/impl/ImageServiceImpl.java` | 图片服务实现：缩略图/原图加载委托、图片检测、缓存管理 |
| `DirectoryService` | `service/DirectoryService.java` | 目录服务接口定义 |
| `DirectoryServiceImpl` | `service/impl/DirectoryServiceImpl.java` | 目录服务实现：子目录过滤、系统目录识别、排序 |
| `NavigationService` | `service/NavigationService.java` | 导航服务接口定义 |
| `NavigationServiceImpl` | `service/impl/NavigationServiceImpl.java` | 导航服务实现：双栈前进/后退、路径解析验证 |
| `DirectoryTreeService` | `service/DirectoryTreeService.java` | 目录树服务：TreeView 节点管理、异步目录扫描、路径展开 |
| `FileRepository` | `repository/FileRepository.java` | 文件仓库接口定义 |
| `FileRepositoryImpl` | `repository/impl/FileRepositoryImpl.java` | 文件仓库实现：文件列表、删除、重命名、复制（自动后缀）、大小格式化 |
| `ImageRepository` | `repository/ImageRepository.java` | 图片仓库接口定义 |
| `ImageRepositoryImpl` | `repository/impl/ImageRepositoryImpl.java` | 图片仓库实现：LRU 缓存（500条）、3线程池异步加载、缩略图/原图加载 |
| `ImageManagerProApplication` | `ImageManagerProApplication.java` | 应用入口：Spring 上下文启动、FXMLLoader 控制器工厂、资源清理 |
| `App.java` | `App.java` | 备用入口：IDE 兼容启动类 |

#### 具体任务清单

**1. 文件操作服务（FileOperationServiceImpl）**

- 实现批量文件删除（返回成功/失败数量）
- 实现内存剪贴板管理（`List<File>` 存储）
- 实现文件粘贴到目标目录（委托 Repository 复制，不自动清空剪贴板）
- 实现文件重命名（委托 Repository，处理冲突）
- 实现目录统计（递归计算图片数量和总字节大小）
- 维护接口与实现的分离（`FileOperationService` 接口 + `FileOperationServiceImpl`）

**2. 图片服务（ImageServiceImpl）**

- 实现缩略图加载委托（带缓存命中检查）
- 实现原图加载委托（不缓存）
- 实现图片文件检测（.jpg, .jpeg, .png, .gif, .bmp 扩展名）
- 实现目录内所有图片路径获取
- 实现缓存清理和线程池访问
- 维护接口与实现的分离

**3. 目录服务（DirectoryServiceImpl）**

- 实现子目录列表获取（过滤系统目录和隐藏目录）
- 实现系统目录黑名单过滤（System Volume Information、$Recycle.Bin、Windows、Program Files）
- 实现目录排序（按名称不区分大小写）
- 实现系统根目录获取（`File.listRoots()`）
- 实现用户图片目录获取（`user.home` + Pictures）
- 维护接口与实现的分离

**4. 导航服务（NavigationServiceImpl）**

- 实现双栈导航模型（`Stack<File>` backStack + forwardStack）
- 实现 `navigateTo()`：当前目录压入后退栈，清空前进栈
- 实现 `goBack()`：后退栈弹出，当前目录压入前进栈
- 实现 `goForward()`：前进栈弹出，当前目录压入后退栈
- 实现 `goUp()`：导航到父目录
- 实现 `resolvePath()`：验证路径字符串是否指向有效目录
- 维护接口与实现的分离

**5. 目录树服务（DirectoryTreeService）**

- 初始化"我的电脑"根节点，添加磁盘驱动器子节点
- 实现异步目录加载（2 线程守护线程池）
- 实现节点加载状态跟踪（`ConcurrentHashMap`：unloaded/loading/loaded）
- 实现系统目录和隐藏文件过滤
- 实现递归深度限制（最大 5 层）
- 实现递归路径展开（`PauseTransition` 非阻塞等待，最大 15 深度，10 次重试）
- 实现完整路径重建

**6. 文件仓库（FileRepositoryImpl）**

- 实现可见文件列表（过滤隐藏文件和点前缀文件，目录优先排序）
- 实现文件删除（返回 boolean 成功/失败）
- 实现文件重命名（处理同名冲突）
- 实现文件复制到目录（自动数字后缀：`file(1).txt`、`file(2).txt`）
- 实现图片文件检测
- 实现文件大小格式化（B/KB/MB/GB）
- 实现目录内图片路径获取（排序）
- 维护接口与实现的分离

**7. 图片仓库（ImageRepositoryImpl）**

- 实现 LRU 缓存（`LinkedHashMap` access-order，最大 500 条，同步包装）
- 实现 3 线程守护线程池
- 实现异步缩略图加载（指定尺寸，保持宽高比，加载后缓存）
- 实现原图加载（原始尺寸，不缓存）
- 实现缓存清理和线程池关闭
- 维护接口与实现的分离

**8. 应用入口（ImageManagerProApplication）**

- 实现 `init()`：启动 Spring 上下文
- 实现 `start()`：FXMLLoader 加载、控制器工厂设置、样式表应用、Stage 配置
- 实现 `stop()`：调用 controller.shutdown() 清理线程池、关闭 Spring 上下文

#### 提供给成员 A 的接口契约

成员 B 需要确保以下接口的**签名和行为稳定**，因为成员 A 的 Controller 直接依赖它们：

```java
public interface FileOperationService {
    int deleteFiles(Set<File> files);
    void copyToClipboard(Set<File> files);
    List<File> getClipboardFiles();
    void pasteFiles(File targetDir);
    boolean renameFile(File file, String newName);
    long[] calculateDirStats(File dir);
}

public interface ImageService {
    Image loadThumbnail(File file, double width, double height);
    Image loadFullImage(File path);
    List<String> getImagePaths(File directory);
    boolean isImageFile(File file);
    void clearCache();
    ExecutorService getExecutorService();
    Image getCachedImage(String key);
    void putCachedImage(String key, Image image);
}

public interface DirectoryService {
    File[] listChildDirectories(File parent);
    File[] getSystemRoots();
    File getPicturesDirectory();
    boolean isSystemDirectory(File dir);
}

public interface NavigationService {
    void navigateTo(File dir);
    File goUp();
    File goBack();
    File goForward();
    boolean hasBackHistory();
    boolean hasForwardHistory();
    File getCurrentDirectory();
    void setCurrentDirectory(File dir);
    void clearHistory();
    void pushBackStack(File dir);
    File resolvePath(String pathStr);
}

public interface FileRepository {
    File[] listVisibleFiles(File dir);
    boolean deleteFile(File file);
    boolean renameFile(File file, String newName);
    File copyFileTo(File file, File targetDir);
    boolean isImageFile(File file);
    boolean isVisibleFile(File file);
    String formatSize(long bytes);
    List<String> getImagePaths(File directory);
}

public interface ImageRepository {
    Image loadThumbnail(File file, double width, double height);
    Image loadFullImage(File path);
    Image getCachedImage(String key);
    void putCachedImage(String key, Image image);
    void clearCache();
    ExecutorService getExecutorService();
    void shutdown();
}
```

---

### 协作边界与接口约定

#### 依赖关系图

```
成员 A 的代码                          成员 B 的代码
─────────────────                    ─────────────────
MainController ──────注入──────────→ FileOperationService (接口)
                 ├────注入──────────→ ImageService (接口)
                 ├────注入──────────→ DirectoryService (接口)
                 ├────注入──────────→ NavigationService (接口)
                 ├────注入──────────→ DirectoryTreeService (具体类)
                 └────注入──────────→ RenderStrategy (接口)

SlideShowController ─直接使用──────→ ImageService (接口)

VBoxFactory ────────无依赖──────────→ (纯 UI 工具)

RenderStrategyImpl ──无依赖─────────→ (纯渲染策略)
```

#### 协作规则

1. **接口先行**：成员 B 先定义好 Service 和 Repository 的接口签名，成员 A 基于接口开发 Controller
2. **接口稳定**：接口一旦确定，修改需双方协商。实现类内部重构不影响对方
3. **独立编译**：成员 A 的代码只依赖接口，不依赖实现类；成员 B 可以独立修改实现逻辑
4. **测试隔离**：成员 B 为所有 Service/Repository 编写单元测试；成员 A 侧重手动 UI 测试
5. **FXML 绑定**：成员 A 负责 FXML 中 `fx:id` 与 Controller 中 `@FXML` 字段的一致性
6. **中文规范**：双方均遵守界面文字使用中文的规范

#### 共享文件（需协商修改）

| 文件 | 说明 | 协商规则 |
|------|------|---------|
| `pom.xml` | Maven 依赖配置 | 添加依赖需双方确认 |
| `application.properties` | 应用配置 | 修改需通知对方 |
| `ImageManagerProApplication.java` | 应用入口 | 成员 B 主要维护，成员 A 如需修改 Stage 配置需协商 |

---

### 建议的 Git 分支策略

```
master ─────────────────────────────────────────→ 主分支（稳定版本）
  │
  ├── feature/frontend-ui ──→ 成员 A 的功能分支
  │     ├── feat/main-controller
  │     ├── feat/slideshow-controller
  │     ├── feat/vbox-factory
  │     ├── feat/render-strategy
  │     └── feat/ui-style
  │
  └── feature/backend-data ──→ 成员 B 的功能分支
        ├── feat/file-operation-service
        ├── feat/image-service
        ├── feat/directory-service
        ├── feat/navigation-service
        ├── feat/file-repository
        ├── feat/image-repository
        └── feat/directory-tree-service
```

#### 合并流程

1. 各自从 `master` 创建功能分支
2. 在各自的功能分支上开发和提交
3. 成员 B 先合并接口定义到 `master`
4. 成员 A 基于最新 `master` 的接口开发
5. 双方完成开发后分别向 `master` 发起 Pull Request
6. Code Review 后合并

---

### 开发里程碑建议

| 阶段 | 成员 A 任务 | 成员 B 任务 | 交付物 |
|------|------------|------------|--------|
| **阶段 1：基础框架** | main.fxml 布局搭建、style.css 基础样式 | 所有接口定义、Repository 实现 | 可编译运行的空壳 UI + 可测试的数据层 |
| **阶段 2：核心功能** | MainController 核心交互、VBoxFactory 卡片 | Service 实现、DirectoryTreeService | 目录浏览 + 文件列表基本可用 |
| **阶段 3：文件操作** | 右键菜单 UI、选中模型 | FileOperationService 完整实现 | 复制/粘贴/删除/重命名可用 |
| **阶段 4：图片功能** | SlideShowController、缩略图异步加载 | ImageService + ImageRepository 缓存 | 图片预览 + 幻灯片可用 |
| **阶段 5：性能优化** | RenderStrategy 渐进渲染、大目录优化 | 线程池调优、缓存策略 | 大目录（600+ 文件）流畅浏览 |
| **阶段 6：测试收尾** | 手动测试 checklist、UI 细节打磨 | 单元测试覆盖、边界情况处理 | 全部测试通过、可打包发布 |

---

## 编码规范

- 界面文字全部使用中文（窗口标题、标签、提示、右键菜单）
- Service/Repository 使用接口 + 实现类，面向接口编程
- Controller 通过构造器注入 Service 接口
- FXML 绑定使用 `@FXML` 注解
- 代码内使用中文注释说明逻辑
- 提交信息使用中文，格式：`feat: 功能描述` / `fix: 修复描述` / `refactor: 重构描述`
