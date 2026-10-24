package it.penny.api;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class HealthControllerTest {
  @Test void healthReturnsOk() throws Exception {
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();
    mvc.perform(get("/api/health")).andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("ok"));
  }
}
