package com.aives.modules.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Length;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Material {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 50)
    private String id;

    @Column(name = "lecturer_id", nullable = false, length = 50)
    private String lecturerId;

    @Column(name = "lesson_id", length = 50)
    private String lessonId;

    @Column(name = "topic_id", length = 50)
    private String topicId;

    @Column(name = "chapters_id", length = 50)
    private String chapterId;

    @Column(name = "uploaded_by", length = 50)
    private String uploadedBy;

    @Nationalized
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "gcs_uri", nullable = false, length = 500)
    private String gcsUri;

    @Builder.Default
    @Column(name = "rag_corpus_id", length = 100)
    private String ragCorpusId = "6917529027641081856";

    @Builder.Default
    @Column(name = "rag_status", nullable = false, length = 30)
    private String ragStatus = "INDEXING";

    @Builder.Default
    @Column(name = "chunk_count")
    private Integer chunkCount = 0;

    @Column(name = "error_message", length = Length.LONG32)
    private String errorMessage;

    /** Chu doc duoc tu file (che do luu local), dua vao prompt khi AI sinh cau hoi. */
    @Nationalized
    @Column(name = "content_text", length = Length.LONG32)
    private String contentText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (ragStatus == null) {
            ragStatus = "INDEXING";
        }
        if (chunkCount == null) {
            chunkCount = 0;
        }
        if (ragCorpusId == null) {
            ragCorpusId = "6917529027641081856";
        }
    }
}
