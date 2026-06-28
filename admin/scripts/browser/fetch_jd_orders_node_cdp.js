const fs = require('fs');

const SHOP_ID = Number(process.env.ZR_SYSTEM_SHOP_ID || '0');
const PHONE = process.env.ZR_SHOP_PHONE || '';
const CONTROL_SHOP_ID = process.env.ZR_SHOP_ID || '';
const SHOP_NAME = process.env.ZR_SHOP_NAME || 'shop';
const OUTPUT_DIR = process.env.OUTPUT_DIR || '/tmp/order-output';
const CONTROL_URL = process.env.ZR_CONTROL_URL || 'http://127.0.0.1:14501';
const ORDER_URL = 'https://store.jddj.com/plus/order/all';
const ORDER_LIST_API_PATTERN = /sff\.jddj\.com\/api.*api=dsm\.o2o\.order\.cater\.pcAllOrderListQuery/;
const MAX_ORDER_PAGES = Number(process.env.ZR_JD_MAX_ORDER_PAGES || '20');
const VERIFICATION_PATTERN = /验证一下|快速验证|购物无忧|安全验证|遇到问题点我反馈/;

const SENSITIVE_KEY_PATTERN = /cookie|token|authorization|auth|storage|profile|debugPort|headers?|nonce|device|pin|customerUrl|appPhoneIcon|appPhone|deliverManPhone|uri/i;

function text(value) {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

function firstNonBlank(...values) {
  for (const value of values) {
    const normalized = text(value);
    if (normalized) return normalized;
  }
  return null;
}

function money(value) {
  const normalized = text(value);
  if (!normalized) return null;
  const compact = normalized.replace(/,/g, '');
  const match = compact.match(/(\-)?[^\d]*(\d+(?:\.\d+)?)/);
  if (!match) return null;
  const sign = compact.includes('-') ? -1 : 1;
  return sign * Number(match[2]);
}

function countFromText(value) {
  const match = text(value).match(/(\d+)\s*种商品[,，]\s*共\s*(\d+)\s*件/);
  return match ? Number(match[2]) : null;
}

function parseMinute(value) {
  const normalized = text(value);
  const match = normalized.match(/(\d{2})-(\d{2})\s+(\d{2}):(\d{2})/);
  if (!match) return null;
  const year = new Date().getFullYear();
  return `${year}-${match[1]}-${match[2]}T${match[3]}:${match[4]}:00`;
}

function extractOrderedAt(order) {
  const descList = order?.overviewVo?.orderDescList || [];
  for (const item of descList) {
    const match = text(item).match(/(\d{2}-\d{2}\s+\d{2}:\d{2})\s*下单/);
    if (match) return parseMinute(match[1]);
  }
  const deliveryList = order?.deliveryVo?.deliverList || [];
  for (const item of deliveryList) {
    if (text(item?.desc).includes('商家已接单')) return parseMinute(item?.time);
  }
  return null;
}

function completedAt(order, status) {
  if (!['用户已收餐', '已完成'].includes(text(status))) return null;
  const deliveryList = order?.deliveryVo?.deliverList || [];
  for (const item of deliveryList) {
    if (/送达|收餐|完成/.test(text(item?.desc))) {
      return parseMinute(item?.time);
    }
  }
  return parseMinute(order?.basicVo?.expectTimeVo?.expectTimeFmt);
}

function cancelledAt(order, status) {
  if (!/取消/.test(text(status))) return null;
  const deliveryList = order?.deliveryVo?.deliverList || [];
  for (const item of deliveryList) {
    if (/取消/.test(text(item?.desc))) return parseMinute(item?.time);
  }
  return null;
}

function productItems(order) {
  return (order?.productVo?.skuVoList || []).map((sku) => ({
    name: text(sku?.name) || null,
    price: money(sku?.price?.text),
    quantity: money(sku?.count?.text),
    total: money(sku?.totalCount?.text),
  })).filter((item) => item.name);
}

function sanitize(value, depth = 0) {
  if (depth > 8) return '[depth]';
  if (Array.isArray(value)) return value.map((item) => sanitize(item, depth + 1));
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value)
      .filter(([key]) => !SENSITIVE_KEY_PATTERN.test(key))
      .map(([key, item]) => [key, sanitize(item, depth + 1)]));
  }
  return value;
}

function orderRawPayload(order) {
  return sanitize({
    basicVo: order?.basicVo,
    deliveryVo: order?.deliveryVo,
    feeVo: order?.feeVo,
    mealVo: order?.mealVo,
    productVo: order?.productVo,
    statisticsVo: order?.statisticsVo,
    overviewVo: order?.overviewVo,
  });
}

function parseJdOrder(order, targetStationNo = CONTROL_SHOP_ID) {
  const basic = order?.basicVo || {};
  const stationNo = text(basic.stationNo);
  if (targetStationNo && stationNo && stationNo !== String(targetStationNo)) return null;
  const orderId = firstNonBlank(basic.orderId, basic.jdOrderId);
  if (!orderId) return null;
  const status = firstNonBlank(basic.orderStatusText, order?.deliveryVo?.title);
  const expectedAt = parseMinute(basic.expectTimeVo?.expectTimeFmt);
  const orderedAt = extractOrderedAt(order);
  const items = productItems(order);
  const itemSummary = firstNonBlank(order?.statisticsVo?.productStatistics, order?.mealVo?.title);
  const customerTail = text(order?.userVo?.lastDigit).match(/(\d{4})/)?.[1] || null;
  const address = firstNonBlank(order?.userVo?.appAddress);
  const rawText = [
    `#${text(basic.orderNo)}`,
    [text(basic.expectTimeVo?.expectTimeFmt), text(basic.expectTimeVo?.expectSuffix)].filter(Boolean).join(''),
    status,
    order?.statisticsVo?.productStatistics,
    order?.statisticsVo?.amountStatistics,
    ...(order?.overviewVo?.orderDescList || []),
    `订单编号：${orderId}`,
  ].filter(Boolean).join('|');
  return Object.fromEntries(Object.entries({
    platform_order_id: orderId,
    platform_order_no: firstNonBlank(basic.jdOrderId, orderId),
    order_sequence: firstNonBlank(basic.orderNo),
    order_time_text: [text(basic.expectTimeVo?.expectTimeFmt), text(basic.expectTimeVo?.expectSuffix)].filter(Boolean).join('') || null,
    ordered_at: orderedAt,
    expected_delivery_at: expectedAt,
    completed_at: completedAt(order, status),
    cancelled_at: cancelledAt(order, status),
    fetched_at: new Date().toISOString().slice(0, 19),
    status,
    status_text: status,
    order_type: firstNonBlank(order?.deliveryVo?.deliverTag),
    tags_json: order?.basicVo?.orderStatus ? [{ code: order.basicVo.orderStatus }] : null,
    estimated_income: money(order?.feeVo?.estimatedAmount || order?.statisticsVo?.amount),
    merchant_income: money(order?.feeVo?.estimatedAmount || order?.statisticsVo?.amount),
    customer_name: firstNonBlank(order?.userVo?.userName),
    customer_phone_tail: customerTail,
    address,
    recipient_address: address,
    delivery_type: firstNonBlank(order?.deliveryVo?.deliverTag),
    rider_name: firstNonBlank(order?.deliveryVo?.deliverManName),
    remark: firstNonBlank(order?.statisticsVo?.userRemark),
    item_summary: itemSummary,
    item_count: countFromText(itemSummary),
    items_json: items,
    raw_text: rawText.slice(0, 1200),
    raw_payload: orderRawPayload(order),
  }).filter(([, value]) => value !== null && value !== undefined && value !== ''));
}

function extractOrderList(payload) {
  return payload?.result?.orderPage?.resultList || [];
}

function extractOrderPageMeta(payload) {
  const orderPage = payload?.result?.orderPage || {};
  return {
    totalCount: Number(orderPage.totalCount || payload?.result?.total || 0) || null,
    totalPage: Number(orderPage.totalPage || 0) || null,
    pageSize: Number(orderPage.pageSize || 0) || null,
  };
}

function classifyPage(textValue, orderCount) {
  const body = text(textValue);
  if (/账号登录|验证码登录|请输入用户名|请输入密码|忘记密码/.test(body)) {
    return 'login-expired';
  }
  if (VERIFICATION_PATTERN.test(body)) {
    return 'verification-required';
  }
  if (orderCount === 0 && /暂无|无订单|共0单|共 0 单/.test(body)) {
    return 'empty';
  }
  return orderCount > 0 ? 'ok' : 'unknown';
}

async function waitForCapturedPage(capturedPages, targetPageNo, previousResponseCount, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (targetPageNo && capturedPages.has(Number(targetPageNo))) return true;
    if (!targetPageNo && capturedPages.size > previousResponseCount) return true;
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  return false;
}

async function clickPaginationPage(page, targetPageNo) {
  const target = await page.evaluate((pageNo) => {
    const app = document.querySelector('#app.store-plus-app') || document.querySelector('.store-plus-app');
    if (app) app.scrollTop = app.scrollHeight;
    const targetText = String(pageNo);
    const pageItems = Array.from(document.querySelectorAll('.dj-pagination-num'));
    const pageItem = pageItems.find((el) => (el.innerText || '').trim() === targetText);
    const targetInfo = (el, mode) => {
      const rect = el.getBoundingClientRect();
      return {
        ok: true,
        mode,
        targetPageNo: pageNo,
        x: rect.x + rect.width / 2,
        y: rect.y + rect.height / 2,
      };
    };
    if (pageItem) {
      return targetInfo(pageItem, 'number');
    }
    const rightArrow = Array.from(document.querySelectorAll('.dj-pagination-left'))
      .find((el) => el.querySelector('.arrow-right'));
    if (rightArrow) {
      return targetInfo(rightArrow, 'next');
    }
    return { ok: false, mode: 'missing-pagination', targetPageNo: pageNo };
  }, targetPageNo);
  if (!target.ok) return target;
  await page.mouse.move(target.x, target.y, { steps: 8 });
  await page.waitForTimeout(150 + Math.floor(Math.random() * 250));
  await page.mouse.click(target.x, target.y);
  return { ok: true, mode: target.mode, targetPageNo };
}

async function maybeResolveVerification(page) {
  let bodyText = await page.locator('body').innerText().catch(() => '');
  if (!VERIFICATION_PATTERN.test(bodyText)) return { required: false, resolved: true };
  const labels = ['快速验证', '验证一下'];
  for (const label of labels) {
    const locator = page.getByText(label, { exact: false }).last();
    if (await locator.count().catch(() => 0)) {
      await locator.click({ timeout: 5000 }).catch(() => null);
      await page.waitForTimeout(5000);
      bodyText = await page.locator('body').innerText().catch(() => '');
      if (!VERIFICATION_PATTERN.test(bodyText)) {
        return { required: true, resolved: true, method: `text:${label}` };
      }
    }
  }
  return { required: true, resolved: false };
}

async function collectOrders(page) {
  const rawOrders = [];
  let pageMeta = null;
  const capturedPages = new Set();
  const clickResults = [];
  const missingPages = [];
  page.on('response', async (resp) => {
    if (!ORDER_LIST_API_PATTERN.test(resp.url())) return;
    try {
      const payload = JSON.parse(await resp.text());
      if (!payload?.result?.orderPage || !Object.prototype.hasOwnProperty.call(payload.result.orderPage, 'resultList')) return;
      const list = extractOrderList(payload);
      if (!Array.isArray(list)) return;
      const reqBody = JSON.parse(resp.request().postData() || '{}');
      const pageNo = Number(reqBody.pageNo || capturedPages.size + 1);
      rawOrders.push(...list);
      capturedPages.add(pageNo);
      pageMeta = extractOrderPageMeta(payload);
    } catch {
      // Ignore unrelated API responses.
    }
  });
  await page.goto(ORDER_URL, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => null);
  for (let i = 0; i < 12 && capturedPages.size === 0; i++) {
    await page.waitForTimeout(1000);
  }

  const totalPage = Math.min(
    MAX_ORDER_PAGES,
    Number(pageMeta?.totalPage || 0) || Math.ceil(Number(pageMeta?.totalCount || 0) / Number(pageMeta?.pageSize || 10)) || 1,
  );
  for (let pageNo = 2; pageNo <= totalPage; pageNo++) {
    if (capturedPages.has(pageNo)) continue;
    await page.waitForTimeout(1200 + Math.floor(Math.random() * 800));
    const beforeCount = capturedPages.size;
    const clickResult = await clickPaginationPage(page, pageNo).catch((err) => ({ ok: false, mode: 'click-error', error: text(err?.message) }));
    clickResults.push(clickResult);
    if (!clickResult.ok) break;
    let captured = await waitForCapturedPage(capturedPages, pageNo, beforeCount);
    if (!captured) {
      const verification = await maybeResolveVerification(page);
      if (verification.required && verification.resolved) {
        const retryClick = await clickPaginationPage(page, pageNo).catch((err) => ({ ok: false, mode: 'retry-click-error', error: text(err?.message) }));
        clickResults.push({ ...retryClick, retry: true, verification });
        captured = retryClick.ok && await waitForCapturedPage(capturedPages, pageNo, beforeCount);
      }
      if (!captured) {
        missingPages.push({ pageNo, verification });
        break;
      }
    }
  }
  const bodyText = (await page.locator('body').innerText().catch(() => '')).slice(0, 5000);
  const orders = rawOrders.map((order) => parseJdOrder(order)).filter(Boolean);
  const seen = new Set();
  const deduped = [];
  for (const order of orders) {
    if (seen.has(order.platform_order_id)) continue;
    seen.add(order.platform_order_id);
    deduped.push(order);
  }
  return {
    bodyText,
    rawCount: rawOrders.length,
    pageMeta,
    capturedPages: Array.from(capturedPages).sort((a, b) => a - b),
    clickResults,
    expectedPages: totalPage,
    missingPages,
    complete: totalPage <= 1 || capturedPages.size >= totalPage,
    orders: deduped,
    status: classifyPage(bodyText, deduped.length),
  };
}

async function main() {
  const { chromium } = require('playwright-core');
  if (!SHOP_ID || !PHONE || !CONTROL_SHOP_ID) {
    throw new Error('ZR_SYSTEM_SHOP_ID, ZR_SHOP_PHONE, and ZR_SHOP_ID are required');
  }
  const openUrl = `${CONTROL_URL.replace(/\/+$/, '')}/open?phone=${encodeURIComponent(PHONE)}&shopId=${encodeURIComponent(CONTROL_SHOP_ID)}&url=${encodeURIComponent(ORDER_URL)}&width=1706&height=900&renderWidth=1706&renderHeight=900&renderScale=1&scale=1&mode=remote-backend`;
  const openResp = await fetch(openUrl);
  const openJson = await openResp.json();
  if (!openJson.ok) throw new Error(`open failed: ${JSON.stringify(openJson)}`);

  const browser = await chromium.connectOverCDP(`http://127.0.0.1:${openJson.debugPort}`);
  const context = browser.contexts()[0] || await browser.newContext();
  const page = context.pages()[0] || await context.newPage();
  const collection = await collectOrders(page);

  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  const ts = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
  const snapshot = {
    shop_name: SHOP_NAME,
    system_shop_id: SHOP_ID,
    control_shop_id: CONTROL_SHOP_ID,
    platform: 'jdms',
    generated_at: new Date().toISOString(),
    page_url: page.url(),
    page_title: await page.title(),
    raw_count: collection.rawCount,
    count: collection.orders.length,
    status: collection.status,
    page_meta: collection.pageMeta,
    captured_pages: collection.capturedPages,
    expected_pages: collection.expectedPages,
    missing_pages: collection.missingPages,
    complete: collection.complete,
    page_clicks: collection.clickResults,
    text_sample: collection.bodyText.replace(/\s+/g, ' ').slice(0, 1200),
    orders: collection.orders,
  };
  fs.writeFileSync(`${OUTPUT_DIR}/jd_orders_${SHOP_ID}_${ts}.json`, JSON.stringify(snapshot, null, 2));
  fs.writeFileSync(`${OUTPUT_DIR}/jd_orders_${SHOP_ID}.json`, JSON.stringify(snapshot, null, 2));

  const payload = { shopId: SHOP_ID, source: 'fetch_jd_orders_node_cdp', ingestBatchId: `jdms-${SHOP_ID}-${ts}`, orders: collection.orders };
  const payloadPath = `${OUTPUT_DIR}/jd_ingest_payload.json`;
  fs.writeFileSync(payloadPath, JSON.stringify(payload, null, 2));
  if (!collection.complete) {
    throw new Error(`JD order collection incomplete: captured pages ${collection.capturedPages.join(',') || 'none'} of ${collection.expectedPages}`);
  }
  console.log(JSON.stringify({ outputDir: OUTPUT_DIR, platform: 'jdms', labels: collection.rawCount, orders: collection.orders.length, status: collection.status, payload: payloadPath }));
  await browser.close();
}

if (require.main === module) {
  main().catch((err) => {
    console.error(err.stack || err);
    process.exit(1);
  });
}

module.exports = {
  parseJdOrder,
  extractOrderList,
  extractOrderPageMeta,
  classifyPage,
  money,
};
