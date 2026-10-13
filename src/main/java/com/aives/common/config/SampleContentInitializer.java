package com.aives.common.config;

import com.aives.common.util.RandomIdGenerator;
import com.aives.modules.auth.entity.User;
import com.aives.modules.auth.enums.RoleName;
import com.aives.modules.auth.repository.UserRepository;
import com.aives.modules.content.entity.Lesson;
import com.aives.modules.content.entity.Question;
import com.aives.modules.content.entity.Rubric;
import com.aives.modules.content.entity.RubricCriterion;
import com.aives.modules.content.entity.Topic;
import com.aives.modules.content.repository.LessonRepository;
import com.aives.modules.content.repository.QuestionRepository;
import com.aives.modules.content.repository.RubricCriterionRepository;
import com.aives.modules.content.repository.RubricRepository;
import com.aives.modules.content.repository.TopicRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Chay sau DataInitializer. Quan ly bo cau hoi MAU cua he thong, theo app.seed.sample-content (SEED_SAMPLE_CONTENT):
 *
 * true (chi dung cho test tu dong / chay thu tren H2): tao bo cau hoi mau Toan lop 6 - 7
 *   (1 bai hoc, 1 chu de, 1 rubric 3 tieu chi, 5 cau hoi da duyet) neu chua co; bo mau cu ve kien truc
 *   phan mem (neu co) duoc chuyen sang ARCHIVED.
 *
 * false (mac dinh): KHONG co du lieu mau. Neu database dang co 2 bai hoc mau (nhan biet bang tieu de bai hoc)
 *   thi XOA HAN: chu de, cau hoi cua chung, va moi luot thi / diem / don phuc khao cua cac phien thi
 *   da dung cac cau hoi do. Rubric mau duoc giu lai vi cau hoi khac co the dang dung.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class SampleContentInitializer implements CommandLineRunner {

    public static final String SAMPLE_LESSON_TITLE = "Toán lớp 6 - 7 (dữ liệu mẫu)";
    private static final String OLD_SAMPLE_LESSON_TITLE = "Kiến trúc phần mềm (dữ liệu mẫu)";

    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final TopicRepository topicRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository criterionRepository;
    private final QuestionRepository questionRepository;
    private final RandomIdGenerator idGenerator;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.seed.sample-content:false}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled) {
            removeSampleContent();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> archiveOldSample());

        if (lessonRepository.existsByTitle(SAMPLE_LESSON_TITLE)) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            String sampleQuestionId = seed();
            if (sampleQuestionId != null && !vietnameseIsStoredCorrectly(sampleQuestionId)) {
                // Khong giu lai du lieu mau bi hong chu
                status.setRollbackOnly();
                log.error("KHONG tao cau hoi mau vi database KHONG luu duoc tieng Viet co dau. "
                        + "PostgreSQL: tao lai database voi ENCODING 'UTF8'. "
                        + "SQL Server: chay script database/08_text_columns_to_nvarchar.sql. Sau do khoi dong lai backend. "
                        + "Neu khong, cau hoi va cau tra loi cua sinh vien se bi luu thanh dau '?'.");
            }
        });
    }

    /**
     * Xoa han 2 bai hoc mau va moi du lieu phu thuoc. Khong co bai hoc mau thi khong lam gi.
     * Thu tu xoa di tu bang con len bang cha de khong vi pham khoa ngoai.
     */
    public void removeSampleContent() {
        transactionTemplate.executeWithoutResult(status -> {
            List<String> lessonIds = entityManager
                    .createQuery("select l.id from Lesson l where l.title in :titles", String.class)
                    .setParameter("titles", List.of(SAMPLE_LESSON_TITLE, OLD_SAMPLE_LESSON_TITLE))
                    .getResultList();
            if (lessonIds.isEmpty()) {
                return;
            }
            List<String> topicIds = ids("select t.id from Topic t where t.lesson.id in :ids", lessonIds);
            List<String> questionIds = ids("select q.id from Question q where q.topic.id in :ids", topicIds);
            // Phien thi da duoc gan cau hoi mau, va cac luot thi cua nhung phien do
            List<String> examIds = ids("select distinct eq.exam.id from VivaExamQuestion eq where eq.question.id in :ids", questionIds);
            List<String> attemptIds = ids("select a.id from VivaAttempt a where a.exam.id in :ids", examIds);

            String gradesOfAttempts = "(select qg.id from QuestionGrade qg where qg.attempt.id in :ids)";
            delete("delete from GradeAppeal ga where ga.questionGrade.id in " + gradesOfAttempts, attemptIds);
            delete("delete from CriteriaGrade cg where cg.questionGrade.id in " + gradesOfAttempts, attemptIds);
            delete("delete from QuestionGrade qg where qg.attempt.id in :ids", attemptIds);
            // Cau hoi xoay tro toi cau truoc do (parent_exchange_id): go lien ket truoc khi xoa
            delete("update InterviewExchange ie set ie.parentExchange = null where ie.attempt.id in :ids", attemptIds);
            delete("delete from InterviewExchange ie where ie.attempt.id in :ids", attemptIds);
            delete("delete from VivaAttempt a where a.id in :ids", attemptIds);
            // Bo ca bo cau hoi cua phien: phien con dung duoc se tu boc lai cau hoi khi co sinh vien vao thi
            delete("delete from VivaExamQuestion eq where eq.exam.id in :ids", examIds);
            if (!topicIds.isEmpty()) {
                entityManager.createNativeQuery("delete from viva_exam_topics where topics_id in (:ids)")
                        .setParameter("ids", topicIds)
                        .executeUpdate();
            }
            delete("delete from Question q where q.id in :ids", questionIds);
            delete("delete from Topic t where t.id in :ids", topicIds);
            delete("delete from Lesson l where l.id in :ids", lessonIds);
            entityManager.clear();
            log.warn("Da XOA du lieu mau: {} bai hoc, {} chu de, {} cau hoi, {} luot thi cua {} phien thi da dung cau hoi mau",
                    lessonIds.size(), topicIds.size(), questionIds.size(), attemptIds.size(), examIds.size());
        });
    }

    private List<String> ids(String jpql, List<String> parentIds) {
        if (parentIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(jpql, String.class).setParameter("ids", parentIds).getResultList();
    }

    private void delete(String jpql, List<String> ids) {
        if (!ids.isEmpty()) {
            entityManager.createQuery(jpql).setParameter("ids", ids).executeUpdate();
        }
    }

    private void archiveOldSample() {
        List<Question> oldQuestions = questionRepository.findByTopic_Lesson_TitleAndStatus(OLD_SAMPLE_LESSON_TITLE, "APPROVED");
        for (Question question : oldQuestions) {
            question.setStatus("ARCHIVED");
        }
        if (!oldQuestions.isEmpty()) {
            questionRepository.saveAll(oldQuestions);
            log.info("Da chuyen {} cau hoi mau cu (kien truc phan mem) sang ARCHIVED", oldQuestions.size());
        }
    }

    /** Doc lai 1 cau hoi vua luu tu database xem chu tieng Viet co con nguyen khong. */
    private boolean vietnameseIsStoredCorrectly(String questionId) {
        entityManager.flush();
        String expected = questionRepository.findById(questionId).map(Question::getContent).orElse("");
        // Xoa cache de lan doc sau lay that tu database
        entityManager.clear();
        String stored = questionRepository.findById(questionId).map(Question::getContent).orElse(null);
        return expected.equals(stored);
    }

    /** Tao du lieu mau. Tra ve id cua 1 cau hoi vua tao, hoac null neu khong tao duoc. */
    private String seed() {
        // Bai hoc va rubric can 1 nguoi so huu: lay giang vien dau tien, khong co thi lay admin
        Optional<User> owner = userRepository.findFirstByRole_NameOrderByCreatedAtAsc(RoleName.LECTURER)
                .or(() -> userRepository.findFirstByRole_NameOrderByCreatedAtAsc(RoleName.ADMIN));
        if (owner.isEmpty()) {
            log.warn("Chua co tai khoan LECTURER / ADMIN nao nen khong tao duoc cau hoi mau");
            return null;
        }
        String ownerId = owner.get().getId();

        Lesson lesson = Lesson.builder()
                .lecturerId(ownerId)
                .title(SAMPLE_LESSON_TITLE)
                .description("Bài học mẫu do hệ thống tạo để thi thử phỏng vấn AI bằng các câu hỏi dễ hiểu.")
                .build();
        lesson.setId(idGenerator.next("LS"));
        lessonRepository.save(lesson);

        Topic topic = Topic.builder()
                .lesson(lesson)
                .name("Số học cơ bản")
                .description("Số nguyên tố, dấu hiệu chia hết, ước chung lớn nhất, số hữu tỉ.")
                .build();
        topic.setId(idGenerator.next("TP"));
        topicRepository.save(topic);

        Rubric rubric = Rubric.builder()
                .createdBy(ownerId)
                .name("Rubric vấn đáp Toán cơ bản (mẫu)")
                .description("Chấm câu trả lời vấn đáp trên thang 10.")
                .maxScore(new BigDecimal("10.00"))
                .build();
        rubric.setId(idGenerator.next("RB"));
        rubricRepository.save(rubric);

        saveCriterion(rubric, 1, "Đúng kiến thức", "60",
                "Nêu đúng và đủ định nghĩa / quy tắc so với đáp án tham khảo, không có lỗi sai kiến thức.");
        saveCriterion(rubric, 2, "Ví dụ và vận dụng", "25",
                "Cho được ví dụ đúng hoặc áp dụng đúng quy tắc vào một số cụ thể.");
        saveCriterion(rubric, 3, "Diễn đạt", "15",
                "Trình bày rõ ràng, đi thẳng vào câu hỏi, dùng đúng thuật ngữ toán học.");

        // Id tang dan theo thu tu soan, de danh sach (sap theo ngay tao roi id) giu dung thu tu duoi day
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            ids.add(idGenerator.next("QS"));
        }
        Collections.sort(ids);

        saveQuestion(ids.get(0), topic, rubric, "REMEMBER",
                "Thế nào là số nguyên tố? Hãy cho ví dụ.",
                "Số nguyên tố là số tự nhiên lớn hơn 1, chỉ có đúng hai ước là 1 và chính nó. Ví dụ: 2, 3, 5, 7, 11. "
                        + "Số 2 là số nguyên tố chẵn duy nhất. Số 0 và số 1 không phải số nguyên tố, cũng không phải hợp số.",
                "[\"số tự nhiên lớn hơn 1\", \"hai ước\", \"1 và chính nó\", \"2, 3, 5, 7\", \"hợp số\"]");
        saveQuestion(ids.get(1), topic, rubric, "REMEMBER",
                "Nêu dấu hiệu chia hết cho 2 và dấu hiệu chia hết cho 5.",
                "Số chia hết cho 2 là số có chữ số tận cùng là chữ số chẵn: 0, 2, 4, 6, 8. "
                        + "Số chia hết cho 5 là số có chữ số tận cùng là 0 hoặc 5. "
                        + "Số có chữ số tận cùng là 0 thì chia hết cho cả 2 và 5.",
                "[\"chữ số tận cùng\", \"chữ số chẵn\", \"0, 2, 4, 6, 8\", \"0 hoặc 5\"]");
        saveQuestion(ids.get(2), topic, rubric, "APPLY",
                "Nêu dấu hiệu chia hết cho 3 và cho 9. Số 126 có chia hết cho 3 và cho 9 không? Vì sao?",
                "Số chia hết cho 3 khi tổng các chữ số của nó chia hết cho 3. Số chia hết cho 9 khi tổng các chữ số của nó "
                        + "chia hết cho 9. Với 126: tổng các chữ số là 1 + 2 + 6 = 9, chia hết cho cả 3 và 9, "
                        + "nên 126 chia hết cho 3 và chia hết cho 9.",
                "[\"tổng các chữ số\", \"chia hết cho 3\", \"chia hết cho 9\", \"1 + 2 + 6 = 9\"]");
        saveQuestion(ids.get(3), topic, rubric, "APPLY",
                "Ước chung lớn nhất của hai số là gì? Hãy tìm ước chung lớn nhất của 12 và 18.",
                "Ước chung lớn nhất (ƯCLN) của hai số là số lớn nhất trong các ước chung của hai số đó. "
                        + "Ước của 12 là 1, 2, 3, 4, 6, 12. Ước của 18 là 1, 2, 3, 6, 9, 18. Ước chung là 1, 2, 3, 6 nên ƯCLN là 6. "
                        + "Cách khác: 12 = 2² × 3, 18 = 2 × 3², lấy thừa số chung với số mũ nhỏ nhất: 2 × 3 = 6.",
                "[\"ước chung\", \"số lớn nhất\", \"phân tích ra thừa số nguyên tố\", \"số mũ nhỏ nhất\", \"6\"]");
        saveQuestion(ids.get(4), topic, rubric, "UNDERSTAND",
                "Số hữu tỉ là gì? Số nguyên có phải là số hữu tỉ không? Hãy cho ví dụ.",
                "Số hữu tỉ là số viết được dưới dạng phân số a/b với a, b là số nguyên và b khác 0. "
                        + "Ví dụ: 1/2, -3/4, 0,25. Mọi số nguyên đều là số hữu tỉ vì viết được dưới dạng phân số có mẫu là 1, "
                        + "ví dụ 5 = 5/1.",
                "[\"phân số a/b\", \"a, b là số nguyên\", \"b khác 0\", \"mẫu là 1\", \"số nguyên là số hữu tỉ\"]");

        log.info("Seeded sample content: 1 lesson, 1 topic, 1 rubric, 5 questions (Toan lop 6 - 7)");
        return ids.get(0);
    }

    private void saveCriterion(Rubric rubric, int orderNo, String name, String weightPercent, String description) {
        RubricCriterion criterion = RubricCriterion.builder()
                .rubric(rubric)
                .name(name)
                .description(description)
                .weightPercent(new BigDecimal(weightPercent))
                .orderNo(orderNo)
                .build();
        criterion.setId(idGenerator.next("RC"));
        criterionRepository.save(criterion);
    }

    private void saveQuestion(String id, Topic topic, Rubric rubric, String bloomLevel, String content,
                              String modelAnswer, String expectedKeywords) {
        Question question = Question.builder()
                .topic(topic)
                .rubric(rubric)
                .content(content)
                .bloomLevel(bloomLevel)
                .modelAnswer(modelAnswer)
                .expectedKeywords(expectedKeywords)
                .source("MANUAL")
                .status("APPROVED")
                .build();
        question.setId(id);
        questionRepository.save(question);
    }
}
