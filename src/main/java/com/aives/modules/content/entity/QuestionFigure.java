package com.aives.modules.content.entity;

import com.aives.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Length;

/**
 * Hinh ve cua 1 cau hoi (vd hinh "Tinh goc x"), cat tu trang PDF giang vien gui len.
 * Tach bang rieng de khi doc danh sach cau hoi khong phai tai ca anh. Khoa chinh = questions_id (1 cau toi da 1 hinh).
 */
@Entity
@Table(name = "question_figures")
@AttributeOverride(
        name = "id",
        column = @Column(name = "questions_id", nullable = false, updatable = false, length = 20))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionFigure extends BaseEntity {

    // Hien tai luon la image/png
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    // PostgreSQL: bytea, SQL Server: varbinary(max)
    @Column(name = "image_data", nullable = false, length = Length.LONG32)
    private byte[] imageData;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "height", nullable = false)
    private Integer height;

    // Trang chua hinh trong file PDF (bat dau tu 1)
    @Column(name = "source_page")
    private Integer sourcePage;
}
