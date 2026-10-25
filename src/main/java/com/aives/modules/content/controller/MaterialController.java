package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.MaterialResponse;
import com.aives.modules.content.service.IMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Materials", description = "Quản lý slide bài giảng và học liệu RAG")
@RestController
@RequiredArgsConstructor
public class MaterialController {

    private final IMaterialService materialService;

    @Operation(summary = "Lấy danh sách học liệu và trạng thái RAG của chương")
    @GetMapping("/api/v1/chapters/{chapterId}/materials")
    public ApiResponse<List<MaterialResponse>> getChapterMaterials(
            @Parameter(description = "Mã chương") @PathVariable String chapterId) {
        List<MaterialResponse> response = materialService.getMaterialsByChapterId(chapterId);
        return ApiResponse.ok("Lấy danh sách học liệu thành công", response);
    }

    @Operation(summary = "Nạp slide/tài liệu bài giảng lên GCS và kích hoạt RAG",
            description = "Tải lên tài liệu (.pdf, .pptx, <= 50MB). Lưu GCS và gửi job embedding bất đồng bộ sang Vertex AI RAG Engine (INDEXING).")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @PostMapping(value = "/api/v1/chapters/{chapterId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MaterialResponse> uploadChapterMaterial(
            @Parameter(description = "Mã chương") @PathVariable String chapterId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        MaterialResponse response = materialService.uploadMaterial(chapterId, file, principal.getId());
        return ApiResponse.ok("Nạp tài liệu thành công", response);
    }

    @Operation(summary = "Kích hoạt lại tiến trình lập chỉ mục RAG cho tài liệu bị lỗi")
    @PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
    @PostMapping("/api/v1/materials/{materialId}/reindex")
    public ApiResponse<MaterialResponse> reindexMaterial(
            @Parameter(description = "Mã học liệu") @PathVariable String materialId,
            @AuthenticationPrincipal UserPrincipal principal) {
        MaterialResponse response = materialService.triggerReindex(materialId, principal.getId());
        return ApiResponse.ok("Đã kích hoạt lại tiến trình", response);
    }
}