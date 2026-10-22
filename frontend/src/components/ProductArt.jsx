import { useId } from 'react';

// Ảnh minh họa vẽ bằng SVG cho sản phẩm chưa có ảnh (danh mục mẫu Tiki không kèm ảnh).
// - Hình theo danh mục: mỗi nhóm danh mục (giày thể thao, balo, ví...) có 1 hình vẽ riêng.
// - Trong cùng danh mục, mỗi sản phẩm khác nhau ở màu, nền, họa tiết, góc nghiêng và chiều
//   quay, tính từ mã sản phẩm nên cùng sản phẩm luôn ra cùng 1 hình ở mọi trang.

/** Số giả ngẫu nhiên 32 bit từ mã sản phẩm (mỗi seed cho 1 dãy số khác nhau). */
function hashOf(id, seed) {
  let h = (Number(id) || 0) ^ Math.imul(seed, 0x9e3779b1);
  h = Math.imul(h ^ (h >>> 16), 0x85ebca6b);
  h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35);
  return (h ^ (h >>> 16)) >>> 0;
}

/** Bảng màu và cách trình bày của 1 sản phẩm. */
function styleOf(id) {
  const hue = hashOf(id, 1) % 360;
  const accentShift = [150, 180, 40, 210][hashOf(id, 2) % 4];
  const accentHue = (hue + accentShift) % 360;
  const dark = hashOf(id, 3) % 5 === 0;
  return {
    main: dark ? `hsl(${hue} 25% 28%)` : `hsl(${hue} 58% 56%)`,
    accent: `hsl(${accentHue} 62% ${dark ? 60 : 44}%)`,
    light: `hsl(${hue} 30% 92%)`,
    ink: `hsl(${hue} 30% 20%)`,
    bg: `hsl(${(hue + 25) % 360} 45% 95%)`,
    pattern: `hsl(${hue} 45% 82%)`,
    patternKind: hashOf(id, 4) % PATTERNS.length,
    rotate: (hashOf(id, 5) % 21) - 10,
    flip: hashOf(id, 6) % 2 === 1,
    scale: 0.9 + (hashOf(id, 7) % 13) / 100,
    variant: hashOf(id, 8) % 3,
  };
}

const PATTERNS = [
  null,
  (c) => <circle cx="6" cy="6" r="1.6" fill={c} />,
  (c) => <path d="M0 12 12 0" stroke={c} strokeWidth="2" />,
  (c) => <path d="M12 0V12H0" fill="none" stroke={c} strokeWidth="1" />,
  (c) => <path d="M0 8q3-4 6 0t6 0" fill="none" stroke={c} strokeWidth="1.4" />,
  (c) => <path d="M6 3v6M3 6h6" stroke={c} strokeWidth="1.4" />,
];

const line = { strokeLinecap: 'round', strokeLinejoin: 'round' };

// ---- Hình vẽ theo nhóm danh mục, khung 120 × 120. s = bảng màu, v = biến thể nhỏ (0-2). ----

function Sneaker({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path
        d="M14 82c0-8 4-12 12-14l20-6c6-10 12-14 20-14h6c4 10 14 16 28 18 8 1 8 8 8 16H14Z"
        fill={s.main}
      />
      <path d="M14 82h94v4c0 4-3 6-7 6H20c-4 0-6-2-6-6Z" fill={s.light} />
      <path
        d={v === 1 ? 'M40 76c18-2 34-8 46-16' : 'M42 78l20-12 18 8'}
        fill="none"
        stroke={s.accent}
        strokeWidth="5"
      />
      <path d="M56 56l8 6M62 52l8 6M68 50l7 5" fill="none" strokeWidth="2" />
    </g>
  );
}

function Boot({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M38 20h32v46c18 4 32 10 34 20v6H32l4-30Z" fill={s.main} />
      <path d="M32 86h72v4c0 2-2 4-4 4H34c-2 0-2-2-2-4Z" fill={s.ink} />
      <path d={v === 2 ? 'M38 20h32v12H38Z' : 'M38 20h32v6H38Z'} fill={s.accent} />
      {v !== 1 && <path d="M70 40v20" fill="none" stroke={s.light} strokeWidth="3" />}
    </g>
  );
}

function Heel({ s, wedge }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      {wedge ? (
        <path d="M16 56c20-6 40 4 56 16l28 6c6 2 6 8 6 12H62L20 74c-6-4-6-14-4-18Z" fill={s.main} />
      ) : (
        <path
          d="M16 56c20-6 40 4 56 16l28 6c6 2 6 8 6 12H70c-12-8-26-14-40-16l-4 26h-6l-2-30c-4-4-4-10-2-14Z"
          fill={s.main}
        />
      )}
      {wedge && <path d="M20 74l42 16H24c-4 0-6-4-4-16Z" fill={s.accent} />}
      <path d="M40 60c8 2 16 6 22 10" fill="none" stroke={s.light} strokeWidth="3" />
      {!wedge && <circle cx="58" cy="66" r="4" fill={s.accent} />}
    </g>
  );
}

function LowShoe({ s, v, strap }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M12 84c0-12 6-18 18-20 14-2 24-6 32-10h18c16 4 28 12 30 22v8Z" fill={s.main} />
      <path d="M12 84h98v3c0 3-2 5-5 5H17c-3 0-5-2-5-5Z" fill={v === 2 ? s.accent : s.ink} />
      <path d="M56 58c8 6 22 6 30 0" fill={s.light} />
      {strap && <path d="M46 62l40 6v8l-40-6Z" fill={s.accent} />}
      {!strap && <circle cx="42" cy="66" r="3.5" fill={s.accent} />}
    </g>
  );
}

function Sandal({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M12 86c0-6 6-8 14-8h74c6 0 10 2 10 8s-4 6-10 6H24c-8 0-12-2-12-6Z" fill={s.light} />
      <path d="M30 80c4-14 14-22 26-22s20 8 24 22" fill="none" stroke={s.main} strokeWidth="7" />
      <path d="M78 80c2-10 8-16 16-16" fill="none" stroke={s.accent} strokeWidth="6" />
      {v !== 0 && <path d="M44 80c2-8 6-12 12-12" fill="none" stroke={s.accent} strokeWidth="5" />}
    </g>
  );
}

function Slipper({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path
        d="M14 82c0-8 8-12 20-12h62c10 0 14 6 14 12s-4 10-14 10H34c-12 0-20-4-20-10Z"
        fill={s.light}
      />
      <path d="M58 74c0-16 10-26 24-26s20 10 20 26" fill={s.main} />
      {v === 1 ? (
        <path d="M66 56h28" fill="none" stroke={s.accent} strokeWidth="5" />
      ) : (
        <circle cx="80" cy="60" r="6" fill={s.accent} />
      )}
    </g>
  );
}

function Backpack({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M50 22c0-6 4-10 10-10s10 4 10 10" fill="none" />
      <rect x="30" y="22" width="60" height="78" rx="18" fill={s.main} />
      <rect x="40" y="62" width="40" height="30" rx="8" fill={s.accent} />
      <path d="M40 72h40" fill="none" strokeWidth="2" />
      {v !== 2 && <path d="M38 40h44" fill="none" stroke={s.light} strokeWidth="3" />}
    </g>
  );
}

function Briefcase({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M48 36v-8h24v8" fill="none" />
      <rect x="16" y="36" width="88" height="56" rx="8" fill={s.main} />
      <path d={v === 1 ? 'M16 54h88' : 'M22 44h76'} fill="none" stroke={s.accent} strokeWidth="4" />
      <rect x="54" y="50" width="12" height="8" rx="2" fill={s.light} />
    </g>
  );
}

function Handbag({ s, v, tote }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      {tote ? (
        <>
          <path d="M40 46c0-24 40-24 40 0" fill="none" strokeWidth="4" />
          <path d="M24 46h72l-4 52H28Z" fill={s.main} />
          <path d="M28 64h64" fill="none" stroke={s.accent} strokeWidth={v === 0 ? 8 : 4} />
        </>
      ) : (
        <>
          <path d="M44 48c0-18 32-18 32 0" fill="none" strokeWidth="4" />
          <path d="M26 48h68l8 44c0 4-2 6-6 6H24c-4 0-6-2-6-6Z" fill={s.main} />
          <path d="M26 48h68l-6 18H32Z" fill={s.accent} />
          <circle cx="60" cy="66" r="4" fill={s.light} />
        </>
      )}
    </g>
  );
}

function Crossbody({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M30 60 76 14" fill="none" stroke={s.accent} strokeWidth="4" />
      <rect x="24" y="56" width="66" height="44" rx={v === 1 ? 20 : 10} fill={s.main} />
      <path d="M24 70c20 10 46 10 66 0" fill="none" />
      <rect x="52" y="72" width="10" height="8" rx="2" fill={s.light} />
    </g>
  );
}

function Suitcase({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M50 26V14h20v12" fill="none" strokeWidth="3" />
      <rect x="30" y="26" width="60" height="74" rx="10" fill={s.main} />
      <path
        d="M46 34v58M60 34v58M74 34v58"
        fill="none"
        stroke={v === 1 ? s.accent : s.light}
        strokeWidth="3"
      />
      <circle cx="40" cy="106" r="4" fill={s.ink} />
      <circle cx="80" cy="106" r="4" fill={s.ink} />
    </g>
  );
}

function Duffel({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M46 50c0-14 28-14 28 0" fill="none" strokeWidth="4" />
      <rect x="14" y="50" width="92" height="46" rx="23" fill={s.main} />
      <path d="M30 58h60" fill="none" stroke={s.light} strokeWidth="2" strokeDasharray="4 3" />
      <path
        d={v === 2 ? 'M14 74h92' : 'M36 50v46M84 50v46'}
        fill="none"
        stroke={s.accent}
        strokeWidth="5"
      />
    </g>
  );
}

function Wallet({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <rect x="18" y="36" width="84" height="56" rx="8" fill={s.main} />
      <path d={v === 1 ? 'M18 44h84v18H18Z' : 'M70 52h32v20H70Z'} fill={s.accent} />
      <circle cx={v === 1 ? 60 : 82} cy={v === 1 ? 62 : 62} r="4" fill={s.light} />
      <path d="M26 82h30" fill="none" stroke={s.light} strokeWidth="2" />
    </g>
  );
}

function Belt({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M8 66c30-18 74-18 104 0v14c-30-18-74-18-104 0Z" fill={s.main} />
      <rect
        x="44"
        y="50"
        width="28"
        height="26"
        rx="4"
        fill="none"
        stroke={s.accent}
        strokeWidth="5"
      />
      <path d="M58 54v18" fill="none" stroke={s.accent} strokeWidth="3" />
      {v !== 0 && <circle cx="90" cy="64" r="2" fill={s.light} stroke="none" />}
      {v !== 0 && <circle cx="98" cy="67" r="2" fill={s.light} stroke="none" />}
    </g>
  );
}

function Glasses({ s, v, frameOnly }) {
  const lens = frameOnly ? s.light : s.ink;
  const r = v === 1 ? 'round' : 'square';
  return (
    <g {...line} stroke={frameOnly ? s.main : s.ink} strokeWidth={frameOnly ? 4 : 3}>
      {r === 'round' ? (
        <>
          <circle cx="38" cy="64" r="18" fill={lens} />
          <circle cx="82" cy="64" r="18" fill={lens} />
        </>
      ) : (
        <>
          <rect x="16" y="48" width="42" height="32" rx="10" fill={lens} />
          <rect x="62" y="48" width="42" height="32" rx="10" fill={lens} />
        </>
      )}
      <path d="M54 60c4-4 8-4 12 0M16 56 8 50M104 56l8-6" fill="none" />
      {!frameOnly && <path d="M24 56l10-6" fill="none" stroke={s.accent} strokeWidth="3" />}
    </g>
  );
}

function Cap({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M22 76c0-26 16-40 38-40s36 14 36 40Z" fill={s.main} />
      <path d="M90 76c10 0 20 4 22 10H60l4-10Z" fill={s.accent} />
      <path d="M60 36v40" fill="none" stroke={s.light} strokeWidth="2" />
      {v === 1 && <circle cx="60" cy="36" r="4" fill={s.accent} />}
    </g>
  );
}

function SunHat({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <ellipse cx="60" cy="78" rx="50" ry="14" fill={s.main} />
      <path d="M34 76c0-26 10-38 26-38s26 12 26 38" fill={s.main} />
      <path d="M34 68c16 4 36 4 52 0v8c-16 4-36 4-52 0Z" fill={s.accent} />
      {v === 2 && <circle cx="80" cy="70" r="5" fill={s.light} />}
    </g>
  );
}

function Scarf({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M22 30c24 10 52 10 76 0l-6 22c-20 8-44 8-64 0Z" fill={s.main} />
      <path d="M70 48l8 46H58Z" fill={s.main} />
      <path
        d={v === 1 ? 'M30 40c18 6 42 6 60 0' : 'M64 60l10 0M62 72h12'}
        fill="none"
        stroke={s.accent}
        strokeWidth="4"
      />
      <path d="M60 94v8M66 94v8M72 94v8" fill="none" strokeWidth="2" />
    </g>
  );
}

function Mask({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path
        d="M28 52c-14 0-18 22-2 26M92 52c14 0 18 22 2 26"
        fill="none"
        stroke={s.accent}
        strokeWidth="3"
      />
      <path d="M28 46c20-8 44-8 64 0v34c-20 10-44 10-64 0Z" fill={s.main} />
      <path
        d="M30 58h60M30 68h60"
        fill="none"
        stroke={v === 1 ? s.accent : s.light}
        strokeWidth="2.5"
      />
    </g>
  );
}

function Glove({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path
        d="M38 98V58l-10-12c-4-6 2-12 8-8l8 8V24c0-6 10-6 10 0v22-28c0-6 10-6 10 0v28-24c0-6 10-6 10 0v24-16c0-6 10-6 10 0v40c0 10-6 20-14 22v8Z"
        fill={s.main}
      />
      <path d="M38 92h42" fill="none" stroke={s.accent} strokeWidth={v === 2 ? 10 : 6} />
    </g>
  );
}

function Sock({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M44 14h30v52l18 18c8 8 2 22-10 18L46 88c-4-4-2-10-2-16Z" fill={s.main} />
      <path d="M44 14h30v12H44Z" fill={s.accent} />
      <path d="M82 102c10 2 14-10 8-16l-8-8-10 12Z" fill={s.accent} />
      {v !== 0 && <path d="M44 40h30M44 50h30" fill="none" stroke={s.light} strokeWidth="3" />}
    </g>
  );
}

function Brooch({ s, v }) {
  const petals = v === 1 ? 6 : 5;
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      {Array.from({ length: petals }, (_, i) => (
        <ellipse
          key={i}
          cx="60"
          cy="40"
          rx="11"
          ry="20"
          fill={s.main}
          transform={`rotate(${(360 / petals) * i} 60 60)`}
        />
      ))}
      <circle cx="60" cy="60" r="12" fill={s.accent} />
      <circle cx="56" cy="56" r="3" fill={s.light} stroke="none" />
    </g>
  );
}

function HairBow({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path d="M60 60 22 38c-8 0-10 44 0 44Z" fill={s.main} />
      <path d="M60 60l38-22c8 0 10 44 0 44Z" fill={s.main} />
      <rect x="52" y="50" width="16" height="20" rx="5" fill={s.accent} />
      {v !== 2 && <path d="M30 50l14 8M30 70l14-6" fill="none" stroke={s.light} strokeWidth="2" />}
    </g>
  );
}

function ShoeBrush({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <rect x="18" y="44" width="84" height="22" rx="10" fill={s.main} />
      <path
        d="M26 66v22M36 66v22M46 66v22M56 66v22M66 66v22M76 66v22M86 66v22M94 66v22"
        fill="none"
        stroke={s.accent}
        strokeWidth="3"
      />
      {v === 1 && <circle cx="96" cy="34" r="8" fill={s.light} />}
    </g>
  );
}

function NeckPillow({ s, v }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path
        d="M34 92C14 78 14 40 36 28s48-12 54 8c8 24 6 50-4 58-6 4-12 0-12-6 0-12 6-24 0-38-6-12-24-12-30 0-6 14 2 30 4 38 2 8-8 10-14 4Z"
        fill={s.main}
      />
      <path d="M44 30c10-4 22-4 32 0" fill="none" stroke={s.accent} strokeWidth={v === 0 ? 6 : 3} />
    </g>
  );
}

function Generic({ s }) {
  return (
    <g {...line} stroke={s.ink} strokeWidth="2.5">
      <path
        d="M40 30 26 40l6 14 8-4v44h40V50l8 4 6-14-14-10c-2 6-8 10-20 10s-18-4-20-10Z"
        fill={s.main}
      />
      <path d="M40 30c2 6 8 10 20 10s18-4 20-10" fill="none" stroke={s.accent} strokeWidth="4" />
    </g>
  );
}

// Danh mục → hình. So theo từ khóa trong tên (chữ thường), thứ tự quan trọng: từ cụ thể trước.
const RULES = [
  ['thể thao', (p) => <Sneaker {...p} />],
  ['boots', (p) => <Boot {...p} />],
  ['cao gót', (p) => <Heel {...p} />],
  ['đế xuồng', (p) => <Heel {...p} wedge />],
  ['búp bê', (p) => <LowShoe {...p} />],
  ['giày lười', (p) => <LowShoe {...p} strap />],
  ['giày tây', (p) => <LowShoe {...p} />],
  ['vệ sinh giày', (p) => <ShoeBrush {...p} />],
  ['sandal', (p) => <Sandal {...p} />],
  ['dép', (p) => <Slipper {...p} />],
  ['balo', (p) => <Backpack {...p} />],
  ['cặp', (p) => <Briefcase {...p} />],
  ['tote', (p) => <Handbag {...p} tote />],
  ['đeo chéo', (p) => <Crossbody {...p} />],
  ['đựng đồ du lịch', (p) => <Duffel {...p} />],
  ['túi xách', (p) => <Handbag {...p} />],
  ['vali', (p) => <Suitcase {...p} />],
  ['ví', (p) => <Wallet {...p} />],
  ['thắt lưng', (p) => <Belt {...p} />],
  ['kính mát', (p) => <Glasses {...p} />],
  ['gọng kính', (p) => <Glasses {...p} frameOnly />],
  ['nón nữ', (p) => <SunHat {...p} />],
  ['nón', (p) => <Cap {...p} />],
  ['khăn', (p) => <Scarf {...p} />],
  ['khẩu trang', (p) => <Mask {...p} />],
  ['găng tay', (p) => <Glove {...p} />],
  ['vớ', (p) => <Sock {...p} />],
  ['ghim cài', (p) => <Brooch {...p} />],
  ['phụ kiện tóc', (p) => <HairBow {...p} />],
  ['du lịch', (p) => <NeckPillow {...p} />],
];

function drawingFor(category) {
  const name = (category ?? '').normalize('NFC').toLowerCase();
  const rule = RULES.find(([keyword]) => name.includes(keyword));
  return rule ? rule[1] : (p) => <Generic {...p} />;
}

/**
 * Ảnh minh họa của 1 sản phẩm: hình theo danh mục, màu và họa tiết theo mã sản phẩm.
 * Chỉ để trang trí (aria-hidden): tên sản phẩm luôn hiện ngay bên cạnh.
 */
function ProductArt({ productId, categoryName, className = '' }) {
  const patternId = `art-${useId().replace(/[^a-zA-Z0-9-]/g, '')}`;
  const s = styleOf(productId);
  const pattern = PATTERNS[s.patternKind];
  const draw = drawingFor(categoryName);
  const transform = `translate(60 62) rotate(${s.rotate}) scale(${s.flip ? -s.scale : s.scale} ${s.scale}) translate(-60 -60)`;
  return (
    <svg
      viewBox="0 0 120 120"
      className={`product-image product-art ${className}`}
      aria-hidden="true"
      focusable="false"
    >
      {pattern && (
        <defs>
          <pattern id={patternId} width="12" height="12" patternUnits="userSpaceOnUse">
            {pattern(s.pattern)}
          </pattern>
        </defs>
      )}
      <rect width="120" height="120" fill={s.bg} />
      {pattern && <rect width="120" height="120" fill={`url(#${patternId})`} />}
      <ellipse cx="60" cy="104" rx="38" ry="5" fill={s.ink} opacity="0.12" />
      <g transform={transform}>{draw({ s, v: s.variant })}</g>
    </svg>
  );
}

export default ProductArt;
