package com.coretrust.bank.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HTTP requests, real JWTs, real database: the whole security chain end to end. */
class
AuthFlowIntegrationTest extends IntegrationTest {

    @Test
    void register_login_singleSession_andForceLoginRevokesTheOldToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Sathya", "email": "sathya@test.com", "phone": "9876543210", "password": "Sathya@123"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        String firstToken = login(false);
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sathya@test.com"));

        // A second login while the first session is active is refused...
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SESSION_ALREADY_ACTIVE"));

        // ...unless forced, which ends the first session immediately
        String secondToken = login(true);
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("SESSION_REVOKED"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPassword_returnsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Sathya", "email": "sathya@test.com", "phone": "9876543210", "password": "Sathya@123"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "sathya@test.com", "password": "Wrong@123"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    private String login(boolean force) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(force)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String credentials(boolean force) {
        return """
                {"email": "sathya@test.com", "password": "Sathya@123", "forceLogin": %s}
                """.formatted(force);
    }
}