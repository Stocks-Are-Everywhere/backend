// package org.scoula.backend.global.config;
//
// import org.apache.coyote.http11.Http11NioProtocol;
// import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
// import org.springframework.boot.web.server.WebServerFactoryCustomizer;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
//
// @Configuration
// public class TomcatConfig {
//
// 	@Bean
// 	public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
// 		return (factory) -> {
// 			factory.addConnectorCustomizers(connector -> {
// 				Http11NioProtocol protocol = (Http11NioProtocol)connector.getProtocolHandler();
//
// 				// 핵심 설정만 적용
// 				protocol.setMaxThreads(200);
// 				protocol.setMaxConnections(10000);
// 				protocol.setConnectionTimeout(5000);
// 				protocol.setAcceptCount(500);
// 				protocol.setKeepAliveTimeout(30000);
// 				protocol.setMaxKeepAliveRequests(100);
//
// 			});
// 		};
// 	}
// }
