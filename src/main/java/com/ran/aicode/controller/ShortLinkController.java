package com.ran.aicode.controller;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接跳转控制器：支持 http://localhost:8101/{deployKey}
 * 301 重定向到实际的静态资源地址 /example/static/{deployKey}/
 */
@RestController
public class ShortLinkController {

    @GetMapping("/{deployKey}")
    public ResponseEntity<Void> redirect(@PathVariable String deployKey) {
        String target = "/example/static/" + deployKey + "/";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", target);
        return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
    }
}


