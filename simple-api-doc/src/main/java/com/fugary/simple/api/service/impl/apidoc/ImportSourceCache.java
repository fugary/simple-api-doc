package com.fugary.simple.api.service.impl.apidoc;

import com.fugary.simple.api.service.apidoc.asset.DocAssetStorageService;
import com.fugary.simple.api.service.impl.apidoc.asset.DocAssetStorageServiceImpl;
import com.fugary.simple.api.service.impl.apidoc.asset.DocAssetStorageServiceImpl.PendingImage;
import com.fugary.simple.api.utils.JsonUtils;
import com.fugary.simple.api.utils.SimpleResultUtils;
import com.fugary.simple.api.utils.security.SecurityUtils;
import com.fugary.simple.api.web.vo.SimpleResult;
import com.fugary.simple.api.web.vo.imports.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.function.Supplier;

/** 仅用于显式预览的来源快照；不参与直接导入和定时同步。 */
@Service
@RequiredArgsConstructor
public class ImportSourceCache {
    private final DocAssetStorageService assets;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    @Value("${simple.api.import-preview-cache.ttl-millis:600000}")
    private long ttlMillis = 600000;
    @Value("${simple.api.import-preview-cache.max-entry-bytes:67108864}")
    private long maxEntryBytes = 67108864;
    @Value("${simple.api.import-preview-cache.max-total-bytes:268435456}")
    private long maxTotalBytes = 268435456;
    private long totalBytes;

    public SimpleResult<DocSourceData> getContent(ApiProjectImportVo options, boolean preview,
            Supplier<SimpleResult<DocSourceData>> download) {
        if (preview) { return download(options, download); }
        DocSourceData cached = StringUtils.isBlank(options.getPreviewKey()) ? null : reuse(options);
        if (cached != null) { return SimpleResultUtils.createSimpleResult(cached); }
        // 回退后不再消费原 key，避免确认成功时清除其他用户或其他参数的预览。
        options.setPreviewKey(null);
        return download.get();
    }

    private SimpleResult<DocSourceData> download(ApiProjectImportVo options,
            Supplier<SimpleResult<DocSourceData>> download) {
        List<PendingImage> images = new ArrayList<>();
        long[] imageBytes = {0};
        SimpleResult<DocSourceData> result = DocAssetStorageServiceImpl.captureImages(image -> {
            imageBytes[0] += image.getBytes().length;
            if (imageBytes[0] <= maxEntryBytes) { images.add(image); }
        }, download);
        if (!result.isSuccess()) { return result; }
        DocSourceData source = result.getResultData();
        if (source == null || source.isEmpty()) { return result; }
        // 文本按 UTF-16 上界计费；二进制不经 Base64 编解码。
        long size = imageBytes[0] + (source.isBinary() ? source.getBinaryContent().length
                : 2L * StringUtils.length(source.getTextContent()));
        if (size > maxEntryBytes || size > maxTotalBytes) {
            return result;
        }
        String key = UUID.randomUUID().toString();
        synchronized (this) {
            cleanExpired();
            while (!entries.isEmpty() && (totalBytes + size > maxTotalBytes || entries.size() >= 32)) {
                discard(entries.keySet().iterator().next());
            }
            entries.put(key, new Entry(fingerprint(options), copy(source), images, size,
                    System.currentTimeMillis() + ttlMillis));
            totalBytes += size;
        }
        options.setPreviewKey(key);
        return result;
    }

    /** 缓存只负责加速；失效、参数不匹配或图片恢复失败时回退原下载流程。 */
    private DocSourceData reuse(ApiProjectImportVo options) {
        Entry entry;
        synchronized (this) {
            cleanExpired();
            entry = entries.get(options.getPreviewKey());
            if (entry == null || !entry.fingerprint.equals(fingerprint(options))) { return null; }
        }
        for (PendingImage image : entry.images) {
            if (StringUtils.isBlank(assets.saveImage(image.getBytes(), image.getFileName(), image.getProjectCode()))) {
                discard(options.getPreviewKey());
                return null;
            }
        }
        return copy(entry.source);
    }

    public synchronized void discard(String key) {
        Entry removed = entries.remove(key);
        if (removed != null) { totalBytes -= removed.size; }
    }

    @Scheduled(fixedDelay = 60000)
    public synchronized void cleanExpired() {
        long now = System.currentTimeMillis();
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.expiresAt <= now) {
                totalBytes -= entry.size;
                iterator.remove();
            }
        }
    }

    @PreDestroy
    public synchronized void clear() {
        entries.clear();
        totalBytes = 0;
    }

    private String fingerprint(ApiProjectImportVo options) {
        ApiProjectTaskImportVo task = options instanceof ApiProjectTaskImportVo ? (ApiProjectTaskImportVo) options : null;
        // 已保存任务的文件名和项目属性由执行流程决定，前端只提交 taskId 与 key。
        boolean savedTask = task != null && task.getTaskId() != null;
        return DigestUtils.sha256Hex(JsonUtils.toJson(Arrays.asList(SecurityUtils.getLoginUserName(),
                options.getUrl(), options.getAuthType(), options.getAuthContent(), options.getImportType(), options.getSourceType(),
                savedTask ? null : options.getFileName(), savedTask ? null : options.getProjectName(),
                savedTask ? null : options.getIconUrl(), savedTask ? null : options.getGroupCode(),
                task == null ? null : task.getProjectId(), task == null ? null : task.getToFolder(),
                task == null ? null : task.getTaskId(), task == null ? null : task.getOverwriteMode())));
    }

    private DocSourceData copy(DocSourceData source) {
        return source.isBinary()
                ? DocSourceData.ofBinary(source.getBinaryContent().clone(), source.getFileName(), source.getContentType())
                : DocSourceData.builder().textContent(source.getTextContent()).fileName(source.getFileName())
                        .contentType(source.getContentType()).build();
    }

    @lombok.Value
    private static class Entry {
        String fingerprint;
        DocSourceData source;
        List<PendingImage> images;
        long size;
        long expiresAt;
    }
}
