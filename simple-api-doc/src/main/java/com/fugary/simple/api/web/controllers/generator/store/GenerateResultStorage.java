package com.fugary.simple.api.web.controllers.generator.store;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Create date 2025/10/27<br>
 *
 * @author gary.fu
 */
@Component
public class GenerateResultStorage {

    // Map<UUID, GeneratedVo> 存储临时文件位置
    private final Map<String, GeneratedVo> storage = new ConcurrentHashMap<>();

    public String store(String type, String language, Path zipPath) {
        String uuid = UUID.randomUUID().toString();
        storage.put(uuid, new GeneratedVo(type, language, zipPath));
        return uuid;
    }

    public GeneratedVo get(String uuid) {
        return storage.get(uuid);
    }

    public void remove(String uuid) {
        GeneratedVo remove = storage.remove(uuid);
        if (remove != null) {
            try {
                Files.walk(remove.getPath())
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {}
        }
    }
}
