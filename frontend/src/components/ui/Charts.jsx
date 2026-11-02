export function ProgressRing({ value = 0, size = 142, stroke = 10, label = 'progress', color = 'var(--accent)' }) {
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const safeValue = Math.max(0, Math.min(100, Number(value) || 0));
  return <div className="progress-ring" role="img" aria-label={`${label}: ${Math.round(safeValue)} percent`} style={{ width: size, height: size, '--ring-color': color }}>
    <svg viewBox={`0 0 ${size} ${size}`}><circle className="ring-track" cx={size / 2} cy={size / 2} r={radius} strokeWidth={stroke} /><circle className="ring-value" cx={size / 2} cy={size / 2} r={radius} strokeWidth={stroke} strokeDasharray={circumference} strokeDashoffset={circumference * (1 - safeValue / 100)} /></svg>
    <div className="ring-copy"><strong>{Math.round(safeValue)}<small>%</small></strong><span>{label}</span></div>
  </div>;
}

export function BarChart({ items, className = '' }) {
  const maximum = Math.max(1, ...items.map((item) => Number(item.value) || 0));
  return <div className={`bar-chart ${className}`} role="list">{items.map((item) => <div className="bar-chart-row" role="listitem" key={item.label}>
    <span>{item.label}</span><div className="bar-track"><span style={{ width: `${Math.min(100, (Number(item.value) || 0) / maximum * 100)}%`, '--bar-tone': item.color || 'var(--accent)' }} /></div><b>{item.display ?? item.value}</b>
  </div>)}</div>;
}

export function RadarChart({ labels, values, className = '' }) {
  const center = 100; const radius = 69; const points = labels.map((label, index) => {
    const angle = -Math.PI / 2 + index * Math.PI * 2 / labels.length;
    const r = radius * Math.max(0, Math.min(5, Number(values[index]) || 0)) / 5;
    return { label, x: center + radius * Math.cos(angle), y: center + radius * Math.sin(angle), vx: center + r * Math.cos(angle), vy: center + r * Math.sin(angle) };
  });
  const polygon = (ratio) => labels.map((_, index) => { const angle = -Math.PI / 2 + index * Math.PI * 2 / labels.length; return `${center + radius * ratio * Math.cos(angle)},${center + radius * ratio * Math.sin(angle)}`; }).join(' ');
  return <svg className={`radar-chart ${className}`} viewBox="0 0 200 200" role="img" aria-label="Skill ratings radar chart">
    {[.33,.66,1].map((ratio) => <polygon key={ratio} points={polygon(ratio)} className="radar-grid" />)}
    {points.map((point) => <line key={point.label} x1={center} y1={center} x2={point.x} y2={point.y} className="radar-axis" />)}
    <polygon points={points.map((point) => `${point.vx},${point.vy}`).join(' ')} className="radar-area" />
    {points.map((point) => <text key={point.label} x={point.x} y={point.y} className="radar-label" textAnchor="middle" dominantBaseline="middle">{point.label}</text>)}
  </svg>;
}

export function Timeline({ items }) {
  return <ol className="timeline">{items.map((item, index) => <li className={item.done ? 'is-done' : ''} key={`${item.title}-${index}`}><span className="timeline-dot" /><div><strong>{item.title}</strong><small>{item.meta}</small></div><time>{item.date}</time></li>)}</ol>;
}

export function ScoreBadge({ score, label = 'match' }) {
  return <span className="score-badge"><strong>{score}</strong><small>{label}</small></span>;
}
