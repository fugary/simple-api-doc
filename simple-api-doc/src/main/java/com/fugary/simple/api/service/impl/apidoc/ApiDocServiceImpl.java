package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiDoc;
import com.fugary.simple.api.mapper.api.ApiDocMapper;
import com.fugary.simple.api.service.apidoc.ApiDocService;
import org.springframework.stereotype.Service;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiDocServiceImpl extends ServiceImpl<ApiDocMapper, ApiDoc> implements ApiDocService {
}
