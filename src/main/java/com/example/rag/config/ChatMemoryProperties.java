package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会话记忆 Token 预算配置。
 * <p>
 * 对应 application.yml 中的 app.chat.memory 配置：
 * <ul>
 *   <li>maxHistoryTokens：单次会话记忆允许进入 Prompt 的总 Token 预算</li>
 *   <li>maxMessageTokens：单条历史消息允许保留的最大 Token 数</li>
 *   <li>maxHistoryMessages：最多保留的历史消息条数</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "app.chat.memory")
public class ChatMemoryProperties {

	/** 单次会话记忆允许进入 Prompt 的总 Token 预算。 */
	private int maxHistoryTokens = 6000;

	/** 单条历史消息允许保留的最大 Token 数。 */
	private int maxMessageTokens = 1500;

	/** 最多保留的历史消息条数。 */
	private int maxHistoryMessages = 20;

	public int getMaxHistoryTokens() {
		return maxHistoryTokens;
	}

	public void setMaxHistoryTokens(int maxHistoryTokens) {
		this.maxHistoryTokens = maxHistoryTokens;
	}

	public int getMaxMessageTokens() {
		return maxMessageTokens;
	}

	public void setMaxMessageTokens(int maxMessageTokens) {
		this.maxMessageTokens = maxMessageTokens;
	}

	public int getMaxHistoryMessages() {
		return maxHistoryMessages;
	}

	public void setMaxHistoryMessages(int maxHistoryMessages) {
		this.maxHistoryMessages = maxHistoryMessages;
	}
}
