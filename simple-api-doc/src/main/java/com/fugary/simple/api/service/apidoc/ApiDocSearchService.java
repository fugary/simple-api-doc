package com.fugary.simple.api.service.apidoc;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fugary.simple.api.entity.api.ApiProject;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.web.vo.doc.ApiDocSearchResultVo;
import com.fugary.simple.api.web.vo.query.ApiDocSearchQueryVo;

public interface ApiDocSearchService {
    Page<ApiDocSearchResultVo> search(ApiDocSearchQueryVo query, ApiProjectShare share);

    Page<ApiProject> searchProjects(ApiDocSearchQueryVo query);
}
