package it.penny.api.trash;

public enum TrashEntity {
 CUSTOMER_RECORD("penny_customer_records","name","ACTIVITIES"),CUSTOMER("penny_customers","name","CUSTOMERS"),CONTACT("penny_customer_contacts","full_name","CUSTOMERS"),
 CATALOG_PRODUCT("penny_catalog_products","name","CATALOG"),INSTALLED_PRODUCT("penny_installed_products","name","PRODUCTS"),WORKSTATION("penny_workstations","name","WORKSTATIONS");
 final String table,label,permission;
 TrashEntity(String table,String label,String permission){this.table=table;this.label=label;this.permission=permission;}
 static TrashEntity parse(String value){try{return valueOf(value);}catch(IllegalArgumentException e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Tipo elemento non valido");}}
}
