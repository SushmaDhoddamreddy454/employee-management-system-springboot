package com.example.employeemgt.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.example.employeemgt.controller.EmployeeController;
import com.example.employeemgt.model.Employee;
import com.example.employeemgt.repository.EmployeeRepository;
import com.example.employeemgt.service.EmployeeService;

@WebMvcTest(EmployeeController.class)
@ContextConfiguration(classes = {EmployeeController.class, SecurityConfig.class})
public class EmployeeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeRepository employeeRepository;

    @MockBean
    private EmployeeService employeeService;

    private static final String VALID_EMPLOYEE_JSON =
            "{\"name\":\"John Doe\",\"email\":\"john.doe@example.com\",\"dob\":\"1990-01-01\",\"salary\":50000.0,\"status\":true}";

    private Employee sampleEmployee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setName("John Doe");
        employee.setEmail("john.doe@example.com");
        employee.setSalary(50000.0);
        employee.setStatus(true);
        return employee;
    }

    @Test
    void unauthenticatedGetCollectionReturns401() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedPostReturns401() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EMPLOYEE_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedPutReturns401() throws Exception {
        mockMvc.perform(put("/api/employees/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EMPLOYEE_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedDeleteReturns401() throws Exception {
        mockMvc.perform(delete("/api/employees/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCanGetCollection() throws Exception {
        when(employeeRepository.findAll()).thenReturn(Collections.singletonList(sampleEmployee()));

        mockMvc.perform(get("/api/employees").with(httpBasic("employee-demo", "EmployeeDemo#2026")))
                .andExpect(status().isOk());
    }

    @Test
    void employeeCanGetById() throws Exception {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(sampleEmployee()));

        mockMvc.perform(get("/api/employees/1").with(httpBasic("employee-demo", "EmployeeDemo#2026")))
                .andExpect(status().isOk());
    }

    @Test
    void employeeCannotPost() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .with(httpBasic("employee-demo", "EmployeeDemo#2026"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EMPLOYEE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotPut() throws Exception {
        mockMvc.perform(put("/api/employees/1")
                        .with(httpBasic("employee-demo", "EmployeeDemo#2026"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EMPLOYEE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/employees/1").with(httpBasic("employee-demo", "EmployeeDemo#2026")))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrCanGet() throws Exception {
        when(employeeRepository.findAll()).thenReturn(Collections.singletonList(sampleEmployee()));

        mockMvc.perform(get("/api/employees").with(httpBasic("hr-demo", "HrDemo#2026")))
                .andExpect(status().isOk());
    }

    @Test
    void hrCanPost() throws Exception {
        when(employeeService.createEmployee(any(Employee.class))).thenReturn(sampleEmployee());

        mockMvc.perform(post("/api/employees")
                        .with(httpBasic("hr-demo", "HrDemo#2026"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EMPLOYEE_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void hrCanPut() throws Exception {
        when(employeeService.updateEmployee(anyLong(), any(Employee.class))).thenReturn(sampleEmployee());

        mockMvc.perform(put("/api/employees/1")
                        .with(httpBasic("hr-demo", "HrDemo#2026"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EMPLOYEE_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void hrCanDelete() throws Exception {
        mockMvc.perform(delete("/api/employees/1").with(httpBasic("hr-demo", "HrDemo#2026")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanGet() throws Exception {
        when(employeeRepository.findAll()).thenReturn(Collections.singletonList(sampleEmployee()));

        mockMvc.perform(get("/api/employees").with(httpBasic("admin-demo", "AdminDemo#2026")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanPost() throws Exception {
        when(employeeService.createEmployee(any(Employee.class))).thenReturn(sampleEmployee());

        mockMvc.perform(post("/api/employees")
                        .with(httpBasic("admin-demo", "AdminDemo#2026"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EMPLOYEE_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanPut() throws Exception {
        when(employeeService.updateEmployee(anyLong(), any(Employee.class))).thenReturn(sampleEmployee());

        mockMvc.perform(put("/api/employees/1")
                        .with(httpBasic("admin-demo", "AdminDemo#2026"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EMPLOYEE_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanDelete() throws Exception {
        mockMvc.perform(delete("/api/employees/1").with(httpBasic("admin-demo", "AdminDemo#2026")))
                .andExpect(status().isOk());
    }

    @Test
    void invalidCredentialsReturns401() throws Exception {
        mockMvc.perform(get("/api/employees").with(httpBasic("admin-demo", "WrongPassword#1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rootResourceIsNotBlockedBySecurity() throws Exception {
        int statusCode = mockMvc.perform(get("/")).andReturn().getResponse().getStatus();
        Assertions.assertNotEquals(401, statusCode);
        Assertions.assertNotEquals(403, statusCode);
    }

    @Test
    void indexHtmlIsNotBlockedBySecurity() throws Exception {
        int statusCode = mockMvc.perform(get("/index.html")).andReturn().getResponse().getStatus();
        Assertions.assertNotEquals(401, statusCode);
        Assertions.assertNotEquals(403, statusCode);
    }

    @Test
    void scriptJsIsNotBlockedBySecurity() throws Exception {
        int statusCode = mockMvc.perform(get("/script.js")).andReturn().getResponse().getStatus();
        Assertions.assertNotEquals(401, statusCode);
        Assertions.assertNotEquals(403, statusCode);
    }

    @Test
    void styleCssIsNotBlockedBySecurity() throws Exception {
        int statusCode = mockMvc.perform(get("/style.css")).andReturn().getResponse().getStatus();
        Assertions.assertNotEquals(401, statusCode);
        Assertions.assertNotEquals(403, statusCode);
    }
}
