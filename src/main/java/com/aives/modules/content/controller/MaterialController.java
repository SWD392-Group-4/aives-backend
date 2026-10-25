package com.aives.modules.content.controller;

import com.aives.common.dto.ApiResponse;
import com.aives.common.security.UserPrincipal;
import com.aives.modules.content.dto.MaterialResponse;
import com.aives.modules.content.service.MaterialService;
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

@Tag(name = "Materials", description = "Quản lý slide/học liệu và chỉ mục RAG của chương")
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('LECTURER', 'ADMIN')")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @Operation(summary = "Lấy danh sách học liệu và trạng thái RAG của chương")
    @GetMapping("/chapters/{chapterId}/materials")
    public ApiResponse<List<MaterialResponse>> getChapterMaterials(
            @PathVariable String chapterId) {
        return ApiResponse.ok(materialService.getMaterialsByChapterId(chapterId));
    }

    @Operation(summary = "Nạp slide/tài liệu bài giảng lên GCS và kích hoạt RAG Corpus")
    @PostMapping(value = "/chapters/{chapterId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MaterialResponse> uploadChapterMaterial(
            @PathVariable String chapterId,
            @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        MaterialResponse response = materialService.uploadMaterial(chapterId, file, principal);
        return ApiResponse.ok("Tải lên học liệu thành công, đang lập chỉ mục RAG", response);
    }

    @Operation(summary = "Kích hoạt lại tiến trình lập chỉ mục RAG cho tài liệu bị lỗi")
    @PostMapping("/materials/{materialId}/reindex")
    public ApiResponse<MaterialResponse> reindexMaterial(
            @PathVariable String materialId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        MaterialResponse response = materialService.reindexMaterial(materialId, principal);
        return ApiResponse.ok("Đã kích hoạt lại tiến trình lập chỉ mục RAG", response);
    }
}
