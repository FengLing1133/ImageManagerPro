package com.yang;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.yang.controller.MainController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ImageManagerProApplication extends Application {

    private FXMLLoader fxmlLoader; // 用于加载FXML界面
    private ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * 在 JavaFX 应用生命周期的 init 阶段启动 Spring 上下文，这样可以在 FXML 控制器中注入 Spring Bean。
     */
    @Override
    public void init() throws Exception {
        // 使用 SpringApplicationBuilder 启动 Spring 上下文
        springContext = new SpringApplicationBuilder(ImageManagerProApplication.class).run();
        super.init();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        fxmlLoader = new FXMLLoader(getClass().getResource("/main.fxml")); // 加载主界面FXML
        // 让 FXMLLoader 使用 Spring 来创建控制器（从而可以注入 Spring Bean）
        if (springContext != null) {
            fxmlLoader.setControllerFactory(springContext::getBean);
        }
        Scene scene = new Scene(fxmlLoader.load()); // 创建场景
        java.net.URL styleUrl = getClass().getResource("/style.css"); // 获取样式表资源
        if (styleUrl != null) scene.getStylesheets().add(styleUrl.toExternalForm()); // 防止NullPointerException
        primaryStage.setTitle("图片管理器"); // 设置窗口标题
        primaryStage.setScene(scene); // 设置场景
        primaryStage.show(); // 显示窗口
    }

    @Override
    public void stop() throws Exception {
        super.stop(); // 调用父类的stop方法
        if (fxmlLoader != null) {
            MainController controller = fxmlLoader.getController(); // 获取主控制器
            if (controller != null) {
                controller.shutdown(); // 关闭线程池，释放资源
            }
        }
        if (springContext != null) {
            springContext.close();
        }
    }

}
