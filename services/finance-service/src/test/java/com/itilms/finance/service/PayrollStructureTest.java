package com.itilms.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.itilms.common.event.EventPublisher;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.finance.client.StaffDirectoryClient;
import com.itilms.finance.config.FinanceProperties;
import com.itilms.finance.entity.StaffPay;
import com.itilms.finance.entity.StaffPayHistory;
import com.itilms.finance.repository.PayslipRepository;
import com.itilms.finance.repository.StaffPayHistoryRepository;
import com.itilms.finance.repository.StaffPayRepository;

class PayrollStructureTest {

    private final StaffPayRepository structures = mock(StaffPayRepository.class);
    private final StaffPayHistoryRepository history = mock(StaffPayHistoryRepository.class);
    private final StaffDirectoryClient directory = mock(StaffDirectoryClient.class);
    private final PayrollService service = new PayrollService(structures, mock(com.itilms.finance.repository.StaffPayHeadRepository.class),
            mock(com.itilms.finance.repository.PayslipItemRepository.class), history, mock(PayslipRepository.class), directory,
            mock(ExpenseService.class), new FinanceProperties(), mock(EventPublisher.class), mock(BooksLockService.class));

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    void everyChangeOfPayIsKeptInTheHistory() {
        StaffPay pay = StaffPay.builder().userId(5L).staffName("Ravi").roleName("Trainer").build();
        when(structures.findById(5L)).thenReturn(Optional.of(pay));
        when(structures.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveStructure(5L, new PayrollService.StructureRequest(n("30000"), n("5000"), n("1000"), true));

        var saved = org.mockito.ArgumentCaptor.forClass(StaffPayHistory.class);
        verify(history).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(5L);
        assertThat(saved.getValue().getBasic()).isEqualByComparingTo("30000");
    }

    @Test
    void aDeductionLargerThanThePayIsRefused() {
        StaffPay pay = StaffPay.builder().userId(5L).staffName("Ravi").roleName("Trainer").build();
        when(structures.findById(5L)).thenReturn(Optional.of(pay));

        assertThatThrownBy(() -> service.saveStructure(5L, new PayrollService.StructureRequest(n("1000"), n("0"), n("2000"), true)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aNewPersonMustBeInTheStaffDirectory() {
        when(structures.findById(9L)).thenReturn(Optional.empty());
        when(directory.staff()).thenReturn(List.of());

        assertThatThrownBy(() -> service.saveStructure(9L, new PayrollService.StructureRequest(n("1000"), n("0"), n("0"), true)))
                .isInstanceOf(com.itilms.common.exception.ResourceNotFoundException.class);
    }
}
