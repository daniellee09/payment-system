package com.example.payment.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 프론트엔드 대시보드에서 API를 호출할 수 있도록 CORS를 허용한다.
 * 개발 환경 전용 설정이므로, 운영 환경에서는 allowedOrigins를 구체적으로 제한해야 한다.
 *
 * /dashboard/, /pos/ 경로 접근 시 각 디렉토리의 index.html로 포워딩한다.
 * Spring Boot는 정적 리소스 디렉토리에 대해 자동으로 index.html을 서빙하지 않기 때문이다.
 */
@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/dashboard/").setViewName("forward:/dashboard/index.html")
        registry.addViewController("/dashboard").setViewName("redirect:/dashboard/")
        registry.addViewController("/pos/").setViewName("forward:/pos/index.html")
        registry.addViewController("/pos").setViewName("redirect:/pos/")
    }
}
