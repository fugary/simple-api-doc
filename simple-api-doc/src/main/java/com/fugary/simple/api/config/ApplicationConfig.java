package com.fugary.simple.api.config;

import com.fugary.simple.api.interceptors.TempFileCleanInterceptor;
import com.fugary.simple.api.security.ShareSecurityInterceptor;
import com.fugary.simple.api.security.UserSecurityInterceptor;
import com.fugary.simple.api.utils.http.SimpleHttpClientUtils;
import com.fugary.simple.api.web.filters.locale.CustomHeaderLocaleContextResolver;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fugary.simple.api.contants.ApiDocConstants.ALL_PATH_PATTERN;

/**
 * Created on 2020/5/5 20:44 .<br>
 *
 * @author gary.fu
 */
@Configuration
@EnableConfigurationProperties({SimpleApiConfigProperties.class})
public class ApplicationConfig implements WebMvcConfigurer {

    @Autowired
    private SimpleApiConfigProperties simpleApiConfigProperties;

    @Bean
    public CommonsMultipartResolver multipartResolver() {
        CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver();
        multipartResolver.setMaxUploadSize(5 * 1024 * 1024); // 5MB
        multipartResolver.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return multipartResolver;
    }

    @Bean
    public LocaleResolver localeResolver(){
        CustomHeaderLocaleContextResolver headerLocaleResolver = new CustomHeaderLocaleContextResolver();
        headerLocaleResolver.setDefaultLocale(Locale.CHINA);
        headerLocaleResolver.setSupportedLocales(Arrays.asList(Locale.CHINA, Locale.US));
        return headerLocaleResolver;
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ALL_PATH_PATTERN, getCorsConfiguration());
        return new CorsFilter(source);
    }

    protected CorsConfiguration getCorsConfiguration() {
        final CorsConfiguration config = new CorsConfiguration();
        config.setAllowedMethods(Stream.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST,
                        HttpMethod.OPTIONS, HttpMethod.PATCH, HttpMethod.PUT)
                .map(Enum::name).collect(Collectors.toList()));
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setExposedHeaders(Collections.singletonList("Content-Disposition"));
        config.setMaxAge(7 * 24 * 60 * 60L); // 设置跨域check缓存时间
        return config;
    }

    @Bean
    public RestTemplate restTemplate() {
        ClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(
                SimpleHttpClientUtils.getHttpsClient());
        RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory);
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }

    @Bean
    public UserSecurityInterceptor userSecurityInterceptor(){
        return new UserSecurityInterceptor();
    }

    @Bean
    public ShareSecurityInterceptor shareSecurityInterceptor(){
        return new ShareSecurityInterceptor();
    }

    @Bean
    public TempFileCleanInterceptor tempFileCleanInterceptor(){
        return new TempFileCleanInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userSecurityInterceptor()).addPathPatterns("/admin/**")
                .addPathPatterns("/upload/uploadFiles");
        registry.addInterceptor(shareSecurityInterceptor()).addPathPatterns("/shares/**")
                .excludePathPatterns("/shares/loadShare/**");
        registry.addInterceptor(tempFileCleanInterceptor()).addPathPatterns("/**");
    }

    @Bean
    public OpenApiCustomiser openApiCustomiser() {
        return openApi -> {
            openApi.getInfo().setVersion(simpleApiConfigProperties.getProjectVersion());
            openApi.addSecurityItem(new SecurityRequirement()
                    .addList("AccessToken", "/admin/**"));
            if (openApi.getComponents() != null) {
                openApi.getComponents().addSecuritySchemes("AccessToken", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).name("Authorization").scheme("Bearer").bearerFormat("JWT")
                        .in(SecurityScheme.In.HEADER)
                        .description("Authorization header using login access token. Example: \"Authorization: Bearer {token}\""));
            }
        };
    }
}
