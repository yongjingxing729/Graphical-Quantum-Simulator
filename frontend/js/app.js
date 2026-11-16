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
const MAX_COLS = 24;
const DEFAULT_COLS = 8;

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

// ---- DOM 引用 ----
const $ = (id) => document.getElementById(id);

function init() {
  buildPalette();
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
  const grid = $("circuitGrid");
  grid.innerHTML = "";

  // 左侧比特标签 + 输入态选择
  const labels = document.createElement("div");
  labels.className = "wire-labels";
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
  grid.appendChild(labels);

  for (let c = 0; c < columns.length; c++) {
    const colEl = document.createElement("div");
    colEl.className = "circuit-column";

    const head = document.createElement("div");
    head.className = "col-head";
    head.textContent = "t" + c;
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
    grid.appendChild(colEl);
  }

  $("qubitCount").textContent = qubits;
  $("colCount").textContent = columns.length;
  renderValidation();
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
    // 未选择门：点击已有门则删除
    if (findGateAt(col, wire)) {
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
    // 重复点击同一比特：忽略
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
  return { qubits, columns: JSON.parse(JSON.stringify(columns)), inputs: inputs.slice() };
}

function restore(s) {
  qubits = s.qubits;
  columns = s.columns;
  inputs = s.inputs.slice();
  pending = null;
}

function mutate(fn) {
  undoStack.push(snapshot());
  if (undoStack.length > 50) undoStack.shift();
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
  const next = Math.max(MIN_COLS, Math.min(MAX_COLS, n));
  mutate(() => {
    if (next > columns.length) {
      while (columns.length < next) columns.push([]);
    } else {
      columns = columns.slice(0, next);
    }
  });
}

function clearCircuit() {
  mutate(() => {
    columns = Array.from({ length: DEFAULT_COLS }, () => []);
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
  const steps = columns
    .filter((c) => c.length > 0)
    .map((c) => ({
      gates: c.map((g) => {
        const o = { type: g.type, targets: g.targets };
        if (g.angle != null) o.angle = g.angle;
        return o;
      }),
    }));

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
  document.querySelectorAll("[data-preset]").forEach((b) =>
    b.addEventListener("click", () => applyPreset(b.dataset.preset))
  );
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      selectedGate = null;
      pending = null;
      refreshPaletteSelection();
      updateSelectionInfo();
      renderCircuit();
    }
  });
}

document.addEventListener("DOMContentLoaded", init);
