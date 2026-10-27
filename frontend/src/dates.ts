/** HTML date fields use the viewer's calendar day, including around UTC midnight. */
export function dateInputToday(date=new Date(),timeZone?:string):string{
 const parts=new Intl.DateTimeFormat('en-CA',{year:'numeric',month:'2-digit',day:'2-digit',...(timeZone?{timeZone}:{})}).formatToParts(date);
 const value=(type:string)=>parts.find(part=>part.type===type)?.value??'';
 return `${value('year')}-${value('month')}-${value('day')}`;
}
