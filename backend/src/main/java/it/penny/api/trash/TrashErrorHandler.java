package it.penny.api.trash;
import it.penny.api.common.ApiError;
import it.penny.api.customers.CustomerController;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice(assignableTypes={TrashController.class,CustomerController.class,it.penny.api.customers.ContactController.class})
public class TrashErrorHandler {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ApiError> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(ApiError.of("TRASH_REQUEST_ERROR",e.getReason()==null?"Richiesta non valida":e.getReason()));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiError> linked(){return ResponseEntity.status(409).body(ApiError.of("TRASH_LINKED","Operazione non possibile: elemento collegato ad altri dati o genitore nel cestino. Ripristina prima il genitore oppure conserva l'elemento in archivio."));}
 @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class) ResponseEntity<ApiError> concurrent(){return ResponseEntity.status(409).body(ApiError.of("CONCURRENT_UPDATE","Elemento modificato contemporaneamente. Ricarica e riprova."));}
}
