import React from 'react';
const classes=(...values:(string|undefined|false)[])=>values.filter(Boolean).join(' ');
export type ButtonProps=React.ComponentPropsWithRef<'button'>&{
 variant?:'primary'|'secondary'|'danger'|'ghost';
 /** Navigation, tabs and clickable content keep their layout, sharing states and targets. */
 presentation?:'control'|'content';
};
export function Button({variant='secondary',presentation='control',className,...props}:ButtonProps){
 return <button {...props} className={classes('ui-button','ui-button--'+variant,presentation==='content'&&'ui-button--content',className)}/>;
}
export function Input({className,type='text',...props}:React.ComponentPropsWithRef<'input'>){
 return <input {...props} type={type} className={classes('ui-input','ui-input--'+type,className)}/>;
}
export function SearchInput({className,type='search',...props}:React.ComponentPropsWithRef<'input'>){
 return <Input {...props} type={type} className={classes('ui-search-input',className)}/>;
}
export function SearchControl({label,labelHidden=false,className,...props}:React.ComponentPropsWithRef<'input'>&{label:React.ReactNode;labelHidden?:boolean}){
 return <label className={classes('ui-search-control',labelHidden&&'ui-search-control--label-hidden')}><span className="ui-search-control-label">{label}</span><SearchInput {...props} className={className}/></label>;
}
export function Select({className,...props}:React.ComponentPropsWithRef<'select'>){
 return <select {...props} className={classes('ui-select',className)}/>;
}
export function Textarea({className,...props}:React.ComponentPropsWithRef<'textarea'>){
 return <textarea {...props} className={classes('ui-textarea',className)}/>;
}
export function Table({className,...props}:React.ComponentPropsWithRef<'table'>){
 return <div className="ui-table-scroll" tabIndex={0} role="region" aria-label={props['aria-label']?props['aria-label']+' · scorrimento':'Tabella · scorrimento orizzontale'}><table {...props} className={classes('ui-table',className)}/></div>;
}
