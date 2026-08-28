package com.example.rag.conversation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.example.rag.config.ChatMemoryProperties;
import com.example.rag.dao.mapper.ChatMessageMapper;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * 基于 a_chat_message 表的 {@link ChatMemoryRepository} 实现。
 * <p>
 * 复用已有的对话持久化数据，内存零占用，应用重启不丢失对话历史。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>读操作（findByConversationId）</b>：从 DB 加载最近 N 条消息，并剔除末尾的 user
 *       消息——因为 {@code prepareConversation()} 在 Advisor 读取前已将当前提问写入 DB，
 *       而 Advisor 会将当前提问作为 prompt 指令单独发送，不剔除会导致重复</li>
 *   <li><b>写操作（saveAll）</b>：空操作，消息写入统一由 {@link ChatHistoryService} 负责</li>
 *   <li><b>删除操作（deleteByConversationId）</b>：空操作，归档由 {@link ChatHistoryService#archiveConversation} 负责</li>
 * </ul>
 */
@Component
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

	/** 对话消息 Mapper。 */
	private final ChatMessageMapper messageMapper;

	/** 会话记忆 Token 预算配置。 */
	private final ChatMemoryProperties memoryProperties;

	/** 文本 Token 估算器。 */
	private final TokenEstimator tokenEstimator;

	public JdbcChatMemoryRepository(ChatMessageMapper messageMapper,
			ChatMemoryProperties memoryProperties, TokenEstimator tokenEstimator) {
		this.messageMapper = messageMapper;
		this.memoryProperties = memoryProperties;
		this.tokenEstimator = tokenEstimator;
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		List<Boolean> roles = new ArrayList<>();
		List<String> contents = new ArrayList<>();
		for (Map<String, Object> row : messageMapper.selectRecentMessages(
				conversationId, this.memoryProperties.getMaxHistoryMessages())) {
			String role = (String) row.get("role");
			String content = (String) row.get("content");
			roles.add("user".equals(role));
			contents.add(content == null ? "" : content);
		}

		// prepareConversation() 在 Advisor 之前已将当前 user 消息写入 DB，
		// Advisor 会将当前提问作为 prompt 指令单独发送，这里需要剔除以避免重复。
		if (!roles.isEmpty() && roles.get(roles.size() - 1)) {
			roles.remove(roles.size() - 1);
			contents.remove(contents.size() - 1);
		}

		return this.buildTokenBudgetedMessages(roles, contents);
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		// 空操作：消息持久化由 ChatHistoryService 统一负责
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		// 空操作：会话归档由 ChatHistoryService.archiveConversation() 负责
	}

	@Override
	public List<String> findConversationIds() {
		return Collections.emptyList();
	}

	/**
	 * 从最新到最旧按单条和总 Token 预算截断历史消息，并恢复时间正序。
	 *
	 * @param roles    历史消息角色列表
	 * @param contents 历史消息内容列表
	 * @return Token 预算内的消息列表
	 */
	private List<Message> buildTokenBudgetedMessages(List<Boolean> roles, List<String> contents) {
		int maxHistoryTokens = this.memoryProperties.getMaxHistoryTokens();
		int maxMessageTokens = this.memoryProperties.getMaxMessageTokens();
		List<Boolean> keptRoles = new ArrayList<>();
		List<String> keptContents = new ArrayList<>();
		int totalTokens = 0;

		for (int i = roles.size() - 1; i >= 0; i--) {
			String truncated = this.tokenEstimator.truncate(contents.get(i), maxMessageTokens);
			int tokens = this.tokenEstimator.estimate(truncated);
			if (!keptRoles.isEmpty() && totalTokens + tokens > maxHistoryTokens) {
				break;
			}
			totalTokens += tokens;
			keptRoles.add(roles.get(i));
			keptContents.add(truncated);
		}

		Collections.reverse(keptRoles);
		Collections.reverse(keptContents);
		List<Message> messages = new ArrayList<>(keptRoles.size());
		for (int i = 0; i < keptRoles.size(); i++) {
			String content = keptContents.get(i);
			messages.add(keptRoles.get(i) ? new UserMessage(content) : new AssistantMessage(content));
		}
		return messages;
	}
}
