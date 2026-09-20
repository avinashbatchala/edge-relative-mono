package com.edgerelative.application.reference;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the configured instrument-to-sector mapping used by the reference seeder. */
@Configuration
@EnableConfigurationProperties(SectorMappingProperties.class)
public class SectorReferenceConfiguration {
}
