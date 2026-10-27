package it.penny.api.sales;

import org.springframework.jdbc.core.namedparam.*;
import java.time.LocalDate;
import java.util.*;

/** Shared quantity queries keep commercial totals and technical availability in agreement. */
class SalesQueries {
 static final String FROM=" FROM penny_sales s JOIN penny_customers c ON c.id=s.customer_id LEFT JOIN (SELECT sale_id,sum(quantity) AS allocated FROM penny_sale_allocations GROUP BY sale_id) a ON a.sale_id=s.id";
 static final String WHERE=" WHERE (CAST(:customer AS bigint) IS NULL OR s.customer_id=:customer) AND (CAST(:from AS date) IS NULL OR s.sold_on>=:from) AND (CAST(:to AS date) IS NULL OR s.sold_on<=:to) AND (lower(s.product_name) LIKE :q ESCAPE '\\' OR lower(s.product_code) LIKE :q ESCAPE '\\' OR lower(c.name) LIKE :q ESCAPE '\\' OR lower(coalesce(s.reference,'')) LIKE :q ESCAPE '\\')";
 static final String FIELDS="s.id,s.customer_id AS \"customerId\",c.name AS \"customerName\",s.catalog_product_id AS \"catalogProductId\",s.product_code AS code,s.product_name AS \"productName\",s.origin,s.category,s.sold_on AS \"soldOn\",s.quantity,coalesce(a.allocated,0) AS \"allocatedQuantity\",CASE WHEN s.status='CONFIRMED' THEN s.quantity-coalesce(a.allocated,0) ELSE 0 END AS \"availableQuantity\"";
 final NamedParameterJdbcTemplate named;
 SalesQueries(M2Support support){named=new NamedParameterJdbcTemplate(support.db);}
 static MapSqlParameterSource params(String q,Long customer,LocalDate from,LocalDate to,int page,int size){M2Support.pagination(page,size);M2Support.dates(from,to);return new MapSqlParameterSource().addValue("q",M2Support.like(q)).addValue("customer",customer).addValue("from",from).addValue("to",to).addValue("size",size).addValue("offset",page*size);}
 Map<String,Object> totals(String where,MapSqlParameterSource p,boolean prices){
  String fields="coalesce(sum(s.quantity),0) AS \"soldQuantity\",coalesce(sum(coalesce(a.allocated,0)),0) AS \"allocatedQuantity\",coalesce(sum(s.quantity-coalesce(a.allocated,0)),0) AS \"availableQuantity\""+(prices?",coalesce(sum(s.quantity*s.unit_price),0)::numeric(20,2) AS \"totalAmount\"":"");
  return M2Support.money(named.queryForMap("SELECT "+fields+FROM+where+" AND s.status='CONFIRMED'",p));
 }
}
