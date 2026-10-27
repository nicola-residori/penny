package it.penny.api.records;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController
public class CustomerRecordController {
 private final CustomerRecordService records;
 private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
 private CustomerRecordInput input(com.fasterxml.jackson.databind.JsonNode value){try{return json.treeToValue(value,CustomerRecordInput.class);}catch(Exception ex){throw CustomerRecordService.bad("Campi scheda non validi");}}
 public CustomerRecordController(CustomerRecordService records){this.records=records;}
 @GetMapping("/api/customer-records") public Map<String,Object> list(@RequestParam String kind,@RequestParam(required=false) Long customerId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="asc") String dir,Authentication auth){return records.list(kind,customerId,q,page,size,sort,dir,auth);}
 @GetMapping("/api/customer-records/{id}") public Map<String,Object> get(@PathVariable long id,Authentication auth){return records.get(id,auth);}
 @PostMapping("/api/customer-records") public Map<String,Object> create(@RequestBody com.fasterxml.jackson.databind.JsonNode value,Authentication auth){return records.create(input(value),auth);}
 @PutMapping("/api/customer-records/{id}") public Map<String,Object> update(@PathVariable long id,@RequestBody com.fasterxml.jackson.databind.JsonNode value,Authentication auth){return records.update(id,input(value),auth);}
 @GetMapping("/api/customer-record-deadlines") public Map<String,Object> deadlines(@RequestParam LocalDate from,@RequestParam LocalDate to,Authentication auth){return records.deadlines(from,to,auth);}
}
