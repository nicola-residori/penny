import React from 'react';
import ProductsPanel from './ProductsPanel';
type Props={canReadSales?:boolean;canWriteSales?:boolean;canReadProducts?:boolean;canWriteProducts?:boolean;canReadWorkstations?:boolean;canWriteWorkstations?:boolean;canReadCustomers?:boolean;canReadCatalog?:boolean;canDeleteRecords?:boolean};
export default function HardwarePanel({profile}:{profile:Props}){
 return profile.canReadProducts?<ProductsPanel canReadFinancials={!!profile.canReadSales} canWriteFinancials={!!profile.canWriteSales} category="HARDWARE" canWrite={!!profile.canWriteProducts} canReadCustomers={!!profile.canReadCustomers} canReadCatalog={!!profile.canReadCatalog} canDeleteRecords={!!profile.canDeleteRecords}/>:null;
}
