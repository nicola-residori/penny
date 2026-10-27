package it.penny.api.records;
import it.penny.api.common.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice(assignableTypes=CustomerRecordController.class)
public class CustomerRecordErrorHandler {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ApiError> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(ApiError.of("RECORD_REQUEST_ERROR",e.getReason()==null?"Scheda non valida":e.getReason()));}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<ApiError> linked(){return ResponseEntity.status(409).body(ApiError.of("RECORD_CONFLICT","Collegamenti non validi o modificati: ricarica cliente, tipologia e partecipanti."));}
 @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class) ResponseEntity<ApiError> concurrent(){return ResponseEntity.status(409).body(ApiError.of("CONCURRENT_UPDATE","Scheda modificata contemporaneamente. Ricarica e riprova."));}
}
