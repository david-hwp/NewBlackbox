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

  const rawBox = unionRect(selected);
  const target = expandBox(rawBox, 24, 32);
  const desiredLeft = Math.max(0, Math.min(
    target.left,
    Math.max(0, Math.max(document.documentElement.scrollWidth, document.body.scrollWidth) - viewport.width),
  ));
  const buttonBottom = submit ? submit.rect.bottom + window.scrollY : target.bottom;
  let desiredTop = Math.max(0, target.top);
  if (buttonBottom - desiredTop > viewport.height - 72) {
    desiredTop = Math.max(0, buttonBottom - (viewport.height - 72));
  }
  desiredTop = Math.min(
    desiredTop,
    Math.max(0, Math.max(document.documentElement.scrollHeight, document.body.scrollHeight) - viewport.height),
  );

  window.scrollTo(desiredLeft, desiredTop);

  const afterScrollSelected = selected.map((item) => {
    const rect = item.el.getBoundingClientRect();
    return {
      ...item,
      rect: {
        x: rect.x,
        y: rect.y,
        w: rect.width,
        h: rect.height,
        left: rect.left,
        top: rect.top,
        right: rect.right,
        bottom: rect.bottom,
      },
    };
  });
  const afterBox = viewportBox(afterScrollSelected);
  let strategy = "scroll";
  let offsetX = desiredLeft;
  let offsetY = desiredTop;

  if (!currentViewportContains(afterBox)) {
    const overflowRight = Math.max(0, afterBox.right - viewport.width);
    const overflowBottom = Math.max(0, afterBox.bottom - viewport.height);
    const translateX = Math.max(0, Math.min(afterBox.left, Math.max(afterBox.left, overflowRight)));
    const translateY = Math.max(0, Math.min(afterBox.top, Math.max(afterBox.top, overflowBottom)));
    let wrapper = document.getElementById("zr-login-align-wrapper");
    if (!wrapper) {
      wrapper = document.createElement("div");
      wrapper.id = "zr-login-align-wrapper";
      while (document.body.firstChild) {
        wrapper.appendChild(document.body.firstChild);
      }
      document.body.appendChild(wrapper);
      document.documentElement.style.overflow = "hidden";
      document.body.style.overflow = "hidden";
      document.body.style.margin = "0";
      document.body.style.width = `${viewport.width}px`;
      document.body.style.height = `${viewport.height}px`;
    }
    wrapper.style.transformOrigin = "0 0";
    wrapper.style.transform = `translate(${-translateX}px, ${-translateY}px)`;
    wrapper.style.willChange = "transform";
    strategy = "transform";
    offsetX = translateX;
    offsetY = translateY;
  }

  window.__zrLoginAligned = true;
  const finalSelected = selected.map((item) => serializeItem(item, item.el.getBoundingClientRect()));
  const allSelectedVisible = finalSelected.every((item) => item.visibleInViewport);

  return {
    ok: true,
    strategy,
    offsetX: Math.round(offsetX),
    offsetY: Math.round(offsetY),
    rawBox: {
      x: Math.round(rawBox.left + window.scrollX),
      y: Math.round(rawBox.top + window.scrollY),
      w: Math.round(rawBox.right - rawBox.left),
      h: Math.round(rawBox.bottom - rawBox.top),
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
