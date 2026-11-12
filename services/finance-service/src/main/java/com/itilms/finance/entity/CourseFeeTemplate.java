package com.itilms.finance.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.itilms.common.entity.AuditableEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The usual fee structure of one course: installments, the first due date, and the extra heads every student pays. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "course_fee_templates")
public class CourseFeeTemplate extends AuditableEntity {

    @Id
    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "installment_count", nullable = false)
    private int installmentCount = 1;

    @Column(name = "first_due_days", nullable = false)
    private int firstDueDays = 7;

    @ElementCollection
    @CollectionTable(name = "course_fee_template_charges", joinColumns = @JoinColumn(name = "course_id"))
    @OrderColumn(name = "sort_order")
    private List<Charge> charges = new ArrayList<>();

    public CourseFeeTemplate(Long courseId) {
        this.courseId = courseId;
    }

    /** One extra head: its name, the amount, and how many days after placement it falls due. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class Charge {

        @Column(nullable = false, length = 120)
        private String label;

        @Column(nullable = false, precision = 12, scale = 2)
        private BigDecimal amount;

        @Column(name = "due_days", nullable = false)
        private int dueDays;
    }
}
