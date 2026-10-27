import React,{useId} from 'react';
import Icon,{type IconName} from './Icons';

export function DossierHeader({title,eyebrow,icon='catalog',avatar,subtitle,badges,actions}:{title:string;eyebrow:string;icon?:IconName;avatar?:React.ReactNode;subtitle?:React.ReactNode;badges?:React.ReactNode;actions?:React.ReactNode}){
 return <header className="dossier-header">{avatar??<span className="dossier-symbol"><Icon name={icon}/></span>}<div className="dossier-identity"><span className="dossier-eyebrow">{eyebrow}</span><h2>{title}</h2>{subtitle&&<p>{subtitle}</p>}{badges&&<div className="dossier-badges">{badges}</div>}</div>{actions&&<div className="dossier-actions">{actions}</div>}</header>;
}
export function DossierSection({title,icon='profile',children}:{title:string;icon?:IconName;children:React.ReactNode}){
 const id=useId();return <section className="dossier-section" aria-labelledby={id}><header><Icon name={icon}/><h3 id={id}>{title}</h3></header><div className="dossier-section-body">{children}</div></section>;
}
export function DossierFields({items}:{items:readonly (readonly [string,React.ReactNode])[]}){
 return <dl className="dossier-fields">{items.map(([label,value])=><React.Fragment key={label}><dt>{label}</dt><dd>{value===null||value===undefined||value===''?'—':value}</dd></React.Fragment>)}</dl>;
}
export function DossierMetrics({items}:{items:{label:string;value:React.ReactNode;tone?:'blue'|'green'|'purple'|'amber'}[]}){
 return <div className="dossier-metrics">{items.map(({label,value,tone='blue'})=><article key={label} className={'dossier-metric dossier-metric--'+tone}><strong>{value}</strong><span>{label}</span></article>)}</div>;
}
