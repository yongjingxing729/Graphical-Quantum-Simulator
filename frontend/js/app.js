"use strict";

/* ============================================================
 * Quantum Circuit Simulator — 前端逻辑（原生 JS，无依赖）
 * 线路模型：columns[列/时间步] = [ {type, targets:[...], angle?} ]
 * 一列 = Strange 的一个 Step（同一列内不同比特可并行施加门）。
 * ============================================================ */

// ---- 门元数据 ----
const GATES = {
  H:       { arity: 1, label: "H",        group: "single" },
  X:       { arity: 1, label: "X",        group: "single" },
  Y:       { arity: 1, label: "Y",        group: "single" },
  Z:       { arity: 1, label: "Z",        group: "single" },
  I:       { arity: 1, label: "I",        group: "single" },
  S:       { arity: 1, label: "S",        group: "single" },
  T:       { arity: 1, label: "T",        group: "single" },
  M:       { arity: 1, label: "M",        group: "single" },
  P:       { arity: 1, label: "P(θ)",     group: "rotation", angle: true },
  RX:      { arity: 1, label: "Rx(θ)",    group: "rotation", angle: true },
  RY:      { arity: 1, label: "Ry(θ)",    group: "rotation", angle: true },
  RZ:      { arity: 1, label: "Rz(θ)",    group: "rotation", angle: true },
  CNOT:    { arity: 2, label: "CNOT",     group: "multi" },
  CZ:      { arity: 2, label: "CZ",       group: "multi" },
  SWAP:    { arity: 2, label: "SWAP",     group: "multi" },
  TOFFOLI: { arity: 3, label: "Toffoli",  group: "multi" },
};

const MIN_QUBITS = 1;
const MAX_QUBITS = 12;
const MIN_COLS = 1;
const MAX_COLS = 10000;      // 单量子位门数上限（一列最多一个门 / 比特）
const DEFAULT_COLS = 8;

// 虚拟滚动：避免 1 万列时 DOM 爆炸，只渲染可视窗口内的列
const COL_W = 60;            // 44px 格子 + 16px 间距
const WINDOW_COLS = 60;      // 一次渲染的列数
const WINDOW_BUFFER = 10;    // 预渲染缓冲列

// 合法的单比特输入态（与后端保持一致）
const INPUT_STATES = [
  { value: "0", label: "|0⟩" },
  { value: "1", label: "|1⟩" },
  { value: "+", label: "|+⟩" },
  { value: "-", label: "|−⟩" },
  { value: "i", label: "|i⟩" },
  { value: "-i", label: "|−i⟩" },
];

// ---- 状态 ----
let qubits = 2;
let columns = Array.from({ length: DEFAULT_COLS }, () => []);
let inputs = Array.from({ length: qubits }, () => "0"); // 每个比特的初始输入态
let selectedGate = null;          // 当前选中的门类型（string|null）
let pending = null;               // 多比特门放置中：{type, col, wires:[]}
let undoStack = [];
let dividers = new Set();        // 列右侧分隔线：Set<列索引>，d 表示“第 d 列之后”有一条竖线

// ---- DOM 引用 ----
const $ = (id) => document.getElementById(id);

function init() {
  buildPalette();
  applyDividerColor();
  bindEvents();
  renderCircuit();
  loadMeta();
  loadUpstream();
}

// ============================================================
// 门面板
// ============================================================
function buildPalette() {
  buildGroup("singleGates", ["H", "X", "Y", "Z", "I", "S", "T", "M"]);
  buildGroup("rotationGates", ["P", "RX", "RY", "RZ"]);
  buildGroup("multiGates", ["CNOT", "CZ", "SWAP", "TOFFOLI"]);
}

function buildGroup(containerId, types) {
  const container = $(containerId);
  types.forEach((t) => {
    const btn = document.createElement("button");
    btn.className = "gate-btn";
    btn.dataset.gate = t;
    btn.textContent = GATES[t].label;
    btn.title = t + (GATES[t].angle ? "（需在下方填写角度 θ）" : "");
    btn.addEventListener("click", () => selectGate(t, btn));
    container.appendChild(btn);
  });
}

function selectGate(type, btn) {
  if (selectedGate === type) {
    selectedGate = null;
    pending = null;
  } else {
    selectedGate = type;
    pending = null;
  }
  refreshPaletteSelection();
  updateSelectionInfo();
  renderCircuit();
}

function refreshPaletteSelection() {
  document.querySelectorAll(".gate-btn").forEach((b) => {
    b.classList.toggle("selected", b.dataset.gate === selectedGate);
  });
}

function updateSelectionInfo() {
  const el = $("selectionInfo");
  if (pending && GATES[pending.type]) {
    const meta = GATES[pending.type];
    el.textContent = `正在放置 ${meta.label}：已选 ${pending.wires.length}/${meta.arity} 比特，请继续点击同列的其他比特线`;
  } else if (selectedGate) {
    const meta = GATES[selectedGate];
    if (meta.arity === 1) {
      el.textContent = `已选择 ${meta.label}，点击线路格子放置${meta.angle ? "（使用下方角度 θ）" : ""}`;
    } else if (selectedGate === "CNOT") {
      el.textContent = "已选择 CNOT：第 1 次点击 = 控制位(●)，第 2 次点击 = 目标位(⊕)";
    } else if (selectedGate === "TOFFOLI") {
      el.textContent = "已选择 Toffoli：前 2 次点击 = 控制位(●)，第 3 次点击 = 目标位(⊕)";
    } else if (selectedGate === "CZ" || selectedGate === "SWAP") {
      el.textContent = `已选择 ${meta.label}：点击两个要操作的比特（对称）`;
    } else {
      el.textContent = `已选择 ${meta.label}（${meta.arity} 比特）：点击第一个比特`;
    }
  } else {
    el.textContent = "未选择门（点击已有门可删除）";
  }
}

// ============================================================
// 线路渲染
// ============================================================
function renderCircuit() {
  renderLabels();

  const track = $("circuitTrack");
  const totalW = Math.max(MIN_COLS, columns.length) * COL_W;
  const totalH = 20 + qubits * 52; // col-head(20) + 每行格子(44) + 行间距(8)
  if (track) {
    track.style.width = totalW + "px";
    track.style.height = totalH + "px";
  }
  clampScroll();
  renderWindow();

  $("qubitCount").value = qubits;
  $("colCount").value = columns.length;
  renderValidation();
}

// 左侧比特标签 + 输入态选择（固定，不随列水平滚动）
function renderLabels() {
  const labels = $("wireLabels");
  if (!labels) return;
  labels.innerHTML = "";
  const lhead = document.createElement("div");
  lhead.className = "col-head";
  lhead.textContent = "输入";
  labels.appendChild(lhead);
  for (let w = 0; w < qubits; w++) {
    const l = document.createElement("div");
    l.className = "wire-label";
    const name = document.createElement("span");
    name.className = "qname";
    name.textContent = "q" + w;
    const sel = document.createElement("select");
    sel.className = "input-select";
    sel.title = `设置 q${w} 的初始输入态`;
    INPUT_STATES.forEach((st) => {
      const opt = document.createElement("option");
      opt.value = st.value;
      opt.textContent = st.label;
      sel.appendChild(opt);
    });
    sel.value = inputs[w] || "0";
    sel.addEventListener("change", () => {
      inputs[w] = sel.value;
      renderValidation();
    });
    l.appendChild(name);
    l.appendChild(sel);
    labels.appendChild(l);
  }
}

function computeWinStart(scrollLeft) {
  const approx = Math.floor(scrollLeft / COL_W) - WINDOW_BUFFER;
  const maxStart = Math.max(0, columns.length - WINDOW_COLS);
  return Math.max(0, Math.min(approx, maxStart));
}

function renderWindow() {
  const win = $("circuitWindow");
  if (!win) return;
  const scroll = $("circuitScroll");
  const start = computeWinStart(scroll ? scroll.scrollLeft : 0);
  const end = Math.min(columns.length, start + WINDOW_COLS);
  win.innerHTML = "";
  win.style.transform = `translateX(${start * COL_W}px)`;
  for (let c = start; c < end; c++) {
    win.appendChild(renderColumn(c));
  }
}

function renderColumn(c) {
  const colEl = document.createElement("div");
  colEl.className = "circuit-column";
  const hasMeasure = (columns[c] || []).some((g) => g.type === "M");
  if (hasMeasure) {
    colEl.classList.add("has-measure");
  }

  const head = document.createElement("div");
  head.className = "col-head";
  head.textContent = "t" + c;
  if (hasMeasure) {
    const badge = document.createElement("span");
    badge.className = "measure-badge";
    badge.textContent = "⏼";
    badge.title = "测量列：线路在此处被测量（坍缩/断电）";
    head.appendChild(badge);
  }
  colEl.appendChild(head);

  for (let w = 0; w < qubits; w++) {
    const cell = document.createElement("div");
    cell.className = "cell";
    const gate = findGateAt(c, w);
    if (gate) {
      cell.classList.add("has-gate");
      applyRole(cell, gate, w);
      cell.title = describeGate(gate);
    } else if (isConnector(c, w)) {
      cell.classList.add("connector");
      cell.title = "连接线";
    } else {
      cell.classList.add("empty");
    }
    if (pending && pending.col === c && pending.wires.includes(w)) {
      cell.classList.add("pending-hint");
    }
    cell.dataset.col = c;
    cell.dataset.wire = w;
    cell.addEventListener("click", () => onCellClick(c, w));
    colEl.appendChild(cell);
  }

  // 列间分隔线（右键可增删）
  if (dividers.has(c)) {
    const divider = document.createElement("span");
    divider.className = "col-divider";
    divider.title = "分隔线（右键该列可删除）";
    colEl.appendChild(divider);
  }

  // 列后「+」：点击插入一个空列（空门），用于对齐/留白
  const plus = document.createElement("button");
  plus.type = "button";
  plus.className = "insert-plus";
  plus.title = "在此列后插入一个空列";
  plus.textContent = "+";
  plus.addEventListener("click", (e) => {
    e.stopPropagation();
    insertColumnAfter(c);
  });
  colEl.appendChild(plus);

  // 删除这一列
  const del = document.createElement("button");
  del.type = "button";
  del.className = "delete-col";
  del.title = "删除这一列";
  del.textContent = "×";
  del.addEventListener("click", (e) => {
    e.stopPropagation();
    removeColumnAt(c);
  });
  colEl.appendChild(del);

  // 右键菜单：分隔线 / 插列 / 删列
  colEl.addEventListener("contextmenu", (e) => showColumnContextMenu(e, c));

  return colEl;
}

function clampScroll() {
  const scroll = $("circuitScroll");
  if (!scroll) return;
  const maxLeft = Math.max(0, columns.length * COL_W - scroll.clientWidth);
  if (scroll.scrollLeft > maxLeft) {
    scroll.scrollLeft = maxLeft;
  }
}

let scrollRaf = 0;
function onScroll() {
  if (scrollRaf) return;
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0;
    renderWindow();
  });
}

function scrollToColumn(c) {
  const scroll = $("circuitScroll");
  if (!scroll) return;
  const maxLeft = Math.max(0, columns.length * COL_W - scroll.clientWidth);
  const target = Math.max(0, Math.min(c * COL_W, maxLeft));
  scroll.scrollLeft = target;
  renderWindow();
}

function scrollToEnd() {
  const scroll = $("circuitScroll");
  if (!scroll) return;
  scroll.scrollLeft = Math.max(0, columns.length * COL_W - scroll.clientWidth);
  renderWindow();
}

function findGateAt(col, wire) {
  const list = columns[col] || [];
  return list.find((g) => g.targets.includes(wire)) || null;
}

function isConnector(col, wire) {
  const list = columns[col] || [];
  for (const g of list) {
    if (g.targets.length >= 2) {
      const min = Math.min(...g.targets);
      const max = Math.max(...g.targets);
      if (wire > min && wire < max && !g.targets.includes(wire)) {
        return true;
      }
    }
  }
  return false;
}

function applyRole(cell, gate, wire) {
  const meta = GATES[gate.type];
  if (meta.arity === 1) {
    cell.textContent = meta.label;
    return;
  }
  if (gate.type === "CNOT") {
    if (wire === gate.targets[0]) cell.classList.add("control");
    else cell.classList.add("target");
  } else if (gate.type === "CZ") {
    cell.classList.add("control");
  } else if (gate.type === "SWAP") {
    cell.classList.add("swap-x");
  } else if (gate.type === "TOFFOLI") {
    if (wire === gate.targets[2]) cell.classList.add("target");
    else cell.classList.add("control");
  }
}

function describeGate(g) {
  const meta = GATES[g.type];
  if (meta.arity === 1) {
    const q = g.targets[0];
    const angleStr = g.angle != null ? "，θ=" + g.angle : "";
    return `${meta.label} on q${q}${angleStr}`;
  }
  return `${meta.label} on [${g.targets.map((t) => "q" + t).join(", ")}]`;
}

// ============================================================
// 交互：放置 / 删除门
// ============================================================
function onCellClick(col, wire) {
  if (!selectedGate) {
    // 未选择门：点击已有门则删除（多比特门只能点锚点格删除，避免误删整扇门）
    const g = findGateAt(col, wire);
    if (g) {
      const arity = GATES[g.type] ? GATES[g.type].arity : 1;
      if (arity >= 2 && g.targets[0] !== wire) {
        showHint(
          `多比特门 ${GATES[g.type].label} 占用了多个量子位，点击非锚点格不删除；` +
            `请点击锚点格 q${g.targets[0]} 删除整扇门`
        );
        return;
      }
      mutate(() => removeGateAt(col, wire));
    }
    return;
  }

  const meta = GATES[selectedGate];
  if (meta.arity === 1) {
    mutate(() => {
      ensureColumn(col);
      removeOverlapping(col, [wire]);
      const gate = { type: selectedGate, targets: [wire] };
      if (meta.angle) gate.angle = currentAngle();
      columns[col].push(gate);
    });
    return;
  }

  // 多比特门：依次点击各比特
  if (!pending || pending.type !== selectedGate) {
    pending = { type: selectedGate, col, wires: [wire] };
  } else if (pending.col !== col) {
    pending = { type: selectedGate, col, wires: [wire] };
  } else if (pending.wires.includes(wire)) {
    showHint(`q${wire} 已在本次选择中，请点击其它比特`);
  } else {
    pending.wires.push(wire);
  }

  if (pending && pending.wires.length === meta.arity) {
    const wires = pending.wires.slice();
    const type = pending.type;
    const pcol = pending.col;
    pending = null;
    mutate(() => {
      ensureColumn(pcol);
      removeOverlapping(pcol, wires);
      columns[pcol].push({ type, targets: wires });
    });
  }

  updateSelectionInfo();
  renderCircuit();
}

function ensureColumn(col) {
  while (columns.length <= col) columns.push([]);
}

function removeOverlapping(col, wires) {
  columns[col] = columns[col].filter((g) => !g.targets.some((t) => wires.includes(t)));
}

function removeGateAt(col, wire) {
  if (columns[col]) {
    columns[col] = columns[col].filter((g) => !g.targets.includes(wire));
  }
}

function currentAngle() {
  const v = parseFloat($("angleInput").value);
  return Number.isFinite(v) ? v : 0;
}

// ============================================================
// 撤销 / 快照
// ============================================================
function snapshot() {
  return {
    qubits,
    columns: JSON.parse(JSON.stringify(columns)),
    inputs: inputs.slice(),
    dividers: new Set(dividers),
  };
}

function restore(s) {
  qubits = s.qubits;
  columns = s.columns;
  inputs = s.inputs.slice();
  dividers = new Set(s.dividers || []);
  pending = null;
}

function mutate(fn) {
  undoStack.push(snapshot());
  if (undoStack.length > 30) undoStack.shift();
  fn();
  renderCircuit();
}

function undo() {
  const s = undoStack.pop();
  if (s) {
    restore(s);
    renderCircuit();
    updateSelectionInfo();
  }
}

// ============================================================
// 工具栏
// ============================================================
function setQubits(n) {
  if (!Number.isFinite(n)) return;
  const next = Math.max(MIN_QUBITS, Math.min(MAX_QUBITS, n));
  mutate(() => {
    qubits = next;
    while (inputs.length < qubits) inputs.push("0");
    if (inputs.length > qubits) inputs = inputs.slice(0, qubits);
    // 删除引用到被移除比特的门
    columns = columns.map((col) => col.filter((g) => g.targets.every((t) => t < qubits)));
  });
}

function setCols(n) {
  if (!Number.isFinite(n)) return;
  const next = Math.max(MIN_COLS, Math.min(MAX_COLS, n));
  mutate(() => {
    if (next > columns.length) {
      while (columns.length < next) columns.push([]);
    } else {
      columns = columns.slice(0, next);
      dividers = new Set([...dividers].filter((d) => d < next));
    }
  });
}

// 在指定列之后插入一个空列（空门，用于对齐/留白）
function insertColumnAfter(colIndex) {
  if (columns.length >= MAX_COLS) {
    showHint(`列数已达上限 ${MAX_COLS}（即单量子位门数上限）`);
    return;
  }
  mutate(() => {
    columns.splice(colIndex + 1, 0, []);
    adjustDividersOnInsert(colIndex + 1);
  });
  scrollToColumn(colIndex + 1);
}

// 批量：一次追加 N 个空列（放在末尾）
function addColsBatch(n) {
  const room = MAX_COLS - columns.length;
  if (room <= 0) {
    showHint(`列数已达上限 ${MAX_COLS}`);
    return;
  }
  const count = Math.max(1, Math.min(Math.floor(n || 1), room));
  mutate(() => {
    for (let i = 0; i < count; i++) {
      columns.push([]);
    }
  });
  scrollToEnd();
}

// 删除指定一列
function removeColumnAt(colIndex) {
  if (columns.length <= MIN_COLS) {
    showHint(`至少保留 ${MIN_COLS} 列`);
    return;
  }
  mutate(() => {
    columns.splice(colIndex, 1);
    adjustDividersOnRemove([colIndex]);
  });
}

// 批量删除列：tail=后几列，head=前几列，range=范围 a,b（闭区间）
function deleteColsBatch(mode, raw) {
  const text = String(raw == null ? "" : raw).trim();
  const maxRemove = columns.length - MIN_COLS;
  if (maxRemove <= 0) {
    showHint(`至少保留 ${MIN_COLS} 列，无法删除`);
    return;
  }

  let indices = [];
  if (mode === "tail") {
    const n = parseInt(text, 10);
    if (!Number.isFinite(n) || n <= 0) {
      showHint("请输入要删除的列数（正整数）");
      return;
    }
    const k = Math.min(n, maxRemove);
    for (let i = columns.length - k; i < columns.length; i++) indices.push(i);
  } else if (mode === "head") {
    const n = parseInt(text, 10);
    if (!Number.isFinite(n) || n <= 0) {
      showHint("请输入要删除的列数（正整数）");
      return;
    }
    const k = Math.min(n, maxRemove);
    for (let i = 0; i < k; i++) indices.push(i);
  } else {
    const parts = text.split(/[,，\-~]/).map((s) => parseInt(s.trim(), 10));
    if (parts.length < 2 || parts.some((x) => !Number.isFinite(x))) {
      showHint("范围格式：a,b 或 a-b（如 1,4，删除第 1~4 列）");
      return;
    }
    let a = Math.max(0, parts[0]);
    let b = Math.min(columns.length - 1, parts[1]);
    if (a > b) { const t = a; a = b; b = t; }
    const k = Math.min(b - a + 1, maxRemove);
    for (let i = a; i < a + k; i++) indices.push(i);
  }

  if (indices.length === 0) {
    showHint("没有要删除的列");
    return;
  }
  mutate(() => {
    const rem = new Set(indices);
    columns = columns.filter((_, i) => !rem.has(i));
    adjustDividersOnRemove(indices);
  });
  showHint(`已删除 ${indices.length} 列`);
}

// 插入列后，其后（>= at）的分隔线整体右移一位
function adjustDividersOnInsert(at) {
  const nd = new Set();
  dividers.forEach((d) => nd.add(d >= at ? d + 1 : d));
  dividers = nd;
}

// 删除若干列后，重算分隔线位置（被删列之后的分隔线一并删除，其余左移）
function adjustDividersOnRemove(removedIndices) {
  const rem = new Set(removedIndices);
  const nd = new Set();
  dividers.forEach((d) => {
    if (rem.has(d)) return; // 第 d 列被删，其后的分隔线删除
    let before = 0;
    rem.forEach((r) => { if (r < d) before++; });
    nd.add(d - before);
  });
  dividers = nd;
}

// 切换某列右侧的分隔线
function toggleDivider(colIndex) {
  const nd = new Set(dividers);
  if (nd.has(colIndex)) nd.delete(colIndex);
  else nd.add(colIndex);
  mutate(() => {
    dividers = nd;
  });
  showHint(dividers.has(colIndex)
    ? `已在 t${colIndex} 右侧添加分隔线`
    : `已删除 t${colIndex} 右侧的分隔线`);
}

function showHint(msg) {
  const el = $("selectionInfo");
  if (el) el.textContent = msg;
}

// ============================================================
// 分隔线 / 右键菜单 / 颜色自适应
// ============================================================
let contextMenu = null;

function ensureContextMenu() {
  if (!contextMenu) {
    contextMenu = document.createElement("div");
    contextMenu.className = "context-menu";
    document.body.appendChild(contextMenu);
  }
  return contextMenu;
}

function hideContextMenu() {
  if (contextMenu) contextMenu.hidden = true;
}

function showColumnContextMenu(e, colIndex) {
  e.preventDefault();
  const menu = ensureContextMenu();
  menu.innerHTML = "";
  menu.hidden = false;

  const hasDivider = dividers.has(colIndex);
  const dividerItem = document.createElement("div");
  dividerItem.className = "context-item";
  dividerItem.textContent = hasDivider ? "删除右边的分隔线" : "在右边添加分隔线";
  dividerItem.addEventListener("click", () => { toggleDivider(colIndex); hideContextMenu(); });
  menu.appendChild(dividerItem);

  const insertItem = document.createElement("div");
  insertItem.className = "context-item";
  insertItem.textContent = "在右边插入空列";
  insertItem.addEventListener("click", () => { insertColumnAfter(colIndex); hideContextMenu(); });
  menu.appendChild(insertItem);

  const delItem = document.createElement("div");
  delItem.className = "context-item danger";
  delItem.textContent = "删除这一列";
  delItem.addEventListener("click", () => { removeColumnAt(colIndex); hideContextMenu(); });
  menu.appendChild(delItem);

  const mw = menu.offsetWidth || 170;
  const mh = menu.offsetHeight || 120;
  menu.style.left = Math.max(4, Math.min(e.clientX, window.innerWidth - mw - 8)) + "px";
  menu.style.top = Math.max(4, Math.min(e.clientY, window.innerHeight - mh - 8)) + "px";
}

// 根据电路区背景明暗决定分隔线颜色：深色背景→白线，浅色背景→黑线
function applyDividerColor() {
  const el = document.querySelector(".circuit-area") || document.body;
  const bg = getComputedStyle(el).backgroundColor;
  const m = bg.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  let lum = 0.5;
  if (m) {
    lum = (0.299 * (+m[1]) + 0.587 * (+m[2]) + 0.114 * (+m[3])) / 255;
  }
  const color = lum < 0.5 ? "rgba(255,255,255,0.6)" : "rgba(0,0,0,0.45)";
  document.documentElement.style.setProperty("--divider-color", color);
}

function clearCircuit() {
  mutate(() => {
    columns = Array.from({ length: DEFAULT_COLS }, () => []);
    dividers = new Set();
    pending = null;
    selectedGate = null;
    refreshPaletteSelection();
  });
  updateSelectionInfo();
}

function applyPreset(name) {
  mutate(() => {
    let q = qubits;
    let cols = [];
    if (name === "bell") {
      q = 2;
      cols = [[{ type: "H", targets: [0] }], [{ type: "CNOT", targets: [0, 1] }]];
    } else if (name === "ghz") {
      q = 3;
      cols = [
        [{ type: "H", targets: [0] }],
        [{ type: "CNOT", targets: [0, 1] }],
        [{ type: "CNOT", targets: [1, 2] }],
      ];
    } else if (name === "superposition") {
      cols = [[...Array(qubits).keys()].map((w) => ({ type: "H", targets: [w] }))];
    } else if (name === "bitflip") {
      cols = [[{ type: "X", targets: [0] }]];
    }
    qubits = q;
    inputs = Array.from({ length: q }, () => "0");
    columns = padColumns(cols);
    dividers = new Set();
    pending = null;
  });
  updateSelectionInfo();
}

function padColumns(cols) {
  const out = cols.slice();
  while (out.length < DEFAULT_COLS) out.push([]);
  return out;
}

// ============================================================
// 运行模拟
// ============================================================
async function runCircuit() {
  const steps = [];
  columns.forEach((c, idx) => {
    if (c.length > 0) {
      steps.push({
        col: idx,
        gates: c.map((g) => {
          const o = { type: g.type, targets: g.targets };
          if (g.angle != null) o.angle = g.angle;
          return o;
        }),
      });
    }
  });

  const shots = parseInt($("shotsInput").value, 10) || 0;
  const body = { qubits, shots, inputs: inputs.slice(0, qubits), steps };
  const status = $("runStatus");
  const btn = $("runBtn");
  status.textContent = "运行中…";
  btn.disabled = true;

  try {
    const r = await fetch("/api/circuit/run", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    const data = await r.json();
    if (!r.ok) throw new Error(data.message || "HTTP " + r.status);
    renderResults(data);
    status.textContent = "完成 ✓";
  } catch (e) {
    renderError(e.message);
    status.textContent = "失败";
  } finally {
    renderValidation();
  }
}

// ============================================================
// 结果渲染
// ============================================================
function renderResults(data) {
  const box = $("results");
  box.innerHTML = "";
  const n = data.qubits;

  // 0) 输入态（定义）
  box.appendChild(card("输入态 |ψ_in⟩", renderInputSummary(data, n)));

  // 1) 输出态概率直方图
  box.appendChild(card("输出态概率分布 P(|ψ_out⟩)", renderProbBars(data.probabilities, n)));

  // 2) 测量采样直方图
  if (data.shots > 0 && data.counts) {
    box.appendChild(card("测量采样直方图（" + data.shots + " shots）", renderCountBars(data.counts, n)));
  }

  // 3) 输出态态矢量振幅表
  box.appendChild(card("输出态态矢量振幅（复系数）", renderStateVector(data.stateVector, n)));

  // 4) 各比特 |1⟩ 概率
  box.appendChild(card("各量子比特处于 |1⟩ 的概率", renderQubitProbs(data.qubitProbabilities)));
}

function renderInputSummary(data, n) {
  const wrap = document.createElement("div");
  const line = document.createElement("div");
  line.className = "input-summary";
  (data.inputs || []).forEach((st, i) => {
    const chip = document.createElement("span");
    chip.className = "input-chip";
    chip.innerHTML = `<b>q${i}</b> = ${ketOf(st)}`;
    line.appendChild(chip);
  });
  wrap.appendChild(line);
  if (data.inputProbabilities) {
    wrap.appendChild(renderProbBars(data.inputProbabilities, n));
  }
  return wrap;
}

function card(title, contentEl) {
  const c = document.createElement("div");
  c.className = "result-card";
  const h = document.createElement("h3");
  h.textContent = title;
  c.appendChild(h);
  c.appendChild(contentEl);
  return c;
}

function renderProbBars(probs, n) {
  const wrap = document.createElement("div");
  if (!probs || probs.length === 0) {
    wrap.innerHTML = '<div class="empty-hint">无数据</div>';
    return wrap;
  }
  probs.forEach((p, i) => {
    wrap.appendChild(barRow(toBits(i, n), p, ""));
  });
  return wrap;
}

function renderCountBars(counts, n) {
  const wrap = document.createElement("div");
  const entries = Object.entries(counts || {}).sort((a, b) => a[0].localeCompare(b[0]));
  if (entries.length === 0) {
    wrap.innerHTML = '<div class="empty-hint">无采样数据</div>';
    return wrap;
  }
  const total = entries.reduce((s, [, v]) => s + v, 0) || 1;
  entries.forEach(([bits, count]) => {
    wrap.appendChild(barRow(bits, count / total, count));
  });
  return wrap;
}

function barRow(bits, ratio, suffix) {
  const row = document.createElement("div");
  row.className = "bar-row";

  const label = document.createElement("span");
  label.className = "state-label";
  label.textContent = "|" + bits + "⟩";
  row.appendChild(label);

  const track = document.createElement("div");
  track.className = "bar-track";
  const fill = document.createElement("div");
  fill.className = "bar-fill";
  fill.style.width = Math.max(0, Math.min(100, ratio * 100)).toFixed(2) + "%";
  track.appendChild(fill);
  row.appendChild(track);

  const val = document.createElement("span");
  val.className = "bar-value";
  val.textContent = suffix !== "" ? suffix + "  (" + (ratio * 100).toFixed(1) + "%)" : (ratio * 100).toFixed(2) + "%";
  row.appendChild(val);
  return row;
}

function renderStateVector(sv, n) {
  const wrap = document.createElement("div");
  if (!sv || sv.length === 0) {
    wrap.innerHTML = '<div class="empty-hint">无数据</div>';
    return wrap;
  }
  const table = document.createElement("table");
  table.className = "statevector-table";
  table.innerHTML =
    "<thead><tr><th>基态 |x⟩</th><th>Re</th><th>Im</th><th>|振幅|²</th></tr></thead>";
  const tbody = document.createElement("tbody");
  sv.forEach((a, i) => {
    const p = a.re * a.re + a.im * a.im;
    const tr = document.createElement("tr");
    tr.innerHTML =
      `<td class="state">|${toBits(i, n)}⟩</td>` +
      `<td class="amp">${fmt(a.re)}</td>` +
      `<td class="amp">${fmt(a.im)}</td>` +
      `<td class="amp">${fmt(p)}</td>`;
    tbody.appendChild(tr);
  });
  table.appendChild(tbody);
  wrap.appendChild(table);
  return wrap;
}

function renderQubitProbs(probs) {
  const wrap = document.createElement("div");
  wrap.className = "qubit-prob-list";
  if (!probs) return wrap;
  probs.forEach((p, i) => {
    const item = document.createElement("div");
    item.className = "qubit-prob";
    const lbl = document.createElement("span");
    lbl.textContent = "q" + i;
    const track = document.createElement("div");
    track.className = "prob-track";
    const fill = document.createElement("div");
    fill.className = "prob-fill";
    fill.style.width = (p * 100).toFixed(1) + "%";
    track.appendChild(fill);
    const val = document.createElement("span");
    val.textContent = (p * 100).toFixed(1) + "%";
    item.appendChild(lbl);
    item.appendChild(track);
    item.appendChild(val);
    wrap.appendChild(item);
  });
  return wrap;
}

function renderError(msg) {
  const box = $("results");
  box.innerHTML = "";
  const c = document.createElement("div");
  c.className = "result-card full";
  c.innerHTML = `<h3>运行失败</h3><div class="error-box">${escapeHtml(msg)}</div>`;
  box.appendChild(c);
}

// ============================================================
// 元信息 / 上游提醒
// ============================================================
async function loadMeta() {
  try {
    const r = await fetch("/api/meta");
    const m = await r.json();
    const badge = $("modeBadge");
    badge.textContent = "mode: " + m.mode;
    badge.className = "badge badge-mode " + (m.mode === "cloud" ? "cloud" : "local");
    $("versionBadge").textContent = "v" + m.version;
  } catch (e) {
    $("modeBadge").textContent = "mode: ?";
  }
}

async function loadUpstream() {
  const banner = $("upstreamBanner");
  try {
    const r = await fetch("/api/upstream");
    const u = await r.json();
    banner.hidden = false;
    if (u.hasUpdate === true) {
      banner.className = "banner warn";
      banner.innerHTML =
        `🔔 <strong>开发者提醒：上游开源仓库 Strange 有新变更</strong><br>` +
        `锁定 commit <code>${short(u.pinnedCommit)}</code>（版本 ${u.pinnedVersion}）→ 最新 commit ` +
        `<code>${short(u.latestCommit)}</code>${u.latestVersion ? "（版本 " + u.latestVersion + "）" : ""}。<br>` +
        `请运行 <code>python scripts/check_upstream.py</code>，并按 README「手动更新上游代码」一节同步更新。`;
    } else if (u.hasUpdate === false) {
      banner.className = "banner ok";
      banner.innerHTML =
        `✅ 上游 Strange 已是最新（commit <code>${short(u.pinnedCommit)}</code>，版本 ${u.pinnedVersion}）。`;
    } else {
      banner.className = "banner info";
      banner.innerHTML =
        `ℹ️ 无法连接网络检查上游更新（离线/受限网络）。锁定版本：commit <code>${short(u.pinnedCommit)}</code>，` +
        `版本 ${u.pinnedVersion}。可运行 <code>python scripts/check_upstream.py</code> 手动检查。`;
    }
  } catch (e) {
    banner.hidden = true;
  }
}

// ============================================================
// 工具函数
// ============================================================
function toBits(value, n) {
  let s = "";
  for (let i = n - 1; i >= 0; i--) s += ((value >> i) & 1) ? "1" : "0";
  return s;
}

function fmt(x) {
  if (x == null) return "—";
  const v = Math.abs(x) < 1e-9 ? 0 : Math.round(x * 1e6) / 1e6;
  return String(v);
}

function short(hash) {
  return hash ? String(hash).slice(0, 7) : "?";
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

// ============================================================
// 校验 / 错误提醒
// ============================================================
function ketOf(v) {
  const st = INPUT_STATES.find((x) => x.value === v);
  return st ? st.label : "|0⟩";
}

function firstMeasurementColumn(wire) {
  for (let c = 0; c < columns.length; c++) {
    const g = findGateAt(c, wire);
    if (g && g.type === "M") {
      return c;
    }
  }
  return -1;
}

function validate() {
  const warnings = [];

  // 致命：测量后重新产生叠加态（与后端 Strange 的 measuresafe 规则一致）
  for (let w = 0; w < qubits; w++) {
    const fm = firstMeasurementColumn(w);
    if (fm < 0) continue;
    for (let c = fm + 1; c < columns.length; c++) {
      const g = findGateAt(c, w);
      if (!g) continue;
      const isH = g.type === "H";
      const isCnotTarget = g.type === "CNOT" && g.targets[1] === w;
      if (isH || isCnotTarget) {
        warnings.push({
          level: "fatal",
          text:
            `q${w} 在 t${fm} 被测量后，又在 t${c} 施加了 ${isH ? "H" : "CNOT（作为目标）"}，` +
            `会重新产生叠加态。量子测量不可逆，模拟器会拒绝执行，请先调整线路。`,
        });
      }
    }
  }

  // 提示：测量不可逆
  const hasMeasurement = columns.some((col) => col.some((g) => g.type === "M"));
  if (hasMeasurement) {
    warnings.push({
      level: "info",
      text: "线路包含测量门 M：测量不可逆；本模拟器不会在测量处坍缩态矢量，测量仅用于采样统计。",
    });
  }

  return warnings;
}

function renderValidation() {
  const panel = $("validationPanel");
  if (!panel) return;
  const warnings = validate();
  panel.innerHTML = "";
  if (warnings.length === 0) {
    panel.hidden = true;
  } else {
    panel.hidden = false;
    warnings.forEach((w) => {
      const div = document.createElement("div");
      div.className = "validation-item " + w.level;
      div.textContent = (w.level === "fatal" ? "✖ " : "ℹ ") + w.text;
      panel.appendChild(div);
    });
  }
  const runBtn = $("runBtn");
  if (runBtn) {
    runBtn.disabled = warnings.some((w) => w.level === "fatal");
  }
}

// ============================================================
// 事件绑定
// ============================================================
function bindEvents() {
  $("incQubit").addEventListener("click", () => setQubits(qubits + 1));
  $("decQubit").addEventListener("click", () => setQubits(qubits - 1));
  $("incCol").addEventListener("click", () => setCols(columns.length + 1));
  $("decCol").addEventListener("click", () => setCols(columns.length - 1));
  $("clearBtn").addEventListener("click", clearCircuit);
  $("undoBtn").addEventListener("click", undo);
  $("runBtn").addEventListener("click", runCircuit);

  // 数量输入框：输入目标数量后（回车/失焦）生效
  const qubitInput = $("qubitCount");
  const colInput = $("colCount");
  if (qubitInput) qubitInput.addEventListener("change", () => setQubits(parseInt(qubitInput.value, 10)));
  if (colInput) colInput.addEventListener("change", () => setCols(parseInt(colInput.value, 10)));

  // 批量加列
  const batchCols = $("batchCols");
  if ($("applyAddCols") && batchCols) {
    $("applyAddCols").addEventListener("click", () =>
      addColsBatch(parseInt(batchCols.value, 10))
    );
  }

  // 批量删列
  if ($("applyDelCols")) {
    $("applyDelCols").addEventListener("click", () =>
      deleteColsBatch($("delMode").value, $("delInput").value)
    );
  }

  // 右键菜单：点击其它地方关闭
  document.addEventListener("click", hideContextMenu);

  // 虚拟滚动
  const scrollEl = $("circuitScroll");
  if (scrollEl) {
    scrollEl.addEventListener("scroll", onScroll, { passive: true });
  }

  document.querySelectorAll("[data-preset]").forEach((b) =>
    b.addEventListener("click", () => applyPreset(b.dataset.preset))
  );
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      hideContextMenu();
      selectedGate = null;
      pending = null;
      refreshPaletteSelection();
      updateSelectionInfo();
      renderCircuit();
    }
  });
}

document.addEventListener("DOMContentLoaded", init);
