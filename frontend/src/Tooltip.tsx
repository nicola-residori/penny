import React,{cloneElement,useId,useState,type ReactElement,type ButtonHTMLAttributes} from 'react';
import {createPortal} from 'react-dom';
import './tooltip.css';

/* Preserve the direct tab children; a portal escapes scrolling tab-strip clipping. */
export default function Tooltip({text,children}:{text:string;children:ReactElement<ButtonHTMLAttributes<HTMLButtonElement>>}){
 const [host,setHost]=useState<HTMLElement>(document.body);
 const id=useId(),[hovered,setHovered]=useState(false),[focused,setFocused]=useState(false),[dismissed,setDismissed]=useState(false),[position,setPosition]=useState({left:0,top:0});
 function locate(button:HTMLButtonElement){setHost(button.closest('dialog')??document.body);const rect=button.getBoundingClientRect();setPosition({left:Math.max(8,Math.min(rect.left,window.innerWidth-276)),top:Math.max(8,Math.min(rect.bottom+8,window.innerHeight-100))});}
 const button=cloneElement(children,{
  'aria-describedby':[children.props['aria-describedby'],id].filter(Boolean).join(' '),
  onMouseEnter:e=>{locate(e.currentTarget);setDismissed(false);setHovered(true);children.props.onMouseEnter?.(e);},
  onMouseLeave:e=>{setHovered(false);children.props.onMouseLeave?.(e);},
  onFocus:e=>{locate(e.currentTarget);setDismissed(false);setFocused(true);children.props.onFocus?.(e);},
  onKeyDown:e=>{if(e.key==='Escape'&&(hovered||focused)&&!dismissed){setDismissed(true);e.stopPropagation();e.preventDefault();}children.props.onKeyDown?.(e);},
  onBlur:e=>{setFocused(false);children.props.onBlur?.(e);}
 });
 return <>{button}{createPortal(<span className="control-tooltip" id={id} role="tooltip" style={{...position,visibility:(hovered||focused)&&!dismissed?'visible':'hidden'}}>{text}</span>,host)}</>;
}
