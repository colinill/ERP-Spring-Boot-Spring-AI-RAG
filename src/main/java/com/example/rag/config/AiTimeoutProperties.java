package com.example.rag.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型调用超时配置。
 * <p>
 * 对应 application.yml 中的 app.ai 配置：
 * <ul>
 *   <li>callTimeout：非流式模型调用超时时间</li>
 *   <li>streamTimeout：流式模型调用超时时间</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiTimeoutProperties {

	/** 非流式模型调用超时时间。 */
	private Duration callTimeout = Duration.ofSeconds(120);

	/** 流式模型调用超时时间。 */
	private Duration streamTimeout = Duration.ofSeconds(300);

	public Duration getCallTimeout() {
		return callTimeout;
	}

	public void setCallTimeout(Duration callTimeout) {
		this.callTimeout = callTimeout;
	}

	public Duration getStreamTimeout() {
		return streamTimeout;
	}

	public void setStreamTimeout(Duration streamTimeout) {
		this.streamTimeout = streamTimeout;
	}
}
