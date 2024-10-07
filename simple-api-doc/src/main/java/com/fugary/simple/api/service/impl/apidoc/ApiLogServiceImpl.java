package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiLog;
import com.fugary.simple.api.mapper.api.ApiLogMapper;
import com.fugary.simple.api.service.apidoc.ApiLogService;
import org.springframework.stereotype.Service;

@Service
public class ApiLogServiceImpl extends ServiceImpl<ApiLogMapper, ApiLog> implements ApiLogService {
}
