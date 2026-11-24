package com.java.erp.modules.enrollment.repository;

import com.java.erp.modules.enrollment.entity.CourseEnrollment;
import com.java.erp.modules.system.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long>,
        JpaSpecificationExecutor<CourseEnrollment> {

    @Override
    @EntityGraph(attributePaths = {"student", "student.section", "subjectOffering", "subjectOffering.subject",
            "subjectOffering.section", "subjectOffering.staff"})
    Page<CourseEnrollment> findAll(Specification<CourseEnrollment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "student.section", "subjectOffering", "subjectOffering.subject",
            "subjectOffering.section", "subjectOffering.staff"})
    Optional<CourseEnrollment> findWithDetailsById(Long id);

    Optional<CourseEnrollment> findByStudentIdAndSubjectOfferingId(Long studentId, Long subjectOfferingId);

    List<CourseEnrollment> findBySubjectOfferingId(Long subjectOfferingId);

    @EntityGraph(attributePaths = {"student", "student.section", "subjectOffering", "subjectOffering.subject",
            "subjectOffering.section", "subjectOffering.staff"})
    List<CourseEnrollment> findBySubjectOfferingIdOrderByStudentRegisterNumberAsc(Long subjectOfferingId);

    @EntityGraph(attributePaths = {"student", "student.section", "subjectOffering", "subjectOffering.subject",
            "subjectOffering.section", "subjectOffering.staff"})
    List<CourseEnrollment> findByStudentIdOrderBySubjectOfferingAcademicYearDescSubjectOfferingSemesterDescIdAsc(Long studentId);

    /** The class list: active students currently ENROLLED in the offering, in register-number order. */
    @Query("SELECT e.student FROM CourseEnrollment e WHERE e.subjectOffering.id = :offeringId " +
           "AND e.status = com.java.erp.modules.enrollment.entity.CourseEnrollment.Status.ENROLLED " +
           "AND e.student.active = true ORDER BY e.student.registerNumber")
    List<Student> findEnrolledStudents(@Param("offeringId") Long offeringId);

    @Query("SELECT e.student FROM CourseEnrollment e WHERE e.subjectOffering.id = :offeringId " +
           "AND e.status IN :statuses AND e.student.active = true ORDER BY e.student.registerNumber")
    List<Student> findStudentsByStatusIn(@Param("offeringId") Long offeringId,
                                         @Param("statuses") Collection<CourseEnrollment.Status> statuses);

    boolean existsByStudentIdAndSubjectOfferingIdAndStatusIn(Long studentId, Long subjectOfferingId,
                                                            Collection<CourseEnrollment.Status> statuses);

    @Query("SELECT e.subjectOffering.id FROM CourseEnrollment e WHERE e.student.id = :studentId AND e.status IN :statuses")
    List<Long> findOfferingIdsByStudentIdAndStatusIn(@Param("studentId") Long studentId,
                                                     @Param("statuses") Collection<CourseEnrollment.Status> statuses);

    /** (offeringId, studentId) of every active student currently ENROLLED in these offerings (class summaries). */
    @Query("SELECT e.subjectOffering.id AS offeringId, e.student.id AS studentId FROM CourseEnrollment e " +
           "WHERE e.subjectOffering.id IN :offeringIds " +
           "AND e.status = com.java.erp.modules.enrollment.entity.CourseEnrollment.Status.ENROLLED AND e.student.active = true")
    List<OfferingStudent> findEnrolledPairs(@Param("offeringIds") Collection<Long> offeringIds);

    interface OfferingStudent {
        Long getOfferingId();
        Long getStudentId();
    }

    /**
     * (student, subject) of every enrollment with one of these statuses on an ACTIVE offering of the cohort + semester
     * (registration status: one query for the whole cohort).
     */
    @Query("SELECT DISTINCT e.student.id AS studentId, o.subject.id AS subjectId FROM CourseEnrollment e " +
           "JOIN e.subjectOffering o WHERE o.batch.id = :batchId AND o.semester = :semester AND o.active = true " +
           "AND e.status IN :statuses")
    List<StudentSubject> findRegistrations(@Param("batchId") Long batchId, @Param("semester") Integer semester,
                                           @Param("statuses") Collection<CourseEnrollment.Status> statuses);

    interface StudentSubject {
        Long getStudentId();
        Long getSubjectId();
    }

    /** Every enrollment row (any status, any offering state) of one student on offerings of the cohort + semester. */
    @EntityGraph(attributePaths = {"subjectOffering", "subjectOffering.subject", "subjectOffering.staff",
            "subjectOffering.section"})
    @Query("SELECT e FROM CourseEnrollment e WHERE e.student.id = :studentId AND e.subjectOffering.batch.id = :batchId " +
           "AND e.subjectOffering.semester = :semester ORDER BY e.id")
    List<CourseEnrollment> findByStudentAndTerm(@Param("studentId") Long studentId, @Param("batchId") Long batchId,
                                                @Param("semester") Integer semester);

    /** Students with an enrollment row (any status) in the offering. */
    @Query("SELECT e.student.id FROM CourseEnrollment e WHERE e.subjectOffering.id = :offeringId")
    List<Long> findStudentIdsBySubjectOfferingId(@Param("offeringId") Long offeringId);
}
