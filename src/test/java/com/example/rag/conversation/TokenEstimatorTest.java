package com.example.rag.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话历史文本 Token 估算器测试。
 */
class TokenEstimatorTest {

	private final TokenEstimator estimator = new TokenEstimator();

	/**
	 * 验证空文本估算为 0。
	 */
	@Test
	void shouldEstimateEmptyTextAsZero() {
		assertThat(this.estimator.estimate(null)).isZero();
		assertThat(this.estimator.estimate("")).isZero();
	}

	/**
	 * 验证 CJK 字符按 1 token 计算。
	 */
	@Test
	void shouldEstimateCjkCharactersAsOneTokenEach() {
		assertThat(this.estimator.estimate("库存不足")).isEqualTo(4);
	}

	/**
	 * 验证连续 ASCII 字母数字按每 4 字符 1 token 向上取整。
	 */
	@Test
	void shouldEstimateAsciiRunsByFourCharacters() {
		assertThat(this.estimator.estimate("abcd")).isEqualTo(1);
		assertThat(this.estimator.estimate("abcde")).isEqualTo(2);
		assertThat(this.estimator.estimate("A1 B2")).isEqualTo(2);
	}

	/**
	 * 验证中英文混合文本估算值。
	 */
	@Test
	void shouldEstimateMixedText() {
		assertThat(this.estimator.estimate("订单 SO20260301 已发货")).isEqualTo(8);
	}

	/**
	 * 验证超长文本会按预算截断并追加省略号。
	 */
	@Test
	void shouldTruncateLongTextToBudget() {
		String truncated = this.estimator.truncate("a".repeat(100), 5);

		assertThat(truncated).endsWith("…");
		assertThat(this.estimator.estimate(truncated)).isLessThanOrEqualTo(5);
	}
}
