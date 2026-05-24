package storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class StorageIntegrationTest {

    @Autowired private WebApplicationContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private String registerAndLogin(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    @Test
    void fullFlow_registerLoginUploadListDownloadDelete() throws Exception {
        String token = registerAndLogin("flowuser", "flow@test.com", "flowpass1");

        // upload
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "integration test".getBytes());
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("hello.txt"))
                .andReturn();

        long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        // list — paginated response
        mockMvc.perform(get("/api/files").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(fileId));

        // download
        mockMvc.perform(get("/api/files/" + fileId + "/download").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("integration test"));

        // delete
        mockMvc.perform(delete("/api/files/" + fileId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 404 after delete
        mockMvc.perform(get("/api/files/" + fileId + "/download").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        registerAndLogin("dupuser", "dup@test.com", "duppass1");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dupuser\",\"email\":\"other@test.com\",\"password\":\"duppass1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidInput_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ab\",\"email\":\"notanemail\",\"password\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_withoutToken_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());
        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void upload_blockedExtension_returns500() throws Exception {
        String token = registerAndLogin("execuser", "exec@test.com", "execpass1");
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "virus".getBytes());
        mockMvc.perform(multipart("/api/files/upload").file(file)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void userProfile_returnsCurrentUser() throws Exception {
        String token = registerAndLogin("profileuser", "profile@test.com", "profpass1");
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("profileuser"))
                .andExpect(jsonPath("$.email").value("profile@test.com"));
    }
}
