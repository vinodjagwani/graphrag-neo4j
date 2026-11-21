package demo.graphrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("demo")
public record DemoProperties(
        @DefaultValue("3") int vectorTopK,
        @DefaultValue("3") int graphHops,
        @DefaultValue("3") int graphTopPaths) {
}
