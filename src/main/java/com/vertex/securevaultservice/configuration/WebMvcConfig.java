package com.vertex.securevaultservice.configuration;

import com.vertex.securevaultservice.interceptor.CachedBodyFilter;
import com.vertex.securevaultservice.interceptor.HmacRequestInterceptor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private static final String VAULT_PATH_PATTERN = "/api/v1/vault/**";

    private final HmacRequestInterceptor hmacRequestInterceptor;

    public WebMvcConfig(HmacRequestInterceptor hmacRequestInterceptor) {
        this.hmacRequestInterceptor = hmacRequestInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(hmacRequestInterceptor).addPathPatterns(VAULT_PATH_PATTERN);
    }

    @Bean
    public FilterRegistrationBean<CachedBodyFilter> cachedBodyFilter() {
        FilterRegistrationBean<CachedBodyFilter> registration = new FilterRegistrationBean<>(new CachedBodyFilter());
        registration.addUrlPatterns("/api/v1/vault/*");
        registration.setOrder(0);
        return registration;
    }
}
