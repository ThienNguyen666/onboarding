package com.bank.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
// import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "app.onboarding")
public class OnboardingProperties {

    private final Retry retry = new Retry();
    private final Otp otp = new Otp();
    private final Dropoff dropoff = new Dropoff();
    private final EkycMock ekycMock = new EkycMock();
    private final ComplianceMock complianceMock = new ComplianceMock();

    public Retry getRetry() { return retry; }
    public Otp getOtp() { return otp; }
    public Dropoff getDropoff() { return dropoff; }
    public EkycMock getEkycMock() { return ekycMock; }
    public ComplianceMock getComplianceMock() { return complianceMock; }

    public static class Retry {
        private int defaultMaxOcrRetries = 3;
        private int defaultMaxLivenessRetries = 3;
        private int defaultMaxNfcRetries = 3;

        public int getDefaultMaxOcrRetries() { return defaultMaxOcrRetries; }
        public void setDefaultMaxOcrRetries(int v) { this.defaultMaxOcrRetries = v; }
        public int getDefaultMaxLivenessRetries() { return defaultMaxLivenessRetries; }
        public void setDefaultMaxLivenessRetries(int v) { this.defaultMaxLivenessRetries = v; }
        public int getDefaultMaxNfcRetries() { return defaultMaxNfcRetries; }
        public void setDefaultMaxNfcRetries(int v) { this.defaultMaxNfcRetries = v; }
    }

    public static class Otp {
        private int length = 6;
        private int ttlSeconds = 300;
        private int maxVerifyAttempts = 5;
        private boolean debugEndpointEnabled = true;

        public int getLength() { return length; }
        public void setLength(int v) { this.length = v; }
        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int v) { this.ttlSeconds = v; }
        public int getMaxVerifyAttempts() { return maxVerifyAttempts; }
        public void setMaxVerifyAttempts(int v) { this.maxVerifyAttempts = v; }
        public boolean isDebugEndpointEnabled() { return debugEndpointEnabled; }
        public void setDebugEndpointEnabled(boolean v) { this.debugEndpointEnabled = v; }
    }

    public static class Dropoff {
        private int ttlHours = 24;
        public int getTtlHours() { return ttlHours; }
        public void setTtlHours(int v) { this.ttlHours = v; }
    }

    public static class EkycMock {
        private boolean alwaysPassByDefault = true;
        public boolean isAlwaysPassByDefault() { return alwaysPassByDefault; }
        public void setAlwaysPassByDefault(boolean v) { this.alwaysPassByDefault = v; }
    }

    public static class ComplianceMock {
        private String strategy = "RULE_BASED";
        public String getStrategy() { return strategy; }
        public void setStrategy(String v) { this.strategy = v; }
    }
}
