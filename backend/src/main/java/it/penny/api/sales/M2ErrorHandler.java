package it.penny.api.sales;
import it.penny.api.common.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes={SalesController.class,InventoryController.class,AllocationsController.class,WorkstationsController.class,OverviewController.class})
public class M2ErrorHandler {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<ApiError> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(ApiError.of("M2_REQUEST_ERROR",e.getReason()==null?"Richiesta non valida":e.getReason()));}
 @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
 ResponseEntity<ApiError> concurrent(){return ResponseEntity.status(409).body(ApiError.of("CONCURRENT_UPDATE","Dati modificati da un’altra operazione. Ricarica e riprova."));}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiError> conflict(){return ResponseEntity.status(409).body(ApiError.of("M2_CONFLICT","Quantità non disponibile, nome duplicato o collegamento incompatibile. Libera le allocazioni prima di annullare o dismettere."));}
}
