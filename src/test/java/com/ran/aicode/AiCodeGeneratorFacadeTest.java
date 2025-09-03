package com.ran.aicode;

import com.ran.aicode.core.AiCodeGeneratorFacade;
import jakarta.annotation.Resource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

//    @Test
//    void generateAndSaveCode() {
//        File file = aiCodeGeneratorFacade.generateAndSaveCode("任务记录网站", CodeGenTypeEnum.MULTI_FILE);
//        Assertions.assertNotNull(file);
//    }
}
