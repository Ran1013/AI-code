package com.ran.aicode.constant;

public interface AppConstant {

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名（用于生成外部可访问的短链接）。
     * 说明：返回 http://localhost:8101/{deployKey}，再由网关控制器 301 重定向到实际资源。
     */
    String CODE_DEPLOY_HOST = "http://localhost:8101/example";

}
