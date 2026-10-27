import React from 'react';
import './pagination.css';
type Props={total:number;page:number;onPageChange:(page:number)=>void;singular:string;plural:string;label:string;disabled?:boolean;previousLabel?:string;nextLabel?:string};
export default function Pagination({total,page,onPageChange,singular,plural,label,disabled=false,previousLabel='Precedente',nextLabel='Successiva'}:Props){
 const pages=Math.max(1,Math.ceil(total/20));
 return <div className="penny-pagination" aria-label={`Risultati ${label.toLowerCase()}`}><span className="penny-page-count">{total} {total===1?singular:plural}</span>{pages>1&&<nav className="penny-page-controls" aria-label={`Paginazione ${label.toLowerCase()}`}><button type="button" disabled={disabled||page===0} onClick={()=>onPageChange(page-1)}>{previousLabel}</button><span>Pagina {page+1} di {pages}</span><button type="button" disabled={disabled||page>=pages-1} onClick={()=>onPageChange(page+1)}>{nextLabel}</button></nav>}</div>;
}
