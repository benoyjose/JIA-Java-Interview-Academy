package com.jia.academy.appregistry.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AcademyAppContextWebConfiguration implements WebMvcConfigurer {
    private final AcademyAppContextInterceptor interceptor;

    public AcademyAppContextWebConfiguration(AcademyAppContextInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**");
    }
}
