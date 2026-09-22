package com.example.employeemgt.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.example.employeemgt.model.Employee;
import com.example.employeemgt.repository.EmployeeRepository;
import com.example.employeemgt.security.SecurityConfig;
import com.example.employeemgt.service.EmployeeService;

@WebMvcTest(EmployeeController.class)
@ContextConfiguration(classes = {
        EmployeeController.class,
        SecurityConfig.class
})
public class EmployeePaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeRepository employeeRepository;

    @MockBean
    private EmployeeService employeeService;

    @Test
    void getAllEmployeesDefaultsToPageZeroSizeTenAndNullSearch() throws Exception {
        Page<Employee> employeePage = new PageImpl<>(
                Collections.singletonList(sampleEmployee()),
                PageRequest.of(0, 10),
                1);

        when(employeeService.getEmployees(eq(0), eq(10), isNull()))
                .thenReturn(employeePage);

        mockMvc.perform(get("/api/employees")
                        .with(httpBasic("employee-demo", "EmployeeDemo#2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("John Doe"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(employeeService).getEmployees(eq(0), eq(10), isNull());
    }

    @Test
    void getAllEmployeesPropagatesExplicitPageAndSize() throws Exception {
        Page<Employee> employeePage = new PageImpl<>(
                Collections.singletonList(sampleEmployee()),
                PageRequest.of(2, 5),
                11);

        when(employeeService.getEmployees(eq(2), eq(5), isNull()))
                .thenReturn(employeePage);

        mockMvc.perform(get("/api/employees")
                        .param("page", "2")
                        .param("size", "5")
                        .with(httpBasic("employee-demo", "EmployeeDemo#2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(employeeService).getEmployees(eq(2), eq(5), isNull());
    }

    @Test
    void getAllEmployeesPropagatesSearchTermToService() throws Exception {
        Page<Employee> employeePage = new PageImpl<>(
                Collections.singletonList(sampleEmployee()),
                PageRequest.of(0, 10),
                1);

        when(employeeService.getEmployees(eq(0), eq(10), eq("john")))
                .thenReturn(employeePage);

        mockMvc.perform(get("/api/employees")
                        .param("search", "john")
                        .param("page", "0")
                        .param("size", "10")
                        .with(httpBasic("employee-demo", "EmployeeDemo#2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("John Doe"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(employeeService).getEmployees(eq(0), eq(10), eq("john"));
    }

    private Employee sampleEmployee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setName("John Doe");
        employee.setEmail("john.doe@example.com");
        employee.setSalary(50000.0);
        employee.setStatus(true);
        return employee;
    }
}
