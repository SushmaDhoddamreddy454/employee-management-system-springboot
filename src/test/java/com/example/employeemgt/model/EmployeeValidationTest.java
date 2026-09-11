package com.example.employeemgt.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Calendar;
import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class EmployeeValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    private Employee validEmployee() {
        Employee employee = new Employee();
        employee.setName("Jane Doe");
        employee.setEmail("jane.doe@example.com");
        employee.setDob(pastDate());
        employee.setSalary(50000.0);
        employee.setStatus(true);
        return employee;
    }

    private Date pastDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -30);
        return calendar.getTime();
    }

    private Date futureDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 1);
        return calendar.getTime();
    }

    @Test
    void validEmployeeHasNoViolations() {
        Set<ConstraintViolation<Employee>> violations = validator.validate(validEmployee());
        assertThat(violations).isEmpty();
    }

    @Test
    void blankNameIsRejected() {
        Employee employee = validEmployee();
        employee.setName("");
        Set<ConstraintViolation<Employee>> violations = validator.validate(employee);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void invalidEmailFormatIsRejected() {
        Employee employee = validEmployee();
        employee.setEmail("not-an-email");
        Set<ConstraintViolation<Employee>> violations = validator.validate(employee);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void negativeSalaryIsRejected() {
        Employee employee = validEmployee();
        employee.setSalary(-1.0);
        Set<ConstraintViolation<Employee>> violations = validator.validate(employee);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("salary"));
    }

    @Test
    void futureDobIsRejected() {
        Employee employee = validEmployee();
        employee.setDob(futureDate());
        Set<ConstraintViolation<Employee>> violations = validator.validate(employee);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("dob"));
    }
}
