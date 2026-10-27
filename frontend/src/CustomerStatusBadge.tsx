import React from 'react';
import './customer-status.css';
export default function CustomerStatusBadge({status}:{status?:'PROSPECT'|'CUSTOMER'}){
 const prospect=status==='PROSPECT';const label=prospect?'Prospect':'Cliente';
 return <span className={'customer-status-badge '+(prospect?'customer-status-badge--prospect':'customer-status-badge--customer')} aria-label={'Stato commerciale: '+label}>{label}</span>;
}
