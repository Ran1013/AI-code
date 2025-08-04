package com.ran.aicode.service.impl;

import com.ran.aicode.mapper.AppMapper;
import com.ran.aicode.model.entity.App;
import com.ran.aicode.service.AppService;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author <a>ran</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService {

}
