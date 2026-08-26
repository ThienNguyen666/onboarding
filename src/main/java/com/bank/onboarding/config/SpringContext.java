package com.bank.onboarding.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Cầu nối lấy Spring bean cho những object KHÔNG do Spring khởi tạo — cụ thể là
 * OnboardingConductorWorkers, bị Conductor SDK new() bằng reflection thông qua
 * WorkflowExecutor.initWorkers() nên không được @Autowired theo cách thường.
 */
@Component
public class SpringContext implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static <T> T bean(Class<T> type) {
        return context.getBean(type);
    }
}