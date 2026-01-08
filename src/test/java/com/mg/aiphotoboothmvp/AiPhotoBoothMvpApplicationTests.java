package com.mg.aiphotoboothmvp;



// Spring Boot测试核心注解
import com.mg.booth.AiPhotoBoothApplication;
import org.springframework.boot.test.context.SpringBootTest;
// JUnit 5的Test注解（Spring Boot 3.x默认用JUnit 5）
import org.junit.jupiter.api.Test;

/**
 * Spring Boot应用上下文测试类
 * 验证应用能否正常加载Spring上下文
 */
// 关键：显式指定启动类的全类名（必须替换成你项目里的启动类！！！）
@SpringBootTest(classes = AiPhotoBoothApplication.class)
public class AiPhotoBoothMvpApplicationTests {

    /**
     * 测试Spring上下文能否正常加载
     * 这是最基础的应用启动测试
     */
    @Test
    void contextLoads() {
        // 方法体为空即可，只要不报错，说明上下文加载成功
        // 若报错，大概率是启动类路径指定错误，或依赖缺失
    }
}