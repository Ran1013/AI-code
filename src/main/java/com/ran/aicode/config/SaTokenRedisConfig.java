package com.ran.aicode.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token Redis 配置类
 * 确保 Sa-Token 正确使用 Redis 存储 token 和会话信息
 */
@Configuration
@Slf4j
public class SaTokenRedisConfig implements WebMvcConfigurer {

    /**
     * 注册 Sa-Token 拦截器，打开注解式鉴权功能
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 暂时禁用拦截器，使用过滤器进行认证
        // registry.addInterceptor(new SaInterceptor());
    }

    /**
     * 注册 Sa-Token 全局过滤器
     */
    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
                .addInclude("/**")
                .addExclude("/favicon.ico")
                .addExclude("/doc.html")
                .addExclude("/webjars/**")
                .addExclude("/swagger-resources/**")
                .addExclude("/v3/api-docs/**")
                .addExclude("/v3/api-docs/swagger-config")
                .addExclude("/swagger-ui.html")
                .addExclude("/swagger-ui/**")
                .addExclude("/swagger-ui/index.html")
                .addExclude("/v2/api-docs/**")
                .addExclude("/knife4j/**")
                .addExclude("/inner/screenshot/**")
                .addExclude("/static/**")
                .setAuth(obj -> {
                    // 登录认证 -- 拦截所有路由，并排除 /user/login 等接口
                    SaRouter.match("/**")
                            .notMatch("/user/sendVerify","/user/login", "/user/register", "/user/get/login")
                            .notMatch("/doc.html", "/webjars/**", "/swagger-resources/**", "/v3/api-docs/**")
                            .notMatch("/v3/api-docs/swagger-config")
                            .notMatch("/swagger-ui.html", "/swagger-ui/**", "/swagger-ui/index.html")
                            .notMatch("/v2/api-docs/**")
                            .notMatch("/knife4j/**")
                            .notMatch("/inner/screenshot/**")
                            .notMatch("/static/**")
                            .notMatch("/ws/**") // WebSocket 连接路径
                            .check(r -> StpUtil.checkLogin());
                })
                .setError(e -> {
                    log.error("Sa-Token 认证失败: {}", e.getMessage());
                    return SaResult.error(e.getMessage());
                })
                .setBeforeAuth(obj -> {
                    // 设置跨域响应头
                    SaHolder.getResponse()
                            .setHeader("Access-Control-Allow-Origin", "*")
                            .setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                            .setHeader("Access-Control-Allow-Headers", "*")
                            .setHeader("Access-Control-Allow-Credentials", "true");

                    // 如果是预检请求，直接返回
                    if ("OPTIONS".equals(SaHolder.getRequest().getMethod())) {
                        SaRouter.stop();
                    }
                });
    }

    /**
     * Sa-Token 结果包装类
     */
    public static class SaResult {
        public static Object error(String message) {
            return new BaseResponse<>(40100, null, message);
        }
    }

    /**
     * 基础响应类
     */
    public static class BaseResponse<T> {
        private int code;
        private T data;
        private String message;

        public BaseResponse(int code, T data, String message) {
            this.code = code;
            this.data = data;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}