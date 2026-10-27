# M2 API contract (authoritative for frontend/backend work)
All lists {items:[],total:number,page:number,size:number}; page default0 size20 max100, q max100 literal search. All changes CSRF/session through api.ts. GET overview at /api/overview returns nullable numeric counters: customers,catalog,installed,workstations,soldQuantity,allocatedQuantity,availableQuantity,salesAmount. Null means unauthorized, never display false zero.
Profile adds booleans canReadSales,canWriteSales,canReadInventory,canReadAllocations,canWriteAllocations,canReadWorkstations,canWriteWorkstations. Capability canWriteAllocations includes ALLOCATIONS_WRITE, INVENTORY_READ, PRODUCTS_READ, PRODUCTS_WRITE; backend additionally enforces them. No catalog permission required to read inventory; commercial sales fields not in inventory.

## Sales
GET /api/sales?q=&customerId=&from=YYYY-MM-DD&to=&status=CONFIRMED|CANCELLED&page=&size=
GET /api/sales/{id}
POST /api/sales {customerId,catalogProductId,soldOn,quantity,unitPrice,reference,notes} -> {id}; unitPrice numeric >=0 up to999999999.99 2 decimal digits; price default0. Quantity integer1..1000000. Requires SALES_WRITE, CUSTOMERS_READ, CATALOG_READ; active customer/catalog for new sales.
POST /api/sales/{id}/cancel {reason} -> {cancelled:true}; requires SALES_WRITE; blocked if allocations exist. Cancelling twice returns409.
Sale fields: id,customerId,customerName,catalogProductId,code,productName,origin,category,soldOn,quantity,unitPrice,totalAmount,reference,notes,status,cancellationReason,allocatedQuantity,availableQuantity,createdAt.
GET /api/sales/summary with same q/customerId/from/to and pagination (no status) -> {items,total,page,size,totals:{soldQuantity,allocatedQuantity,availableQuantity,totalAmount}}. Only confirmed. Item fields customerId,customerName,catalogProductId,code,productName,soldQuantity,allocatedQuantity,availableQuantity,totalAmount. Values may be JSON numbers or decimal strings; use Intl.NumberFormat EUR.

## Inventory
GET /api/inventory?q=&customerId=&from=&to=&page=&size= -> list of confirmed individual sales WITHOUT PRICE fields: id,customerId,customerName,catalogProductId,code,productName,origin,category,soldOn,quantity,allocatedQuantity,availableQuantity. Includes totals:{soldQuantity,allocatedQuantity,availableQuantity} across complete filter.

## Allocations
GET /api/allocations?saleId=&installedProductId=&customerId=&page=&size= -> list
POST /api/allocations {saleId,installedProductId,quantity} -> {id}
PUT /api/allocations/{id} {quantity} -> {updated:true}
DELETE /api/allocations/{id} -> {released:true} (audit retained)
Fields id,saleId,installedProductId,customerId,catalogProductId,quantity,installedName,workstationName,code,productName,soldOn. No prices. Same sale/install pair unique; display409 for duplicate. Selection installed-products?customerId= returns catalogProductId; client filter for selected catalog; hide RETIRED. Server enforces all invariants. Totals do not change sold quantities.

## Workstations
GET /api/workstations?q=&customerId=&active=true|false&page=&size=
GET /api/workstations/{id}
POST /api/workstations {customerId,name,location,notes,active} -> {id}
PUT /api/workstations/{id} same -> {updated:true}
Fields id,customerId,customerName,name,location,notes,active,createdAt,updatedAt. Unique case-insensitive name per customer. Customer change blocked with linked installations.
InstalledProductInput adds workstationId nullable. GET installed responses add workstationId and workstationName (name only when WORKSTATIONS_READ; null otherwise). An existing workstation link can be kept without workstation read; changing it requires WORKSTATIONS_READ. Archived links preserved; new links to archived station blocked. Frontend ProductsPanel should add searchable/paginated workstation selector and detail.

## Deferred commercial models
No VAT, invoices, payments or commission base. No edits to confirmed sale identity/quantity; cancel + recreate. No physical sale deletion. Date filters inclusive, range validation400. Empty totals zeros. Dates ISO.
