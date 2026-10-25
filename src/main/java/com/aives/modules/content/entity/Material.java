package com.aives.modules.content.entity;

import com.aives.common.entity.BaseEntity;
import com.aives.modules.auth.entity.User;
import com.aives.modules.content.enums.MaterialRagStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Length;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "materials")
@AttributeOverride(
        name = "id",
        column = @Column(name = "materials_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Material extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapters_id", referencedColumnName = "chapters_id")
    private Chapter chapter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", referencedColumnName = "users_id")
    private User uploadedBy;

    @Column(name = "chapters_id", length = 50)
    private String chapterId;

    @Column(name = "uploaded_by", length = 50)
    private String uploadedBy;

    @Nationalized
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "rag_status", nullable = false, length = 20)
    private MaterialRagStatus ragStatus = MaterialRagStatus.INDEXING;

    @Builder.Default
    @Column(name = "rag_corpus_id", length = 100)
    private String ragCorpusId = "6917529027641081856";

    @Builder.Default
    @Column(name = "chunk_count")
    private Integer chunkCount = 0;

    @Column(name = "error_message", length = Length.LONG32)
    private String errorMessage;
}
