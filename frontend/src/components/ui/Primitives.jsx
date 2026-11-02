import { useEffect } from 'react';
import Icon from '../Icon.jsx';

export function Button({ variant = 'primary', size = 'md', icon, children, className = '', ...props }) {
  return <button className={`ui-button ui-button--${variant} ui-button--${size} ${className}`} {...props}>
    {icon && <Icon name={icon} size={16} />}{children}
  </button>;
}

export function IconButton({ label, children, className = '', ...props }) {
  return <button className={`icon-button-ui ${className}`} aria-label={label} title={label} {...props}>{children}</button>;
}

export function Card({ variant = 'standard', className = '', as: Element = 'section', children, ...props }) {
  return <Element className={`cockpit-card cockpit-card--${variant} ${className}`} {...props}>{children}</Element>;
}

export function CardHeading({ eyebrow, title, action, children }) {
  return <div className="card-heading"><div>{eyebrow && <p className="card-eyebrow">{eyebrow}</p>}{title && <h2>{title}</h2>}{children}</div>{action}</div>;
}

export function Badge({ tone = 'neutral', children, className = '' }) {
  return <span className={`ui-badge ui-badge--${tone} ${className}`}>{children}</span>;
}

export function MetricCard({ label, value, change, caption, icon = 'chart', tone = 'violet', className = '', children }) {
  return <Card className={`metric-card metric-card--${tone} ${className}`}>
    <div className="metric-top"><span className="metric-label">{label}</span><span className="metric-icon"><Icon name={icon} size={16} /></span></div>
    <strong className="metric-value">{value}</strong>
    {change && <Badge tone={tone}>{change}</Badge>}
    {caption && <p className="metric-caption">{caption}</p>}{children}
  </Card>;
}

export function EmptyState({ icon = 'sparkles', title, description, action }) {
  return <div className="empty-state-ui"><span className="empty-state-icon"><Icon name={icon} size={21} /></span><h3>{title}</h3><p>{description}</p>{action}</div>;
}

export function Skeleton({ className = '', style }) { return <span className={`skeleton ${className}`} style={style} aria-hidden="true" />; }

export function Toast({ tone = 'info', children, onDismiss }) {
  return <div className={`toast toast--${tone}`} role="status"><span>{children}</span>{onDismiss && <IconButton label="Dismiss message" onClick={onDismiss}><Icon name="close" size={15} /></IconButton>}</div>;
}

export function Modal({ open, title, children, onClose }) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (event) => { if (event.key === 'Escape') onClose?.(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return <div className="modal-scrim" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose?.(); }}><section className="modal-panel" role="dialog" aria-modal="true" aria-label={title}><div className="modal-heading"><h2>{title}</h2><IconButton label="Close dialog" onClick={onClose}><Icon name="close" /></IconButton></div>{children}</section></div>;
}

export function Drawer({ open, title, children, onClose }) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (event) => { if (event.key === 'Escape') onClose?.(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return <div className="cockpit-drawer-scrim" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose?.(); }}>
    <aside className="cockpit-drawer" role="dialog" aria-modal="true" aria-label={title}><header className="drawer-heading"><h2>{title}</h2><IconButton label="Close panel" onClick={onClose}><Icon name="close" size={17} /></IconButton></header><div className="drawer-content">{children}</div></aside>
  </div>;
}

export function Field({ label, hint, error, children, className = '' }) {
  return <label className={`field-ui ${className}`}><span>{label}</span>{children}{hint && <small>{hint}</small>}{error && <small className="field-error">{error}</small>}</label>;
}

export function TextInput({ label, hint, error, ...props }) {
  return <Field label={label} hint={hint} error={error}><input {...props} /></Field>;
}

export function SelectInput({ label, children, hint, ...props }) {
  return <Field label={label} hint={hint}><select {...props}>{children}</select></Field>;
}

export function SearchInput({ label = 'Search', ...props }) {
  return <label className="search-input"><Icon name="search" size={17} /><span className="sr-only">{label}</span><input type="search" {...props} /></label>;
}

export function Tag({ children, onRemove, tone = 'neutral' }) {
  return <span className={`ui-tag ui-tag--${tone}`}>{children}{onRemove && <button type="button" aria-label={`Remove ${children}`} onClick={onRemove}>×</button>}</span>;
}

export function MultiSelect({ label, options, value = [], onChange }) {
  const toggle = (option) => onChange(value.includes(option) ? value.filter((item) => item !== option) : [...value, option]);
  return <fieldset className="multi-select-ui"><legend>{label}</legend><div>{options.map((option) => <button key={option} type="button" aria-pressed={value.includes(option)} className={value.includes(option) ? 'selected' : ''} onClick={() => toggle(option)}>{option}</button>)}</div></fieldset>;
}

export function RangeInput({ label, value, min = 0, max = 100, onChange, ...props }) {
  return <Field label={`${label} · ${value}`}><input type="range" min={min} max={max} value={value} onChange={(event) => onChange?.(Number(event.target.value))} {...props} /></Field>;
}

export function FileDrop({ label, accept, onChange }) {
  return <label className="file-drop"><Icon name="file" size={20} /><strong>{label}</strong><small>Choose a file from your device</small><input type="file" accept={accept} onChange={(event) => onChange?.(event.target.files?.[0] || null)} /></label>;
}
