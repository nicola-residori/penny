package it.penny.api.customers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={CustomerController.class,ContactController.class})
public class CustomerBusinessErrors {
 @ExceptionHandler(DataIntegrityViolationException.class)
 public ResponseEntity<java.util.Map<String,String>> conflict(DataIntegrityViolationException ex){return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("message","Dati in conflitto: verificare referenti associati, stato azienda e collegamenti tra aziende (senza cicli)"));}
}
