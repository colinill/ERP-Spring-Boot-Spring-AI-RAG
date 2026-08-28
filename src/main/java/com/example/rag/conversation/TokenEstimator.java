package com.example.rag.conversation;

import org.springframework.stereotype.Component;

/**
 * 会话历史文本 Token 估算器。
 * <p>
 * 用于在不引入模型分词器的情况下，对历史消息做有界截断：
 * <ul>
 *   <li>CJK 表意字符按 1 token 估算</li>
 *   <li>连续 ASCII 字母或数字按每 4 个字符 1 token 向上取整</li>
 *   <li>空白与标点不计入 Token</li>
 * </ul>
 */
@Component
public class TokenEstimator {

	/** ASCII 字符与 Token 的近似比例。 */
	private static final int ASCII_TOKENS_PER_CHUNK = 4;

	/**
	 * 估算文本 Token 数量。
	 *
	 * @param text 待估算文本
	 * @return 估算 Token 数
	 */
	public int estimate(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}

		int tokens = 0;
		int asciiRun = 0;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (Character.isIdeographic(ch)) {
				if (asciiRun > 0) {
					tokens += (asciiRun + ASCII_TOKENS_PER_CHUNK - 1) / ASCII_TOKENS_PER_CHUNK;
					asciiRun = 0;
				}
				tokens++;
			}
			else if (ch < 128 && Character.isLetterOrDigit(ch)) {
				asciiRun++;
			}
			else {
				if (asciiRun > 0) {
					tokens += (asciiRun + ASCII_TOKENS_PER_CHUNK - 1) / ASCII_TOKENS_PER_CHUNK;
					asciiRun = 0;
				}
			}
		}
		if (asciiRun > 0) {
			tokens += (asciiRun + ASCII_TOKENS_PER_CHUNK - 1) / ASCII_TOKENS_PER_CHUNK;
		}
		return tokens;
	}

	/**
	 * 将文本截断到指定 Token 预算内，截断时补充省略号。
	 *
	 * @param text      原始文本
	 * @param maxTokens Token 预算
	 * @return 截断后的文本
	 */
	public String truncate(String text, int maxTokens) {
		if (text == null || text.isBlank() || estimate(text) <= maxTokens) {
			return text;
		}

		int low = 0;
		int high = text.length();
		while (low < high) {
			int mid = (low + high + 1) >>> 1;
			if (estimate(text.substring(0, mid)) <= maxTokens) {
				low = mid;
			}
			else {
				high = mid - 1;
			}
		}

		int end = low;
		if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))
				&& end < text.length() && Character.isLowSurrogate(text.charAt(end))) {
			end--;
		}
		return text.substring(0, end) + "…";
	}
}
