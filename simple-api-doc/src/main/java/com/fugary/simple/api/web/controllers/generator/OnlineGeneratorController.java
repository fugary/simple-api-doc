package com.fugary.simple.api.web.controllers.generator;

import com.fugary.simple.api.utils.exports.ApiDocParseUtils;
import com.fugary.simple.api.web.controllers.generator.store.GenerateResultStorage;
import com.fugary.simple.api.web.controllers.generator.store.GeneratedVo;
import com.fugary.simple.api.web.controllers.generator.vo.GenRequestVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.languages.AbstractJavaCodegen;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Create date 2025/10/27<br>
 *
 * @author gary.fu
 */
@RestController
@RequestMapping({"admin/gen", "shares/gen"})
public class OnlineGeneratorController {

    @Autowired
    private GenerateResultStorage genStorage;

    @GetMapping("/clients")
    public List<String> getClients() {
        return CodegenConfigLoader.getAll().stream()
                .filter(config -> config.getTag() == CodegenType.CLIENT)
                .map(CodegenConfig::getName)
                .collect(Collectors.toList());
    }

    // 获取所有 client generator
    @GetMapping("/clients/{language}")
    public Map<String, CliOption> getClientConfig(@PathVariable("language") String language) {
        return getGeneratorConfig(language);
    }

    @PostMapping("/clients/{language}")
    public Map<String, String> generateClient(
            HttpServletRequest request,
            @PathVariable String language,
            @RequestBody GenRequestVo requestVo) throws IOException {
        return generateAndOutput(request, requestVo, "client", language);
    }

    @GetMapping("/servers")
    public List<String> getServers() {
        return CodegenConfigLoader.getAll().stream()
                .filter(config -> config.getTag() == CodegenType.SERVER)
                .map(CodegenConfig::getName)
                .collect(Collectors.toList());
    }

    @GetMapping("/servers/{framework}")
    public Map<String, CliOption> getServerConfig(@PathVariable("framework") String framework) {
        return getGeneratorConfig(framework);
    }

    @PostMapping("/servers/{framework}")
    public Map<String, String> generateServer(
            HttpServletRequest request,
            @PathVariable String framework,
            @RequestBody GenRequestVo requestVo) throws IOException {
        return generateAndOutput(request, requestVo, "server", framework);
    }

    private Map<String, String> generateAndOutput(HttpServletRequest request, GenRequestVo requestVo, String type, String language) throws IOException {
        Path zipPath = generateToFile(requestVo, language, type);
        String uuid = genStorage.store(type, language, zipPath);
        String prefix = "/admin";
        if (request.getRequestURI().contains("/shares")) {
            prefix = "/shares";
        }
        return Map.of("code", uuid,
                "link", ApiDocParseUtils.getCurrentUrlPath(request, prefix + "/gen/download/" + uuid));
    }

    // 下载接口
    @GetMapping(value = "/download/{uuid}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> download(@PathVariable String uuid) throws IOException {
        GeneratedVo generatedVo = genStorage.get(uuid);
        if (generatedVo == null || !Files.exists(generatedVo.getPath())) {
            return ResponseEntity.notFound().build();
        }
        Path zipPath = generatedVo.getPath();
        Resource resource = new InputStreamResource(Files.newInputStream(zipPath));
        String fileName = StringUtils.join(List.of(generatedVo.getLanguage(), generatedVo.getType(), "generated.zip"), "-");
        // 可选择异步删除文件
        genStorage.remove(uuid);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 生成文件
     *
     * @param request
     * @param language
     * @param type
     * @return
     * @throws IOException
     */
    private Path generateToFile(GenRequestVo request, String language, String type) throws IOException {
        Path tempDir = Files.createTempDirectory("gen-" + type + "-");
        // 获取 spec 内容
        String specContent;
        if (request.getSpec() != null && !request.getSpec().isEmpty()) {
            specContent = new ObjectMapper().writeValueAsString(request.getSpec());
        } else if (request.getOpenAPIUrl() != null) {
            specContent = new String(new URL(request.getOpenAPIUrl()).openStream().readAllBytes(), StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("必须提供 spec 或 openAPIUrl");
        }
        Path tempSpecFile = Files.createTempFile("gen-temp-spec-", ".json");
        Files.write(tempSpecFile, specContent.getBytes(StandardCharsets.UTF_8));
        DefaultGenerator generator = new DefaultGenerator();
        CodegenConfigurator configurator = new CodegenConfigurator();
        configurator.setInputSpec(tempSpecFile.toAbsolutePath().toString());
        configurator.setGeneratorName(language);
        configurator.setOutputDir(tempDir.toAbsolutePath().toString());
        Object output = request.getOptions().get(AbstractJavaCodegen.TEST_OUTPUT);
        if (output instanceof CharSequence && StringUtils.contains((CharSequence) output, "${project.build.directory}")) {
            output = StringUtils.replace((String) output, "${project.build.directory}", tempDir.toAbsolutePath().toString());
            request.getOptions().put(AbstractJavaCodegen.TEST_OUTPUT, output);
        }
        configurator.setAdditionalProperties(request.getOptions());
        generator.opts(configurator.toClientOptInput()).generate();
        // 打包为 zip
        Path outputPath = ApiDocParseUtils.getApiTempDir().toPath();
        if (!Files.exists(outputPath)) {
            Files.createDirectory(outputPath);
        }
        Path zipPath = Files.createTempFile(outputPath, type + "-", ".zip");
        String innerZipFolder = StringUtils.join(language, "-", type, "/");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(tempDir).filter(Files::isRegularFile)
                    .forEach(file -> {
                        ZipEntry entry = new ZipEntry(innerZipFolder + tempDir.relativize(file).toString());
                        try {
                            zos.putNextEntry(entry);
                            Files.copy(file, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        FileUtils.deleteQuietly(tempSpecFile.toFile());
        FileUtils.deleteQuietly(tempDir.toFile());
        return zipPath;
    }

    private Map<String, CliOption> getGeneratorConfig(String language) {
        CodegenConfig config = CodegenConfigLoader.forName(language);
        if (config == null) {
            throw new IllegalArgumentException("Unknown generator: " + language);
        }
        Map<String, CliOption> result = new LinkedHashMap<>();
        for (CliOption opt : config.cliOptions()) {
            result.put(opt.getOpt(), opt);
        }
        return result;
    }
}


