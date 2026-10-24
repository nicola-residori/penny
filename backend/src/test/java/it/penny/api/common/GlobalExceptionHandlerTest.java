package it.penny.api.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    @Test
    void apiErrorHasStableFields() {
        ApiError error = ApiError.of("VALIDATION_ERROR", "Dati della richiesta non validi");
        assertEquals("VALIDATION_ERROR", error.code());
        assertNotNull(error.timestamp());
        assertFalse(error.message().isBlank());
    }
}
