package com.example.employeemgt.e2e.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.HasAuthentication;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.UsernameAndPassword;
import org.openqa.selenium.chrome.ChromeDriver;

class SeleniumAuthenticationTest {

    private static final String BASE_URL = "http://localhost:8080/";
    private static final String ADMIN_USERNAME = "admin-demo";

    private ChromeDriver driver;

    @BeforeEach
    void setUp() {
        String adminPassword = System.getProperty("e2e.admin.password");

        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                "Missing required system property: e2e.admin.password");
        }

        driver = new ChromeDriver();
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));
        ((HasAuthentication) driver).register(
                UsernameAndPassword.of(ADMIN_USERNAME, adminPassword));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void adminCredentialsGrantAccessToEmployeesEndpoint() {
        driver.get(BASE_URL);

        Object status = ((JavascriptExecutor) driver).executeAsyncScript(
                "var callback = arguments[arguments.length - 1];"
                + "fetch('/api/employees?page=0&size=10')"
                + "  .then(function(response) { callback(response.status); })"
                + "  .catch(function() { callback(-1); });"
        );

        assertThat(status).isEqualTo(200L);
    }
}
