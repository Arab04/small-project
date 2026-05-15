package uz.footballai.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaudeResponse {

    private String content;
    private int inputTokens;
    private int outputTokens;
    private String model;

    public int getTotalTokens() {
        return inputTokens + outputTokens;
    }
}
