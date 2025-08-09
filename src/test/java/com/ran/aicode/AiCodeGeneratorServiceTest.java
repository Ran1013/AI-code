package com.ran.aicode;

import com.ran.aicode.ai.AiCodeGenNameService;
import com.ran.aicode.ai.AiCodeGenTypeRoutingServiceFactory;
import com.ran.aicode.ai.AiCodeGeneratorService;
import com.ran.aicode.ai.model.HtmlCodeResult;
import com.ran.aicode.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;
    @Resource
    private AiCodeGenNameService aiCodeGenNameService;
    @Test
    void generateHtmlCode() {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("ran个人博客网站");
        Assertions.assertNotNull(result);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult multiFileCode = aiCodeGeneratorService.generateMultiFileCode("ran个人博客网站");
        Assertions.assertNotNull(multiFileCode);
    }
    @Test
    void generateNameCode() {
        String result = aiCodeGenNameService.generateAppName("ran个人博客网站");

        System.out.println("123" + result+"321");
        Assertions.assertNotNull(result);
    }

}
