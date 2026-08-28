package com.example.rag.chat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import com.example.rag.config.AiTimeoutProperties;
import com.example.rag.config.TenantContext;

import jakarta.annotation.PreDestroy;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

/**
 * 模型调用超时执行器。
 * <p>
 * 非流式调用在独立线程中执行并阻塞等待超时；流式调用直接通过 Reactor 的 timeout 控制。
 */
@Component
public class ModelCallTimeout implements AutoCloseable {

	/** 模型调用超时配置。 */
	private final AiTimeoutProperties properties;

	/** 执行非流式模型调用的守护线程池。 */
	private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "model-call-timeout");
		thread.setDaemon(true);
		return thread;
	});

	/**
	 * 创建模型调用超时执行器。
	 *
	 * @param properties 模型调用超时配置
	 */
	public ModelCallTimeout(AiTimeoutProperties properties) {
		this.properties = properties;
	}

	/**
	 * 在超时时间内执行非流式模型调用。
	 *
	 * @param supplier 模型调用
	 * @return 模型响应
	 */
	public ChatResponse call(Supplier<ChatResponse> supplier) {
		String entCode = TenantContext.getEntCode();
		String userId = TenantContext.getUserId();
		Future<ChatResponse> future = this.executor.submit(() -> {
			TenantContext.setEntCode(entCode);
			TenantContext.setUserId(userId);
			try {
				return supplier.get();
			}
			finally {
				TenantContext.clear();
			}
		});

		try {
			return future.get(this.properties.getCallTimeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			throw new IllegalStateException("模型调用超时，请稍后重试");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			future.cancel(true);
			throw new IllegalStateException("模型调用被中断", ex);
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new IllegalStateException("模型调用失败", cause);
		}
	}

	/**
	 * 为流式模型响应增加超时控制。
	 *
	 * @param flux 模型响应流
	 * @return 带超时控制的响应流
	 */
	public Flux<ChatResponse> timeout(Flux<ChatResponse> flux) {
		return flux.timeout(this.properties.getStreamTimeout());
	}

	/** 关闭模型调用线程池。 */
	@Override
	@PreDestroy
	public void close() {
		this.executor.shutdownNow();
	}
}
