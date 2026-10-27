package it.penny.api.records;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
public record CustomerRecordInput(Long customerId,Long siteId,String kind,String name,Long productTypeId,Long saleId,LocalDate startsOn,LocalDate expiresOn,String status,String notes,JsonNode details) {}
