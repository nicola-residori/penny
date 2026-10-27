const euro=new Intl.NumberFormat('it-IT',{style:'currency',currency:'EUR'});
/** Decimal strings stay exact: format whole euros as BigInt, then insert cents. */
export function formatEuro(value:number|string):string{
 if(typeof value==='number')return euro.format(value);
 const match=/^([+-]?)(\d+)(?:\.(\d+))?$/.exec(value.trim());
 if(!match)return euro.format(Number(value));
 const fraction=match[3]||'';
 let cents=BigInt(match[2])*100n+BigInt((fraction+'00').slice(0,2));
 if(fraction.length>2&&fraction[2]>='5')cents+=1n;
 const units=cents/100n,decimal=(cents%100n).toString().padStart(2,'0');
 const signed=match[1]==='-'?(units===0n?-0:-units):units;
 return euro.formatToParts(signed).map(part=>part.type==='fraction'?decimal:part.value).join('');
}
