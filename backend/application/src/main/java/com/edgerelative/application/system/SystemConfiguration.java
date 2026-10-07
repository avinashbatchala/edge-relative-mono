package com.edgerelative.application.system;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TradingProperties.class)
public class SystemConfiguration {
}
