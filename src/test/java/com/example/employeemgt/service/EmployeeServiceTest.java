package com.example.employeemgt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import com.example.employeemgt.model.Employee;
import com.example.employeemgt.repository.EmployeeRepository;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);
        employee.setName("Jane Doe");
        employee.setEmail("jane.doe@example.com");
        employee.setSalary(50000.0);
        employee.setStatus(true);
    }

    @Test
    void createEmployeeSucceedsWhenEmailIsUnique() {
        when(employeeRepository.existsByEmail(employee.getEmail())).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        Employee result = employeeService.createEmployee(employee);

        assertThat(result).isEqualTo(employee);
        verify(employeeRepository).save(employee);
    }

    @Test
    void createEmployeeRejectsDuplicateEmail() {
        when(employeeRepository.existsByEmail(employee.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> employeeService.createEmployee(employee))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Email already in use");

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateEmployeeSucceedsWhenEmailUnchanged() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsByEmailAndIdNot(eq(employee.getEmail()), eq(1L))).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

        Employee result = employeeService.updateEmployee(1L, employee);

        assertThat(result).isEqualTo(employee);
    }

    @Test
    void updateEmployeeRejectsEmailAlreadyUsedByAnotherEmployee() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsByEmailAndIdNot(anyString(), anyLong())).thenReturn(true);

        assertThatThrownBy(() -> employeeService.updateEmployee(1L, employee))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Email already in use");

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateEmployeeThrowsWhenEmployeeNotFound() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.updateEmployee(99L, employee))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Employee not found");
    }

    @Test
    void getEmployeesReturnsRepositoryPageWhenSearchIsNull() {
        Page<Employee> expectedPage = new PageImpl<>(
                Collections.singletonList(employee),
                PageRequest.of(0, 10),
                1);

        when(employeeRepository.findAll(PageRequest.of(0, 10)))
                .thenReturn(expectedPage);

        Page<Employee> result = employeeService.getEmployees(0, 10, null);

        assertThat(result).isEqualTo(expectedPage);

        verify(employeeRepository).findAll(PageRequest.of(0, 10));
        verify(employeeRepository, never())
                .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        anyString(), anyString(), any(Pageable.class));
    }

    @Test
    void getEmployeesUsesFindAllWhenSearchIsBlank() {
        Page<Employee> expectedPage = new PageImpl<>(
                Collections.singletonList(employee),
                PageRequest.of(0, 10),
                1);

        when(employeeRepository.findAll(PageRequest.of(0, 10)))
                .thenReturn(expectedPage);

        Page<Employee> result = employeeService.getEmployees(0, 10, "   ");

        assertThat(result).isEqualTo(expectedPage);

        verify(employeeRepository).findAll(PageRequest.of(0, 10));
        verify(employeeRepository, never())
                .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        anyString(), anyString(), any(Pageable.class));
    }

    @Test
    void getEmployeesDelegatesToSearchRepositoryMethodWhenSearchProvided() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Employee> expectedPage =
                new PageImpl<>(Collections.singletonList(employee), pageable, 1);

        when(employeeRepository
                .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        "john", "john", pageable))
                .thenReturn(expectedPage);

        Page<Employee> result =
                employeeService.getEmployees(0, 10, "john");

        assertThat(result).isEqualTo(expectedPage);

        verify(employeeRepository)
                .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        "john", "john", pageable);

        verify(employeeRepository, never())
                .findAll(any(Pageable.class));
    }
}
