package br.com.phfinance.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI financeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Finance Control API")
                        .description("Personal finance control application API")
                        .version("1.0.0"));
    }
}
