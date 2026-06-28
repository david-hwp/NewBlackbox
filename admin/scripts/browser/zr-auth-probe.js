#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const Module = require("module");

const DEBUG_PORT = Number(process.env.ZR_DEBUG_PORT || process.argv[2] || 14502);
const AUTH_URL = process.env.ZR_AUTH_URL || "";
const PROFILE_DIR = process.env.ZR_PROFILE_DIR || "";
const TRACE_FILE = process.env.ZR_TRACE_FILE || "";
const TIMEOUT_MS = Number(process.env.ZR_PROBE_TIMEOUT_MS || 15000);
const POLL_MS = 500;

function trace(event, payload = {}) {
  if (!TRACE_FILE) return;
  fs.mkdirSync(path.dirname(TRACE_FILE), { recursive: true });
  fs.appendFileSync(
    TRACE_FILE,
    `${JSON.stringify({ ts: new Date().toISOString(), event, url: AUTH_URL, ...payload })}\n`,
    "utf8",
  );
}

function loadChromium() {
  const extraProject = process.env.ZR_NODE_PROJECT || path.join(process.env.HOME || "", "data/browser");
  const requireFromExtra = Module.createRequire(path.join(extraProject, "package.json"));
  try {
    return require("playwright-chromium").chromium;
  } catch (error) {
    return requireFromExtra("playwright-chromium").chromium;
  }
}

async function sleep(ms) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`HTTP ${response.status} for ${url}`);
  return response.json();
}

async function connectBrowser() {
  const started = Date.now();
  let lastError = null;
  while (Date.now() - started < TIMEOUT_MS) {
    try {
      const version = await fetchJson(`http://127.0.0.1:${DEBUG_PORT}/json/version`);
      if (!version.webSocketDebuggerUrl) throw new Error("missing browser websocket url");
      return loadChromium().connectOverCDP(version.webSocketDebuggerUrl);
    } catch (error) {
      lastError = error;
      await sleep(POLL_MS);
    }
  }
  throw new Error(`connect_timeout: ${lastError ? lastError.message : "unknown"}`);
}

async function selectPage(browser) {
  const pages = browser.contexts().flatMap((context) => context.pages());
  return pages.find((page) => {
    const url = page.url();
    return url && !url.startsWith("about:") && !url.startsWith("devtools:");
  }) || pages[0] || null;
}

async function preparePlatformProbePage(page) {
  const platform = classifyPlatform(AUTH_URL || page.url());
  if (!["jdms", "tbwm"].includes(platform) || !AUTH_URL) {
    return;
  }
  await page.goto(AUTH_URL, { waitUntil: "domcontentloaded", timeout: TIMEOUT_MS }).catch(() => {});
  await page.waitForTimeout(2000);
}

function classifyCookie(cookie) {
  return {
    domain: cookie.domain,
    nameHash: hashName(cookie.name),
    expires: cookie.expires || 0,
    httpOnly: !!cookie.httpOnly,
    secure: !!cookie.secure,
    sameSite: cookie.sameSite || "",
  };
}

function hashName(value) {
  let hash = 0;
  for (const char of String(value || "")) {
    hash = ((hash << 5) - hash + char.charCodeAt(0)) | 0;
  }
  return `k${Math.abs(hash).toString(16)}`;
}

function domainHost(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch (error) {
    return "";
  }
}

function classifyPlatform(url) {
  const host = domainHost(url);
  if (host.endsWith("store.jddj.com")) return "jdms";
  if (host.endsWith("melody.shop.ele.me")) return "tbwm";
  if (host.endsWith("waimaie.meituan.com")) return "mtwm";
  return "";
}

function cookieMatchesHost(cookie, host) {
  if (!host) return true;
  const domain = String(cookie.domain || "").replace(/^\./, "");
  return host === domain || host.endsWith(`.${domain}`) || domain.endsWith(`.${host}`);
}

async function collectStorage(page) {
  return page.evaluate(() => {
    function keys(storage) {
      const result = [];
      for (let i = 0; i < storage.length; i += 1) {
        const key = storage.key(i);
        if (key) result.push(key);
      }
      return result;
    }
    const tokenPattern = /(token|ticket|sid|session|auth|login|user|shop|merchant|poi|seller|passport)/i;
    const localKeys = keys(window.localStorage || {});
    const sessionKeys = keys(window.sessionStorage || {});
    return {
      localStorageCount: localKeys.length,
      sessionStorageCount: sessionKeys.length,
      localStorageSignalKeys: localKeys.filter((key) => tokenPattern.test(key)).slice(0, 20),
      sessionStorageSignalKeys: sessionKeys.filter((key) => tokenPattern.test(key)).slice(0, 20),
      indexedDbAvailable: !!window.indexedDB,
      cacheStorageAvailable: !!window.caches,
    };
  }).catch((error) => ({ error: error.message }));
}

async function collectPageSignals(page) {
  return page.evaluate(() => {
    const bodyText = (document.body ? document.body.innerText : "").replace(/\s+/g, " ").slice(0, 4000);
    const loginPattern = /(账号登录|验证码登录|请输入.*密码|请输入.*手机号|获取验证码|登录)/;
    const consolePattern = /(商家中心|店铺|订单|商品|营业|账户|退出|工作台|管理|采购|评价)/;
    const logoutPattern = /(退出登录|退出|注销)/;
    const inputCount = Array.from(document.querySelectorAll("input,textarea"))
      .filter((el) => {
        const rect = el.getBoundingClientRect();
        const style = getComputedStyle(el);
        return rect.width > 1 && rect.height > 1 && style.display !== "none" && style.visibility !== "hidden";
      }).length;
    return {
      title: document.title,
      url: location.href,
      hasLoginText: loginPattern.test(bodyText),
      hasConsoleText: consolePattern.test(bodyText),
      hasLogoutText: logoutPattern.test(bodyText),
      visibleInputCount: inputCount,
      textSample: bodyText.slice(0, 240),
    };
  }).catch((error) => ({ error: error.message }));
}

function matchAny(text, patterns) {
  return patterns.some((pattern) => pattern.test(text));
}

function platformPageSignals(signals) {
  const page = signals.page || {};
  const text = `${page.title || ""} ${page.url || ""} ${page.textSample || ""}`;
  const platform = classifyPlatform(signals.url || page.url || "");
  if (platform === "jdms") {
    const hasConsoleText = matchAny(text, [
      /商家首页/,
      /订单管理/,
      /商品管理/,
      /全部门店/,
      /今日有效订单/,
      /经营罗盘/,
    ]);
    const hasLoginText = matchAny(text, [
      /账号登录/,
      /验证码登录/,
      /请输入用户名/,
      /请输入密码/,
      /忘记密码/,
    ]);
    return { platform, hasConsoleText, hasLoginText };
  }
  if (platform === "tbwm") {
    const hasLoginText = matchAny(text, [
      /账号登录/,
      /验证码登录/,
      /短信登录/,
      /请输入.*手机号/,
      /请输入.*密码/,
      /获取验证码/,
      /扫码登录/,
      /淘宝账号/,
      /支付宝登录/,
    ]);
    const hasConsoleText = !hasLoginText && matchAny(text, [
      /饿了么商家中心/,
      /商家中心/,
      /工作台/,
      /订单管理/,
      /订单/,
      /商品管理/,
      /商品/,
      /门店管理/,
      /门店/,
      /经营数据/,
      /营业/,
      /评价管理/,
      /配送/,
    ]);
    return { platform, hasConsoleText, hasLoginText };
  }
  return { platform, hasConsoleText: false, hasLoginText: false };
}

function decide(signals) {
  let positive = 0;
  let negative = 0;
  const platformSignals = platformPageSignals(signals);
  if (signals.page && signals.page.hasConsoleText) positive += 1;
  if (signals.page && signals.page.hasLogoutText) positive += 1;
  if (platformSignals.hasConsoleText) positive += 2;
  if (signals.cookies && signals.cookies.matchingCount >= 2) positive += 1;
  if (signals.storage && (
    signals.storage.localStorageSignalKeys?.length > 0 ||
    signals.storage.sessionStorageSignalKeys?.length > 0 ||
    signals.storage.localStorageCount > 0
  )) positive += 1;
  if (platformSignals.hasLoginText) negative += 2;
  else if (!platformSignals.hasConsoleText && signals.page && signals.page.hasLoginText && signals.page.visibleInputCount >= 1) negative += 2;

  if (platformSignals.hasLoginText && !platformSignals.hasConsoleText) {
    return { status: "UNAUTHORIZED", confidence: "HIGH" };
  }
  if (platformSignals.hasConsoleText && positive >= 2 && negative <= 1) {
    return { status: "AUTHORIZED", confidence: positive >= 3 ? "HIGH" : "MEDIUM" };
  }
  if (positive >= 2 && negative === 0) {
    return { status: "AUTHORIZED", confidence: positive >= 3 ? "HIGH" : "MEDIUM" };
  }
  if (negative >= 2 && positive === 0) {
    return { status: "UNAUTHORIZED", confidence: "HIGH" };
  }
  if (positive > 0) {
    return { status: "UNKNOWN", confidence: "LOW" };
  }
  return { status: "UNKNOWN", confidence: "LOW" };
}

async function main() {
  let browser = null;
  try {
    browser = await connectBrowser();
    const context = browser.contexts()[0];
    const page = await selectPage(browser);
    if (!context || !page) throw new Error("page_not_found");
    await page.waitForLoadState("domcontentloaded", { timeout: TIMEOUT_MS }).catch(() => {});
    await page.waitForTimeout(1000);
    await preparePlatformProbePage(page);

    const host = domainHost(AUTH_URL || page.url());
    const cookies = await context.cookies().catch(() => []);
    const matchingCookies = cookies.filter((cookie) => cookieMatchesHost(cookie, host));
    const signals = {
      profilePath: PROFILE_DIR,
      checkedAt: new Date().toISOString(),
      page: await collectPageSignals(page),
      storage: await collectStorage(page),
      cookies: {
        host,
        totalCount: cookies.length,
        matchingCount: matchingCookies.length,
        matching: matchingCookies.slice(0, 30).map(classifyCookie),
      },
    };
    const decision = decide(signals);
    const result = { ok: true, ...decision, signals };
    trace("authorization_probe_finished", {
      status: result.status,
      confidence: result.confidence,
      cookieCount: signals.cookies.matchingCount,
      page: signals.page,
    });
    console.log(JSON.stringify(result, null, 2));
    process.exit(0);
  } catch (error) {
    const result = {
      ok: false,
      status: "FAILED",
      confidence: "LOW",
      error: error.stack || String(error),
      signals: { profilePath: PROFILE_DIR, checkedAt: new Date().toISOString() },
    };
    trace("authorization_probe_failed", result);
    console.log(JSON.stringify(result, null, 2));
    process.exit(0);
  } finally {
    // Keep the remote browser alive for Xpra.
  }
}

if (require.main === module) {
  main();
}

module.exports = {
  decide,
  platformPageSignals,
};
