package com.aives.testsupport;

import com.aives.integration.ai.LlmClient;
import com.aives.integration.ai.LlmException;
import com.aives.integration.ai.LlmRequest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * "AI" gia cho test: tra lan luot cac ket qua da xep san, KHONG goi Gemini that.
 * Het hang doi: lan goi nhanh (hoi xoay) tra NEXT; lan goi nang (cham diem, doc file) bao loi.
 */
public class ScriptedLlmClient implements LlmClient {

    public static final String NEXT_JSON =
            "{\"decision\":\"NEXT\",\"reason\":\"NONE\",\"followUpQuestion\":\"\",\"explanation\":\"Giải thích của AI\"}";

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<LlmRequest> requests = new ArrayList<>();
    private boolean enabled = true;

    public void reset() {
        script.clear();
        requests.clear();
        enabled = true;
    }

    public void willReturn(String json) {
        script.add(json);
    }

    public void willFail(String message) {
        script.add(new LlmException(message));
    }

    /** false = gia lap backend chua co GEMINI_API_KEY. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int calls() {
        return requests.size();
    }

    public LlmRequest lastRequest() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    public String lastPrompt() {
        return requests.isEmpty() ? null : lastRequest().userPrompt();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String generateJson(LlmRequest request) {
        requests.add(request);
        Object next = script.poll();
        if (next instanceof LlmException exception) {
            throw exception;
        }
        if (next != null) {
            return (String) next;
        }
        if (request.longRunning()) {
            throw new LlmException("Test chưa xếp sẵn kết quả cho lần gọi AI này");
        }
        return NEXT_JSON;
    }
}
