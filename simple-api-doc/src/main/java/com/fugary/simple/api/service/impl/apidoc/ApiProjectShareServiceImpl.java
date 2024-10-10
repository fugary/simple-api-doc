package com.fugary.simple.api.service.impl.apidoc;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fugary.simple.api.entity.api.ApiProjectShare;
import com.fugary.simple.api.mapper.api.ApiProjectShareMapper;
import com.fugary.simple.api.service.apidoc.ApiProjectShareService;
import com.fugary.simple.api.utils.SimpleModelUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Create date 2024/9/23<br>
 *
 * @author gary.fu
 */
@Service
public class ApiProjectShareServiceImpl extends ServiceImpl<ApiProjectShareMapper, ApiProjectShare> implements ApiProjectShareService {
    @Override
    public boolean deleteByProject(Integer projectId) {
        return this.remove(Wrappers.<ApiProjectShare>query().eq("project_id", projectId));
    }

    @Override
    public ApiProjectShare loadByShareId(String shareId) {
        return this.getOne(Wrappers.<ApiProjectShare>query().eq("share_id", shareId));
    }

    @Override
    public int copyProjectShares(Integer fromProjectId, Integer toProjectId, Integer id) {
        List<ApiProjectShare> shares = list(Wrappers.<ApiProjectShare>query()
                .eq("project_id", fromProjectId)
                .eq(id != null, "id", id));
        shares.forEach(share -> {
            share.setId(null);
            share.setShareId(SimpleModelUtils.uuid());
            share.setProjectId(toProjectId);
            if (fromProjectId.equals(toProjectId)) {
                share.setShareName(share.getShareName() + "-copy");
            }
            save(share);
        });
        return shares.size();
    }
}
