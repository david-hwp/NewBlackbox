#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const Module = require("module");

const DEBUG_PORT = Number(process.env.ZR_DEBUG_PORT || process.argv[2] || 14502);
const VIEWPORT_WIDTH = Number(process.env.ZR_WINDOW_WIDTH || 360);
const VIEWPORT_HEIGHT = Number(process.env.ZR_WINDOW_HEIGHT || 520);
const TRACE_FILE = process.env.ZR_TRACE_FILE || "";
const URL_HINT = process.env.ZR_AUTH_URL || "";
const TIMEOUT_MS = Number(process.env.ZR_ALIGN_TIMEOUT_MS || 10000);
const POLL_MS = 500;
const MAX_PANEL_WIDTH_RATIO = Number(process.env.ZR_ALIGN_MAX_PANEL_WIDTH_RATIO || 0.72);
const MAX_PANEL_HEIGHT_RATIO = Number(process.env.ZR_ALIGN_MAX_PANEL_HEIGHT_RATIO || 0.66);

function trace(event, payload = {}) {
  if (!TRACE_FILE) {
    return;
  }
  const entry = {
    ts: new Date().toISOString(),
    event,
    url: URL_HINT,
    ...payload,
  };
  fs.mkdirSync(path.dirname(TRACE_FILE), { recursive: true });
  fs.appendFileSync(TRACE_FILE, `${JSON.stringify(entry)}\n`, "utf8");
}

function loadChromium() {
  const extraProject = process.env.ZR_NODE_PROJECT || "/opt/phase19-xpra/browser";
  const requireFromExtra = Module.createRequire(path.join(extraProject, "package.json"));
  try {
    return require("playwright-chromium").chromium;
  } catch (error) {
    return requireFromExtra("playwright-chromium").chromium;
  }
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} for ${url}`);
  }
  return response.json();
}

async function sleep(ms) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

function clipText(value, limit = 160) {
  return String(value || "").replace(/\s+/g, " ").trim().slice(0, limit);
}

async function connectBrowser() {
  const started = Date.now();
  let lastError = null;
  while (Date.now() - started < TIMEOUT_MS) {
    try {
      const version = await fetchJson(`http://127.0.0.1:${DEBUG_PORT}/json/version`);
      const wsUrl = version.webSocketDebuggerUrl;
      if (!wsUrl) {
        throw new Error("missing browser websocket url");
      }
      const chromium = loadChromium();
      return chromium.connectOverCDP(wsUrl);
    } catch (error) {
      lastError = error;
      await sleep(POLL_MS);
    }
  }
  throw new Error(`connect_timeout: ${lastError ? lastError.message : "unknown"}`);
}

async function selectPage(browser) {
  const started = Date.now();
  let bestPage = null;
  while (Date.now() - started < TIMEOUT_MS) {
    const pages = browser.contexts().flatMap((context) => context.pages());
    bestPage = pages.find((page) => {
      const url = page.url();
      return url && !url.startsWith("about:") && !url.startsWith("devtools:");
    }) || pages[0] || null;
    if (bestPage) {
      const state = await bestPage.evaluate(() => document.readyState).catch(() => "");
      if (state === "interactive" || state === "complete") {
        return bestPage;
      }
    }
    await sleep(POLL_MS);
  }
  if (!bestPage) {
    throw new Error("page_not_found");
  }
  return bestPage;
}

function alignmentScript(args) {
  const viewport = args.viewport;
  const keywords = {
    login: ["登录", "登陆", "login", "submit"],
    account: ["账号", "账户", "用户", "用户名", "手机号", "手机", "phone", "mobile", "account", "user", "username", "loginname"],
    password: ["密码", "password", "pwd"],
    captcha: ["验证码", "校验码", "获取验证码", "短信", "captcha", "code", "sms"],
  };

  function normalize(value) {
    return String(value || "").toLowerCase().replace(/\s+/g, " ").trim();
  }

  function clipLocalText(value, limit = 160) {
    return String(value || "").replace(/\s+/g, " ").trim().slice(0, limit);
  }

  function includesAny(text, words) {
    const normalized = normalize(text);
    return words.some((word) => normalized.includes(normalize(word)));
  }

  function elementLabel(el) {
    return [
      el.innerText,
      el.textContent,
      el.getAttribute("placeholder"),
      el.getAttribute("aria-label"),
      el.getAttribute("name"),
      el.id,
      el.className,
      el.type,
      el.getAttribute("role"),
    ].filter(Boolean).join(" ");
  }

  function visibleRect(el) {
    const rect = el.getBoundingClientRect();
    const style = getComputedStyle(el);
    const visible = rect.width > 1 && rect.height > 1 &&
      style.display !== "none" &&
      style.visibility !== "hidden" &&
      Number(style.opacity || 1) > 0.05;
    if (!visible) {
      return null;
    }
    return {
      x: rect.x,
      y: rect.y,
      w: rect.width,
      h: rect.height,
      left: rect.left,
      top: rect.top,
      right: rect.right,
      bottom: rect.bottom,
    };
  }

  function scoreCandidate(el) {
    const tag = el.tagName;
    const type = normalize(el.getAttribute("type"));
    const label = elementLabel(el);
    let score = 0;
    let role = "other";
    if (tag === "INPUT" || tag === "TEXTAREA") {
      if (["password"].includes(type)) {
        score += 40;
        role = "password";
      } else if (["tel", "text", "number", "email", ""].includes(type)) {
        score += 30;
        role = "account";
      } else {
        score += 8;
      }
      if (includesAny(label, keywords.account)) {
        score += 22;
        if (role === "other") role = "account";
      }
      if (includesAny(label, keywords.password)) {
        score += 24;
        role = "password";
      }
      if (includesAny(label, keywords.captcha)) {
        score += 18;
        if (role === "other" || role === "account") role = "captcha";
      }
    } else if (tag === "BUTTON" || el.getAttribute("role") === "button" || tag === "A") {
      if (includesAny(label, keywords.login)) {
        score += 35;
        role = "submit";
      }
      if (includesAny(label, keywords.captcha)) {
        score += 18;
        role = "captchaButton";
      }
      if (normalize(el.className).includes("login")) {
        score += 12;
      }
      if (type === "submit") {
        score += 14;
        role = "submit";
      }
    } else {
      if (includesAny(label, keywords.login)) score += 8;
      if (includesAny(label, keywords.account)) score += 6;
      if (includesAny(label, keywords.password)) score += 6;
      if (normalize(el.className).includes("login")) score += 8;
    }
    return { score, role, label };
  }

  function toCandidate(el) {
    const rect = visibleRect(el);
    if (!rect) {
      return null;
    }
    const scored = scoreCandidate(el);
    if (scored.score <= 0) {
      return null;
    }
    return {
      el,
      tag: el.tagName,
      type: el.getAttribute("type") || "",
      role: scored.role,
      score: scored.score,
      label: scored.label.replace(/\s+/g, " ").trim().slice(0, 160),
      rect,
    };
  }

  function unionRect(items) {
    return items.reduce((box, item) => ({
      left: Math.min(box.left, item.rect.left),
      top: Math.min(box.top, item.rect.top),
      right: Math.max(box.right, item.rect.right),
      bottom: Math.max(box.bottom, item.rect.bottom),
    }), { left: Infinity, top: Infinity, right: -Infinity, bottom: -Infinity });
  }

  function expandBox(box, marginX, marginY) {
    const documentWidth = Math.max(document.documentElement.scrollWidth, document.body.scrollWidth, viewport.width);
    const documentHeight = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight, viewport.height);
    const left = Math.max(0, box.left + window.scrollX - marginX);
    const top = Math.max(0, box.top + window.scrollY - marginY);
    const right = Math.min(documentWidth, box.right + window.scrollX + marginX);
    const bottom = Math.min(documentHeight, box.bottom + window.scrollY + marginY);
    return { left, top, right, bottom, width: right - left, height: bottom - top };
  }

  function currentViewportContains(box) {
    return box.left >= 0 && box.top >= 0 && box.right <= viewport.width && box.bottom <= viewport.height;
  }

  function viewportBox(items) {
    return items.reduce((box, item) => ({
      left: Math.min(box.left, item.rect.left),
      top: Math.min(box.top, item.rect.top),
      right: Math.max(box.right, item.rect.right),
      bottom: Math.max(box.bottom, item.rect.bottom),
    }), { left: Infinity, top: Infinity, right: -Infinity, bottom: -Infinity });
  }

  function boxFromViewportRect(rect) {
    return {
      left: rect.left + window.scrollX,
      top: rect.top + window.scrollY,
      right: rect.right + window.scrollX,
      bottom: rect.bottom + window.scrollY,
      width: rect.right - rect.left,
      height: rect.bottom - rect.top,
    };
  }

  function boxFromViewportBox(box) {
    return {
      left: box.left + window.scrollX,
      top: box.top + window.scrollY,
      right: box.right + window.scrollX,
      bottom: box.bottom + window.scrollY,
      width: box.right - box.left,
      height: box.bottom - box.top,
    };
  }

  function unionBoxes(boxes) {
    return boxes.reduce((box, item) => ({
      left: Math.min(box.left, item.left),
      top: Math.min(box.top, item.top),
      right: Math.max(box.right, item.right),
      bottom: Math.max(box.bottom, item.bottom),
      width: Math.max(box.right, item.right) - Math.min(box.left, item.left),
      height: Math.max(box.bottom, item.bottom) - Math.min(box.top, item.top),
    }), { left: Infinity, top: Infinity, right: -Infinity, bottom: -Infinity, width: 0, height: 0 });
  }

  function containsAllSelected(el, items) {
    return items.every((item) => el === item.el || el.contains(item.el));
  }

  function elementText(el) {
    return [
      el.innerText,
      el.textContent,
      el.getAttribute("aria-label"),
      el.id,
      el.className,
    ].filter(Boolean).join(" ");
  }

  function panelScore(el, rect, selectedBox) {
    const text = elementText(el);
    const classText = normalize(`${el.id || ""} ${el.className || ""}`);
    const area = rect.w * rect.h;
    const selectedArea = Math.max(1, (selectedBox.right - selectedBox.left) * (selectedBox.bottom - selectedBox.top));
    const areaRatio = area / selectedArea;
    let score = 0;

    if (el.tagName === "FORM") score += 18;
    if (classText.includes("login")) score += 30;
    if (classText.includes("account") || classText.includes("password")) score += 10;
    if (includesAny(text, ["账号登录", "验证码登录", "账号密码", "手机号", "密码登录"])) score += 46;
    if (includesAny(text, ["忘记密码", "隐私", "协议", "注册账号", "免费入驻", "开店"])) score += 12;
    if (includesAny(text, keywords.login)) score += 10;
    if (includesAny(text, keywords.account) && includesAny(text, keywords.password)) score += 18;

    if (areaRatio >= 1.3 && areaRatio <= 8) {
      score += 28 - Math.abs(Math.log(areaRatio / 3)) * 8;
    } else if (areaRatio < 1.3) {
      score -= 24;
    } else {
      score -= Math.min(80, (areaRatio - 8) * 8);
    }

    if (rect.w > viewport.width * 2.1) score -= 36;
    if (rect.h > viewport.height * 1.35) score -= 28;
    if (el === document.body || el === document.documentElement) score -= 120;
    return score;
  }

  function findLoginPanel(selectedItems, selectedBox) {
    const ancestors = [];
    const seen = new Set();
    selectedItems.forEach((item) => {
      let node = item.el;
      while (node && node.nodeType === 1) {
        if (!seen.has(node)) {
          seen.add(node);
          ancestors.push(node);
        }
        node = node.parentElement;
      }
    });

    const panels = ancestors
      .filter((el) => containsAllSelected(el, selectedItems))
      .map((el) => {
        const rect = visibleRect(el);
        if (!rect) return null;
        return {
          el,
          rect,
          score: panelScore(el, rect, selectedBox),
          label: clipLocalText(elementText(el), 140),
        };
      })
      .filter(Boolean)
      .sort((a, b) => b.score - a.score);

    return panels[0] || null;
  }

  function relevantContentBox(panel, selectedItems, selectedBox) {
    if (!panel) {
      return boxFromViewportBox(selectedBox);
    }

    const relevantBoxes = [];
    const selectedElements = new Set(selectedItems.map((item) => item.el));
    const contentSelector = [
      "input",
      "textarea",
      "button",
      "a",
      "label",
      "[role=button]",
      "[class*=login]",
      "[class*=Login]",
      "[class*=tab]",
      "[class*=Tab]",
      "[class*=password]",
      "[class*=Password]",
      "[class*=account]",
      "[class*=Account]",
      "[class*=code]",
      "[class*=Code]",
      "span",
      "div",
    ].join(",");

    const elements = [panel.el, ...Array.from(panel.el.querySelectorAll(contentSelector)).slice(0, 1200)];
    elements.forEach((el) => {
      const rect = visibleRect(el);
      if (!rect) return;
      if (
        rect.right < panel.rect.left - 2 ||
        rect.left > panel.rect.right + 2 ||
        rect.bottom < panel.rect.top - 2 ||
        rect.top > panel.rect.bottom + 2
      ) {
        return;
      }

      const label = elementLabel(el);
      const classText = normalize(`${el.id || ""} ${el.className || ""}`);
      const tag = el.tagName;
      const isPrimaryControl =
        selectedElements.has(el) ||
        tag === "INPUT" ||
        tag === "TEXTAREA" ||
        tag === "BUTTON" ||
        el.getAttribute("role") === "button";
      const isLoginText =
        includesAny(label, keywords.login) ||
        includesAny(label, keywords.account) ||
        includesAny(label, keywords.password) ||
        includesAny(label, keywords.captcha) ||
        includesAny(label, ["账号登录", "验证码登录", "账号密码", "手机号", "忘记密码", "隐私", "协议", "注册账号", "免费入驻", "开店"]);
      const isLoginClass =
        classText.includes("login") ||
        classText.includes("account") ||
        classText.includes("password") ||
        classText.includes("captcha") ||
        classText.includes("code") ||
        classText.includes("tab");

      if (!isPrimaryControl && !isLoginText && !isLoginClass) {
        return;
      }
      if (rect.w > panel.rect.w * 1.02 && rect.h > panel.rect.h * 0.92 && !isPrimaryControl) {
        return;
      }
      relevantBoxes.push(boxFromViewportRect(rect));
    });

    if (relevantBoxes.length === 0) {
      return boxFromViewportRect(panel.rect);
    }
    return unionBoxes(relevantBoxes);
  }

  function expandDocumentBox(box, marginX, marginY) {
    const documentWidth = Math.max(document.documentElement.scrollWidth, document.body.scrollWidth, viewport.width);
    const documentHeight = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight, viewport.height);
    const left = Math.max(0, box.left - marginX);
    const top = Math.max(0, box.top - marginY);
    const right = Math.min(documentWidth, box.right + marginX);
    const bottom = Math.min(documentHeight, box.bottom + marginY);
    return { left, top, right, bottom, width: right - left, height: bottom - top };
  }

  function ensureWrapper() {
    let wrapper = document.getElementById("zr-login-align-wrapper");
    if (!wrapper) {
      wrapper = document.createElement("div");
      wrapper.id = "zr-login-align-wrapper";
      while (document.body.firstChild) {
        wrapper.appendChild(document.body.firstChild);
      }
      document.body.appendChild(wrapper);
    }
    const documentWidth = Math.max(document.documentElement.scrollWidth, document.body.scrollWidth, viewport.width);
    const documentHeight = Math.max(document.documentElement.scrollHeight, document.body.scrollHeight, viewport.height);
    document.documentElement.style.overflow = "hidden";
    document.body.style.overflow = "hidden";
    document.body.style.margin = "0";
    document.documentElement.style.width = `${documentWidth}px`;
    document.documentElement.style.minWidth = `${documentWidth}px`;
    document.body.style.width = `${documentWidth}px`;
    document.body.style.minWidth = `${documentWidth}px`;
    document.body.style.minHeight = `${documentHeight}px`;
    wrapper.style.position = "absolute";
    wrapper.style.left = "0";
    wrapper.style.top = "0";
    wrapper.style.width = `${documentWidth}px`;
    wrapper.style.minWidth = `${documentWidth}px`;
    wrapper.style.minHeight = `${documentHeight}px`;
    wrapper.style.transformOrigin = "0 0";
    wrapper.style.willChange = "transform";
    return wrapper;
  }

  function serializeItem(item, currentRect) {
    const rect = currentRect || item.rect;
    return {
      tag: item.tag,
      type: item.type,
      role: item.role,
      score: item.score,
      label: item.label,
      rect: {
        x: Math.round(rect.x),
        y: Math.round(rect.y),
        w: Math.round(rect.w || rect.width),
        h: Math.round(rect.h || rect.height),
      },
      visibleInViewport:
        rect.left >= -2 &&
        rect.top >= -2 &&
        rect.right <= viewport.width + 2 &&
        rect.bottom <= viewport.height + 2,
    };
  }

  function refreshItem(item) {
    const rect = visibleRect(item.el);
    if (!rect) return null;
    return { ...item, rect };
  }

  const selector = [
    "input",
    "textarea",
    "button",
    "a",
    "[role=button]",
    "form",
    "[class*=login]",
    "[class*=Login]",
    "[class*=password]",
    "[class*=Password]",
    "[class*=captcha]",
    "[class*=Captcha]",
  ].join(",");
  const candidates = Array.from(document.querySelectorAll(selector))
    .map(toCandidate)
    .filter(Boolean)
    .sort((a, b) => b.score - a.score || a.rect.top - b.rect.top);

  const account = candidates.find((item) => item.role === "account");
  const password = candidates.find((item) => item.role === "password");
  const captcha = candidates.find((item) => item.role === "captcha");
  const submit = candidates.find((item) => item.role === "submit" && item.rect.w >= 40 && item.rect.h >= 16);
  const captchaButton = candidates.find((item) => item.role === "captchaButton");

  const selected = [account, password || captcha, submit || captchaButton].filter(Boolean);
  if (selected.length < 2) {
    return {
      ok: false,
      reason: candidates.length ? "insufficient_form_elements" : "no_candidates",
      candidates: candidates.slice(0, 12).map((item) => ({
        tag: item.tag,
        type: item.type,
        role: item.role,
        score: item.score,
        label: item.label,
        rect: {
          x: Math.round(item.rect.x),
          y: Math.round(item.rect.y),
          w: Math.round(item.rect.w),
          h: Math.round(item.rect.h),
        },
      })),
      viewport,
      page: {
        url: location.href,
        title: document.title,
        scrollWidth: document.documentElement.scrollWidth,
        scrollHeight: document.documentElement.scrollHeight,
      },
    };
  }

  const wrapper = ensureWrapper();
  window.scrollTo(0, 0);
  wrapper.style.transform = "";

  const layoutSelected = selected.map(refreshItem).filter(Boolean);
  if (layoutSelected.length < selected.length) {
    return {
      ok: false,
      reason: "selected_elements_hidden_after_layout",
      selected: selected.map((item) => serializeItem(item)),
      viewport,
      page: {
        url: location.href,
        title: document.title,
        scrollWidth: document.documentElement.scrollWidth,
        scrollHeight: document.documentElement.scrollHeight,
      },
    };
  }

  const rawBox = unionRect(layoutSelected);
  const panel = findLoginPanel(layoutSelected, rawBox);
  const contentBox = relevantContentBox(panel, layoutSelected, rawBox);
  const target = expandDocumentBox(contentBox, 16, 18);
  const controlTarget = expandDocumentBox(boxFromViewportBox(rawBox), 32, 24);
  const alignedTarget = unionBoxes([target, controlTarget]);
  const fitMaxWidth = Math.max(1, viewport.width * args.maxPanelWidthRatio);
  const fitMaxHeight = Math.max(1, viewport.height * args.maxPanelHeightRatio);
  const scale = Math.min(
    1,
    fitMaxWidth / Math.max(1, alignedTarget.width),
    fitMaxHeight / Math.max(1, alignedTarget.height),
  );
  const fittedWidth = alignedTarget.width * scale;
  const fittedHeight = alignedTarget.height * scale;
  const fitMarginX = Math.max(12, Math.round((viewport.width - fittedWidth) / 2));
  const fitMarginY = Math.max(18, Math.round((viewport.height - fittedHeight) * 0.28));
  wrapper.style.transform = `translate(${fitMarginX}px, ${fitMarginY}px) scale(${scale}) translate(${-alignedTarget.left}px, ${-alignedTarget.top}px)`;
  const strategy = scale < 0.999 ? "panel-fit-transform" : "panel-transform";
  const offsetX = alignedTarget.left;
  const offsetY = alignedTarget.top;
  const finalVisibleBox = {
    x: fitMarginX,
    y: fitMarginY,
    w: alignedTarget.width * scale,
    h: alignedTarget.height * scale,
    left: fitMarginX,
    top: fitMarginY,
    right: fitMarginX + alignedTarget.width * scale,
    bottom: fitMarginY + alignedTarget.height * scale,
  };

  window.__zrLoginAligned = true;
  const finalSelected = layoutSelected.map((item) => serializeItem(item, item.el.getBoundingClientRect()));
  const visibleBoxVisible =
    finalVisibleBox.left >= -2 &&
    finalVisibleBox.top >= -2 &&
    finalVisibleBox.right <= viewport.width + 2 &&
    finalVisibleBox.bottom <= viewport.height + 2;
  const selectedControlsVisible = finalSelected.every((item) => (
    item.visibleInViewport ||
    (item.rect.x >= finalVisibleBox.x - 2 &&
      item.rect.y >= finalVisibleBox.y - 2 &&
      item.rect.x + item.rect.w <= finalVisibleBox.x + finalVisibleBox.w + 2 &&
      item.rect.y + item.rect.h <= finalVisibleBox.y + finalVisibleBox.h + 2)
  ));
  const allSelectedVisible = visibleBoxVisible && selectedControlsVisible;

  return {
    ok: allSelectedVisible,
    reason: allSelectedVisible ? "" : "selected_controls_not_visible",
    strategy,
    offsetX: Math.round(offsetX),
    offsetY: Math.round(offsetY),
    scale,
    rawBox: {
      x: Math.round(rawBox.left + window.scrollX),
      y: Math.round(rawBox.top + window.scrollY),
      w: Math.round(rawBox.right - rawBox.left),
      h: Math.round(rawBox.bottom - rawBox.top),
    },
    visibleBox: {
      leftTop: {
        x: Math.round(alignedTarget.left),
        y: Math.round(alignedTarget.top),
      },
      rightBottom: {
        x: Math.round(alignedTarget.right),
        y: Math.round(alignedTarget.bottom),
      },
      document: {
        x: Math.round(alignedTarget.left),
        y: Math.round(alignedTarget.top),
        w: Math.round(alignedTarget.width),
        h: Math.round(alignedTarget.height),
      },
      viewport: {
        x: Math.round(finalVisibleBox.x),
        y: Math.round(finalVisibleBox.y),
        w: Math.round(finalVisibleBox.w),
        h: Math.round(finalVisibleBox.h),
      },
      visibleInViewport: visibleBoxVisible,
      selectedControlsVisible,
      panel: panel ? {
        tag: panel.el.tagName,
        score: Math.round(panel.score),
        label: panel.label,
      } : null,
    },
    selected: finalSelected,
    allSelectedVisible,
    candidates: candidates.slice(0, 12).map((item) => serializeItem(item)),
    viewport,
    page: {
      url: location.href,
      title: document.title,
      scrollWidth: document.documentElement.scrollWidth,
      scrollHeight: document.documentElement.scrollHeight,
      scrollX: Math.round(window.scrollX),
      scrollY: Math.round(window.scrollY),
    },
  };
}

async function alignLoginForm(page) {
  const started = Date.now();
  let lastResult = null;
  while (Date.now() - started < TIMEOUT_MS) {
    lastResult = await page.evaluate(alignmentScript, {
      viewport: { width: VIEWPORT_WIDTH, height: VIEWPORT_HEIGHT },
      maxPanelWidthRatio: MAX_PANEL_WIDTH_RATIO,
      maxPanelHeightRatio: MAX_PANEL_HEIGHT_RATIO,
    }).catch((error) => ({ ok: false, reason: "script_error", error: error.message }));
    trace("login_align_probe", {
      ok: !!lastResult.ok,
      reason: lastResult.reason || "",
      strategy: lastResult.strategy || "",
      selected: (lastResult.selected || []).map((item) => ({ role: item.role, rect: item.rect, label: clipText(item.label, 80) })),
    });
    if (lastResult.ok) {
      return lastResult;
    }
    await sleep(POLL_MS);
  }
  return lastResult || { ok: false, reason: "timeout" };
}

(async () => {
  let browser = null;
  try {
    browser = await connectBrowser();
    const page = await selectPage(browser);
    await page.waitForLoadState("domcontentloaded", { timeout: TIMEOUT_MS }).catch(() => {});
    await page.waitForTimeout(1200);
    const result = await alignLoginForm(page);
    if (result.ok) {
      trace("login_align_applied", {
        strategy: result.strategy,
        offsetX: result.offsetX,
        offsetY: result.offsetY,
        selected: result.selected,
        page: result.page,
      });
      console.log(JSON.stringify({ ok: true, alignment: result }, null, 2));
      process.exit(0);
    }
    trace("login_align_failed", result);
    console.log(JSON.stringify({ ok: false, alignment: result }, null, 2));
    process.exit(0);
  } catch (error) {
    const payload = { ok: false, alignment: { reason: "aligner_error", error: error.stack || String(error) } };
    trace("login_align_failed", payload.alignment);
    console.log(JSON.stringify(payload, null, 2));
    process.exit(0);
  } finally {
    // Do not close the CDP browser/context: it is the live window streamed to the app.
  }
})();
