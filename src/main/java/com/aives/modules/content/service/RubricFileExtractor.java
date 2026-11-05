package com.aives.modules.content.service;

import com.aives.common.exception.BusinessException;
import com.aives.common.exception.ErrorCode;
import com.aives.integration.ai.LlmClient;
import com.aives.integration.ai.LlmRequest;
import com.aives.modules.content.dto.RubricCriteriaDto;
import com.aives.modules.content.dto.UpdateCourseRubricRequest;
import com.aives.modules.content.util.MaterialTextExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI doc file tieu chi cham diem (PDF / DOCX / PPTX / TXT) va tra ve ban NHAP rubric cho trang Rubric mon hoc.
 * Khong luu vao DB: giang vien xem lai, chinh sua roi bam "Luu" (PUT /courses/{id}/rubric) nhu binh thuong.
 * Trong so duoc chuan hoa ve tong dung 100%; file khong ghi trong so thi chia deu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RubricFileExtractor {

    private static final Set<String> ALLOWED = Set.of("pdf", "docx", "pptx", "txt");
    private static final long MAX_BYTES = 10 * 1024 * 1024L;
    private static final int MAX_TEXT_CHARS = 60_000;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final String SYSTEM = """
            Bạn là chuyên gia khảo thí. Nhiệm vụ: đọc tài liệu giảng viên gửi lên và trích xuất RUBRIC chấm thi vấn đáp.
            Quy tắc:
            1. Chỉ lấy các tiêu chí chấm điểm có trong tài liệu, không tự bịa thêm tiêu chí.
            2. Mỗi tiêu chí gồm: name (tên ngắn gọn), description (mô tả yêu cầu để đạt điểm, viết bằng tiếng Việt),
               weightPercent (trọng số %, ghi đúng như tài liệu; nếu tài liệu ghi theo điểm thì quy đổi ra %;
               nếu tài liệu không ghi trọng số thì để 0).
            3. name của rubric: lấy tiêu đề rubric trong tài liệu, nếu không có thì đặt tên ngắn gọn theo nội dung.
            4. Nếu tài liệu có câu hỏi kèm rubric thì chỉ lấy phần rubric, bỏ qua câu hỏi.
            5. Không tìm thấy tiêu chí nào thì trả về criteria là mảng rỗng.""";

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "name", Map.of("type", "STRING"),
                    "description", Map.of("type", "STRING"),
                    "criteria", Map.of(
                            "type", "ARRAY",
                            "items", Map.of(
                                    "type", "OBJECT",
                                    "properties", Map.of(
                                            "name", Map.of("type", "STRING"),
                                            "description", Map.of("type", "STRING"),
                                            "weightPercent", Map.of("type", "NUMBER")),
                                    "required", List.of("name", "description", "weightPercent")))),
            "required", List.of("name", "criteria"));

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public UpdateCourseRubricRequest extract(MultipartFile file) {
        if (!llmClient.isEnabled()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn file rubric");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File quá lớn, tối đa 10MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ hỗ trợ file .pdf, .docx, .pptx, .txt");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không đọc được file tải lên");
        }

        LlmRequest request = buildRequest(name, ext, bytes);
        JsonNode root;
        try {
            root = objectMapper.readTree(llmClient.generateJson(request));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AI doc file rubric loi: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI không đọc được file rubric: " + ex.getMessage());
        }

        List<RubricCriteriaDto> criteria = toCriteria(root.path("criteria"));
        if (criteria.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Không tìm thấy tiêu chí chấm điểm nào trong file \"" + name + "\"");
        }
        return UpdateCourseRubricRequest.builder()
                .name(textOrNull(root.path("name")))
                .description(textOrNull(root.path("description")))
                .criteria(criteria)
                .build();
    }

    private LlmRequest buildRequest(String fileName, String ext, byte[] bytes) {
        String prompt = "Trích xuất rubric từ file \"" + fileName + "\".";
        if ("pdf".equals(ext)) {
            // PDF gui thang cho AI doc (doc duoc ca bang bieu)
            return LlmRequest.longRunning(SYSTEM, prompt, SCHEMA)
                    .withAttachment("application/pdf", bytes)
                    .forDocumentExtraction();
        }
        String text;
        try {
            text = "txt".equals(ext) ? new String(bytes, StandardCharsets.UTF_8) : MaterialTextExtractor.extract(bytes, ext);
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không đọc được nội dung file: " + ex.getMessage());
        }
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File không có nội dung chữ để đọc");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
        }
        return LlmRequest.longRunning(SYSTEM, prompt + "\n\nNỘI DUNG FILE:\n" + text, SCHEMA).forDocumentExtraction();
    }

    /** Doc danh sach tieu chi va chuan hoa trong so ve tong dung 100% (2 chu so thap phan). */
    private List<RubricCriteriaDto> toCriteria(JsonNode arr) {
        List<String> names = new ArrayList<>();
        List<String> descs = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode item : arr) {
                String n = item.path("name").asText("").trim();
                if (n.isEmpty()) {
                    continue;
                }
                String d = item.path("description").asText("").trim();
                names.add(n);
                descs.add(d.isEmpty() ? n : d);
                BigDecimal w = item.path("weightPercent").isNumber() ? item.path("weightPercent").decimalValue() : BigDecimal.ZERO;
                weights.add(w.signum() > 0 ? w : BigDecimal.ZERO);
            }
        }
        int count = names.size();
        if (count == 0) {
            return List.of();
        }

        BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean anyMissing = weights.stream().anyMatch(w -> w.signum() == 0);
        List<BigDecimal> normalized = new ArrayList<>();
        BigDecimal used = BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            BigDecimal w;
            if (i == count - 1) {
                w = HUNDRED.subtract(used);
            } else if (anyMissing || sum.signum() == 0) {
                // Thieu trong so -> chia deu
                w = HUNDRED.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
            } else {
                w = weights.get(i).multiply(HUNDRED).divide(sum, 2, RoundingMode.HALF_UP);
            }
            used = used.add(w);
            normalized.add(w);
        }
        // Tieu chi cuoi co the <= 0 khi lam tron qua tay: chia deu lai
        if (normalized.get(count - 1).signum() <= 0) {
            normalized.clear();
            used = BigDecimal.ZERO;
            for (int i = 0; i < count; i++) {
                BigDecimal w = i == count - 1 ? HUNDRED.subtract(used)
                        : HUNDRED.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
                used = used.add(w);
                normalized.add(w);
            }
        }

        List<RubricCriteriaDto> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(RubricCriteriaDto.builder()
                    .name(names.get(i))
                    .description(descs.get(i))
                    .weightPercent(normalized.get(i))
                    .orderNo(i + 1)
                    .build());
        }
        return result;
    }

    private static String textOrNull(JsonNode node) {
        String s = node.asText("").trim();
        return s.isEmpty() ? null : s;
    }
}
