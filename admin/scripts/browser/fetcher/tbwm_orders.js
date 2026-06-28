const fs = require('fs');

const SHOP_ID = Number(process.env.ZR_SYSTEM_SHOP_ID || '0');
const PHONE = process.env.ZR_SHOP_PHONE || '';
const CONTROL_SHOP_ID = process.env.ZR_SHOP_ID || '';
const SHOP_NAME = process.env.ZR_SHOP_NAME || 'shop';
const OUTPUT_DIR = process.env.OUTPUT_DIR || '/tmp/order-output';
const CONTROL_URL = process.env.ZR_CONTROL_URL || 'http://127.0.0.1:14501';
const HOME_URL = process.env.ZR_TBWM_HOME_URL || 'https://melody.shop.ele.me/';
const ORDER_URLS = (process.env.ZR_TBWM_ORDER_URLS || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);
const MAX_RESPONSE_BYTES = Number(process.env.ZR_TBWM_MAX_RESPONSE_BYTES || '2500000');
const MAX_CAPTURED_ORDERS = Number(process.env.ZR_TBWM_MAX_CAPTURED_ORDERS || '300');
const SENSITIVE_KEY_PATTERN = /cookie|token|auth|authorization|storage|profile|debugPort|headers?|passport|session|csrf|secret|password|credential|riderPhone|courierPhone|deliverymanPhone|deliverManPhone/i;
const LOGIN_PATTERN = /账号登录|验证码登录|短信登录|请输入.*手机号|请输入.*密码|获取验证码|扫码登录|支付宝登录|淘宝账号登录/;
const EMPTY_PATTERN = /暂无.*订单|无订单|没有.*订单|共\s*0\s*单|0\s*个订单|暂无数据/;
const VERIFICATION_PATTERN = /验证一下|安全验证|滑块|验证码|风险校验|请完成验证/;

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

function lower(value) {
  return String(value || '').replace(/[_-]/g, '').toLowerCase();
}

function money(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value === 'object') {
    const cent = firstNonBlank(value.cent, value.cents, value.amountCent, value.priceCent, value.feeCent);
    if (cent !== null && /^-?\d+(\.\d+)?$/.test(cent)) return Number(cent) / 100;
    return money(firstNonBlank(value.amount, value.price, value.fee, value.total, value.totalAmount, value.value, value.text));
  }
  const normalized = text(value).replace(/,/g, '');
  const match = normalized.match(/-?\d+(?:\.\d+)?/);
  if (!match) return null;
  const sign = normalized.includes('-') ? -1 : 1;
  return sign * Math.abs(Number(match[0]));
}

function quantity(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  const match = text(value).match(/-?\d+(?:\.\d+)?/);
  return match ? Number(match[0]) : null;
}

function parseTime(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'number') {
    const millis = value > 100000000000 ? value : value * 1000;
    return formatLocalDateTime(new Date(millis));
  }
  const normalized = text(value);
  if (/^\d{13}$/.test(normalized)) return formatLocalDateTime(new Date(Number(normalized)));
  if (/^\d{10}$/.test(normalized)) return formatLocalDateTime(new Date(Number(normalized) * 1000));
  let match = normalized.match(/(\d{4})[-/](\d{1,2})[-/](\d{1,2})[ T](\d{1,2}):(\d{2})(?::(\d{2}))?/);
  if (match) {
    return `${match[1]}-${pad(match[2])}-${pad(match[3])}T${pad(match[4])}:${match[5]}:${pad(match[6] || '0')}`;
  }
  match = normalized.match(/(\d{1,2})[-/](\d{1,2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?/);
  if (match) {
    const year = new Date().getFullYear();
    return `${year}-${pad(match[1])}-${pad(match[2])}T${pad(match[3])}:${match[4]}:${pad(match[5] || '0')}`;
  }
  return null;
}

function pad(value) {
  return String(value || '0').padStart(2, '0');
}

function formatLocalDateTime(date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return null;
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function findDirect(object, keyNames) {
  if (!object || typeof object !== 'object' || Array.isArray(object)) return null;
  const normalizedKeys = new Set(keyNames.map(lower));
  for (const [key, value] of Object.entries(object)) {
    if (normalizedKeys.has(lower(key))) {
      const normalized = text(value);
      if (normalized) return value;
    }
  }
  return null;
}

function findDeep(object, keyNames, depth = 0, seen = new WeakSet()) {
  if (!object || typeof object !== 'object' || depth > 6) return null;
  if (seen.has(object)) return null;
  seen.add(object);
  const direct = findDirect(object, keyNames);
  if (direct !== null) return direct;
  if (Array.isArray(object)) {
    for (const item of object) {
      const found = findDeep(item, keyNames, depth + 1, seen);
      if (found !== null) return found;
    }
    return null;
  }
  for (const value of Object.values(object)) {
    const found = findDeep(value, keyNames, depth + 1, seen);
    if (found !== null) return found;
  }
  return null;
}

function objectText(value, maxLength = 3000) {
  try {
    return JSON.stringify(value).slice(0, maxLength);
  } catch {
    return '';
  }
}

function getPath(object, path) {
  if (!object || !path) return null;
  return String(path).split('.').reduce((current, key) => {
    if (!current || typeof current !== 'object') return null;
    return current[key];
  }, object);
}

function firstMoney(...values) {
  for (const value of values) {
    const parsed = money(value);
    if (parsed !== null) return parsed;
  }
  return null;
}

function looksLikeTbwmFulfillOrder(object) {
  if (!object || typeof object !== 'object' || Array.isArray(object)) return false;
  return Boolean(
    text(object.id)
    && text(object.shopId)
    && (object.header || object.userInfo || object.foodInfo || object.settlementInfo || object.deliveryInfo || object.printDataInfo)
  );
}

function hasOrderIdentity(object) {
  if (!object || typeof object !== 'object' || Array.isArray(object)) return false;
  if (looksLikeTbwmFulfillOrder(object)) return true;
  return Object.entries(object).some(([key, value]) => (
    /(order|wmorder|bizorder|trade).*(id|no|number)|^(orderId|orderNo|wmOrderId|bizOrderId|tradeId)$/i.test(key)
    && text(value)
  ));
}

function looksLikeOrder(object) {
  if (!object || typeof object !== 'object' || Array.isArray(object)) return false;
  if (looksLikeTbwmFulfillOrder(object)) return true;
  if (!hasOrderIdentity(object)) return false;
  const sample = objectText(object);
  return /(status|state|time|amount|price|fee|customer|buyer|receiver|address|goods|item|dish|订单|顾客|商品|地址)/i.test(sample);
}

function collectOrderObjects(payload, result = [], depth = 0, seen = new WeakSet()) {
  if (!payload || typeof payload !== 'object' || depth > 10 || result.length >= MAX_CAPTURED_ORDERS) return result;
  if (seen.has(payload)) return result;
  seen.add(payload);
  if (Array.isArray(payload)) {
    const orderItems = payload.filter(looksLikeOrder);
    if (orderItems.length > 0) {
      result.push(...orderItems.slice(0, MAX_CAPTURED_ORDERS - result.length));
      return result;
    }
    for (const item of payload) collectOrderObjects(item, result, depth + 1, seen);
    return result;
  }
  if (looksLikeOrder(payload)) {
    result.push(payload);
    return result;
  }
  const priorityEntries = Object.entries(payload).sort(([keyA], [keyB]) => scoreContainerKey(keyB) - scoreContainerKey(keyA));
  for (const [, value] of priorityEntries) {
    collectOrderObjects(value, result, depth + 1, seen);
    if (result.length >= MAX_CAPTURED_ORDERS) break;
  }
  return result;
}

function scoreContainerKey(key) {
  if (/order|trade|list|records|rows|result|data|page/i.test(key)) return 2;
  return 0;
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

function extractItems(order) {
  const list = findDeep(order, [
    'items', 'itemList', 'goodsList', 'goods', 'productList', 'products',
    'skuList', 'commodities', 'dishes', 'dishList', 'orderItems',
  ]);
  if (!Array.isArray(list)) return [];
  return list.map((item) => {
    const name = firstNonBlank(
      findDirect(item, ['name', 'title', 'goodsName', 'itemName', 'productName', 'dishName', 'skuName']),
    );
    if (!name) return null;
    return Object.fromEntries(Object.entries({
      name,
      price: money(findDirect(item, ['price', 'unitPrice', 'salePrice', 'goodsPrice'])),
      quantity: quantity(findDirect(item, ['quantity', 'count', 'num', 'amount', 'itemCount'])),
      total: money(findDirect(item, ['total', 'totalPrice', 'amount', 'subtotal'])),
    }).filter(([, value]) => value !== null && value !== undefined && value !== ''));
  }).filter(Boolean);
}

function countItems(items) {
  if (!items.length) return null;
  const total = items.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0);
  return total || items.length;
}

function statusCompleted(status) {
  return /完成|已送达|已完成|已收货|已收餐/.test(text(status));
}

function statusCancelled(status) {
  return /取消|关闭|作废/.test(text(status));
}

function statusRefunded(status) {
  return /退款|退单/.test(text(status));
}

function extractPhoneTail(value) {
  const phone = text(value);
  return phone.match(/(\d{4})(?!.*\d)/)?.[1] || null;
}

function parseTbwmOrder(order, targetShopId = CONTROL_SHOP_ID) {
  const isFulfillOrder = looksLikeTbwmFulfillOrder(order);
  const shopId = firstNonBlank(findDeep(order, [
    'shopId', 'shop_id', 'storeId', 'store_id', 'restaurantId', 'restaurant_id',
    'sellerId', 'seller_id', 'poiId', 'poi_id', 'wmPoiId', 'wm_poi_id',
  ]));
  if (targetShopId && shopId && shopId !== String(targetShopId)) return null;
  const orderId = firstNonBlank(
    isFulfillOrder ? order.id : null,
    findDeep(order, [
    'orderId', 'order_id', 'wmOrderId', 'wm_order_id', 'bizOrderId', 'biz_order_id',
    'tradeId', 'trade_id', 'platformOrderId', 'platform_order_id',
    ]),
  );
  if (!orderId) return null;
  const orderNo = firstNonBlank(
    isFulfillOrder ? getPath(order, 'header.daySn') : null,
    isFulfillOrder ? getPath(order, 'header.historyDaySn') : null,
    findDeep(order, [
    'orderNo', 'order_no', 'orderNumber', 'order_number', 'displayOrderNo',
    'sequence', 'orderSequence', 'serialNo', 'daySeq', 'daySn',
    ]),
  );
  const status = firstNonBlank(
    isFulfillOrder ? getPath(order, 'header.orderLatestStatus') : null,
    isFulfillOrder ? getPath(order, 'headerExtraInfo.statusDesc') : null,
    findDeep(order, [
    'statusText', 'status_text', 'orderStatusText', 'order_status_text',
    'statusDesc', 'status_desc', 'stateDesc', 'state_desc', 'statusName',
    'orderStatus', 'status', 'state',
    ]),
  );
  const orderedAt = parseTime(firstNonBlank(
    isFulfillOrder ? order.activeTime : null,
    findDeep(order, [
    'orderedAt', 'ordered_at', 'orderTime', 'order_time', 'createTime', 'createdAt',
    'gmtCreate', 'placeOrderTime', 'orderCreatedAt', 'bookTime', 'activeTime',
    ]),
  ));
  const expectedAt = parseTime(firstNonBlank(
    isFulfillOrder ? getPath(order, 'header.planDeliverTime') : null,
    isFulfillOrder ? getPath(order, 'header.historyPredictDeliveryTime') : null,
    findDeep(order, [
    'expectedDeliveryAt', 'expected_delivery_at', 'expectTime', 'expectedTime',
    'promiseTime', 'deliveryTime', 'estimatedDeliveryTime', 'estimateArriveTime',
    'planDeliverTime', 'historyPredictDeliveryTime',
    ]),
  ));
  const completedAt = parseTime(firstNonBlank(
    isFulfillOrder ? order.settledTime : null,
    findDeep(order, [
    'completedAt', 'completed_at', 'finishTime', 'completeTime', 'completedTime',
    'arriveTime', 'deliveredTime', 'settledTime',
    ]),
  ));
  const cancelledAt = parseTime(findDeep(order, ['cancelledAt', 'cancelled_at', 'cancelTime', 'cancelledTime']));
  const refundedAt = parseTime(findDeep(order, ['refundedAt', 'refunded_at', 'refundTime', 'refundedTime']));
  const customerName = firstNonBlank(findDeep(order, [
    'customerName', 'customer_name', 'consigneeName', 'userName', 'buyerName',
    'receiverName', 'recipientName',
  ]));
  const customerPhone = firstNonBlank(findDeep(order, [
    'privacyPhone', 'privacy_phone', 'virtualPhone', 'phone', 'customerPhone',
    'receiverPhone', 'recipientPhone', 'consigneePhone', 'consigneeSecretPhones',
    'ticketCustomerPhones',
  ]));
  const address = firstNonBlank(findDeep(order, [
    'address', 'recipientAddress', 'recipient_address', 'receiverAddress',
    'consigneeAddress', 'customerAddress', 'deliveryAddress',
  ]));
  const items = extractItems(order);
  const itemSummary = firstNonBlank(
    findDeep(order, ['itemSummary', 'item_summary', 'goodsSummary', 'productSummary', 'dishSummary']),
    items.map((item) => `${item.name}${item.quantity ? ` x${item.quantity}` : ''}`).join('，'),
  );
  const resolvedCompletedAt = completedAt || (statusCompleted(status) ? expectedAt : null);
  const resolvedCancelledAt = cancelledAt || (statusCancelled(status) ? parseTime(findDeep(order, ['updateTime', 'updatedAt'])) : null);
  const resolvedRefundedAt = refundedAt || (statusRefunded(status) ? parseTime(findDeep(order, ['updateTime', 'updatedAt'])) : null);
  const rawText = [
    orderNo ? `#${orderNo}` : null,
    status,
    orderedAt,
    expectedAt,
    customerName,
    address,
    itemSummary,
    `订单编号：${orderId}`,
  ].filter(Boolean).join('|');
  return Object.fromEntries(Object.entries({
    platform_order_id: orderId,
    platform_order_no: firstNonBlank(orderNo, orderId),
    order_sequence: orderNo,
    order_time_text: firstNonBlank(findDeep(order, ['orderTimeText', 'order_time_text']), orderedAt),
    ordered_at: orderedAt,
    expected_delivery_at: expectedAt,
    completed_at: resolvedCompletedAt,
    cancelled_at: resolvedCancelledAt,
    refunded_at: resolvedRefundedAt,
    fetched_at: new Date().toISOString().slice(0, 19),
    status,
    status_text: status,
    order_type: firstNonBlank(findDeep(order, ['orderType', 'order_type', 'bizType', 'type'])),
    tags_json: buildTags(order),
    estimated_income: firstMoney(getPath(order, 'settlementInfo.expectedIncomeInfo'), findDeep(order, ['estimatedIncome', 'estimated_income', 'merchantIncome', 'merchant_income', 'shopAmount', 'settleAmount', 'income'])),
    customer_paid_amount: firstMoney(getPath(order, 'settlementInfo.customerPaidInfo'), getPath(order, 'printDataInfo.payAmount'), findDeep(order, ['customerPaidAmount', 'customer_paid_amount', 'userPayAmount', 'payAmount', 'actualPayAmount', 'customerAmount', 'totalAmount'])),
    merchant_income: firstMoney(getPath(order, 'settlementInfo.expectedIncomeInfo'), findDeep(order, ['merchantIncome', 'merchant_income', 'shopAmount', 'settleAmount', 'income', 'estimatedIncome'])),
    original_amount: firstMoney(getPath(order, 'settlementInfo.orderTotalPrice'), getPath(order, 'settlementInfo.subTotalFeeInfo'), getPath(order, 'printDataInfo.ticketPrintContext.totalAmount'), findDeep(order, ['originalAmount', 'original_amount', 'originAmount', 'totalAmount', 'orderAmount'])),
    discount_amount: firstMoney(getPath(order, 'settlementInfo.merchantItemActivityInfo'), findDeep(order, ['discountAmount', 'discount_amount', 'discountFee', 'activityDiscount'])),
    delivery_fee: firstMoney(getPath(order, 'foodInfo.deliveryFee'), findDeep(order, ['deliveryFee', 'delivery_fee', 'shippingFee', 'freight'])),
    package_fee: firstMoney(getPath(order, 'foodInfo.packageFee'), findDeep(order, ['packageFee', 'package_fee', 'packingFee', 'boxFee'])),
    refund_amount: money(findDeep(order, ['refundAmount', 'refund_amount', 'refundFee'])),
    customer_name: customerName,
    customer_phone_tail: extractPhoneTail(customerPhone),
    privacy_phone: customerPhone,
    address,
    recipient_address: address,
    delivery_type: firstNonBlank(findDeep(order, ['deliveryType', 'delivery_type', 'logisticsType', 'shippingType', 'disDeliveryName'])),
    rider_name: firstNonBlank(findDeep(order, ['riderName', 'rider_name', 'courierName', 'deliverymanName'])),
    rider_phone: firstNonBlank(findDeep(order, ['riderPhone', 'rider_phone', 'courierPhone', 'deliverymanPhone'])),
    remark: firstNonBlank(findDeep(order, ['remark', 'note', 'buyerRemark', 'customerRemark'])),
    item_summary: itemSummary,
    item_count: countItems(items),
    items_json: items.length ? items : null,
    raw_text: rawText.slice(0, 1200),
    raw_payload: sanitize(order),
  }).filter(([, value]) => value !== null && value !== undefined && value !== ''));
}

function buildTags(order) {
  const tags = [];
  const statusCode = firstNonBlank(findDeep(order, ['statusCode', 'status_code', 'status', 'state']));
  if (statusCode) tags.push({ code: statusCode });
  const tagList = findDeep(order, ['tags', 'tagList', 'labels']);
  if (Array.isArray(tagList)) {
    tags.push(...tagList.map((tag) => (typeof tag === 'object' ? sanitize(tag) : { text: text(tag) })).filter((tag) => Object.keys(tag).length));
  }
  return tags.length ? tags : null;
}

function classifyPage(textValue, orderCount, capturedJsonCount = 0) {
  const body = text(textValue);
  if (LOGIN_PATTERN.test(body)) return 'login-expired';
  if (VERIFICATION_PATTERN.test(body)) return 'verification-required';
  if (orderCount > 0) return 'ok';
  if (EMPTY_PATTERN.test(body)) return 'empty';
  if (/订单|订单管理|商家中心|工作台/.test(body) || capturedJsonCount > 0) return 'parser-no-match';
  return 'unknown';
}

function safeUrl(url) {
  try {
    const parsed = new URL(url);
    return `${parsed.origin}${parsed.pathname}`;
  } catch {
    return '';
  }
}

async function clickExactMenuEntry(page, label) {
  return page.evaluate((targetLabel) => {
    const normalize = (value) => (value || '').replace(/\s+/g, '');
    const isVisible = (el) => {
      const rect = el.getBoundingClientRect();
      const style = getComputedStyle(el);
      return rect.width > 1
        && rect.height > 1
        && style.display !== 'none'
        && style.visibility !== 'hidden';
    };
    const candidates = Array.from(document.querySelectorAll('a,button,[role=menuitem],li,div,span'))
      .filter((el) => {
        const textValue = normalize(el.innerText || el.textContent || '');
        return isVisible(el) && textValue === targetLabel;
      })
      .map((el) => {
        const rect = el.getBoundingClientRect();
        const role = el.getAttribute('role') || '';
        const tag = el.tagName || '';
        const score = (role === 'menuitem' ? 0 : 10)
          + (tag === 'LI' ? 1 : tag === 'SPAN' ? 2 : tag === 'A' ? 3 : tag === 'BUTTON' ? 3 : 5)
          + ((rect.width * rect.height) / 100000);
        return { el, rect, role, tag, score };
      })
      .sort((left, right) => left.score - right.score);
    if (!candidates.length) return { clicked: false, label: targetLabel };
    const target = candidates[0];
    target.el.scrollIntoView({ block: 'center', inline: 'center' });
    target.el.click();
    return {
      clicked: true,
      label: targetLabel,
      tag: target.tag,
      role: target.role,
      text: (target.el.innerText || target.el.textContent || '').trim().slice(0, 80),
    };
  }, label).catch((error) => ({ clicked: false, label, error: text(error?.message) }));
}

async function clickOrderEntry(page) {
  const exactOrderManagement = await clickExactMenuEntry(page, '订单管理');
  if (exactOrderManagement.clicked) return exactOrderManagement;
  return page.evaluate(() => {
    const candidates = Array.from(document.querySelectorAll('a,button,[role=menuitem],li,div,span'))
      .filter((el) => {
        const textValue = (el.innerText || el.textContent || '').replace(/\s+/g, '');
        const rect = el.getBoundingClientRect();
        const style = getComputedStyle(el);
        return rect.width > 1
          && rect.height > 1
          && style.display !== 'none'
          && style.visibility !== 'hidden'
          && /订单管理|订单查询|历史订单|订单列表/.test(textValue)
          && textValue.length <= 12;
      })
      .slice(0, 8);
    if (!candidates.length) return { clicked: false, label: '订单管理' };
    const target = candidates[0];
    target.scrollIntoView({ block: 'center', inline: 'center' });
    target.click();
    return { clicked: true, label: '订单管理', text: (target.innerText || target.textContent || '').trim().slice(0, 80) };
  }).catch((error) => ({ clicked: false, error: text(error?.message) }));
}

async function collectOrders(page) {
  const rawOrders = [];
  const capturedResponses = [];
  page.on('response', async (resp) => {
    const url = resp.url();
    if (!/melody\.shop\.ele\.me|\.ele\.me\//.test(url)) return;
    const contentType = resp.headers()['content-type'] || '';
    if (!/json|javascript|text/.test(contentType) && !/api|ajax|order|trade/i.test(url)) return;
    const length = Number(resp.headers()['content-length'] || '0');
    if (length > MAX_RESPONSE_BYTES) return;
    try {
      const body = await resp.text();
      if (body.length > MAX_RESPONSE_BYTES) return;
      const payload = JSON.parse(body);
      const orders = collectOrderObjects(payload);
      if (!orders.length) return;
      rawOrders.push(...orders);
      capturedResponses.push({ url: safeUrl(url), count: orders.length });
    } catch {
      // Ignore non-JSON and unrelated responses.
    }
  });

  await page.goto(HOME_URL, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => null);
  await page.waitForTimeout(5000);
  const clicks = [];
  clicks.push(await clickOrderEntry(page));
  await page.waitForTimeout(5000);
  clicks.push(await clickExactMenuEntry(page, '订单查询'));
  await page.waitForTimeout(10000);
  for (const url of ORDER_URLS) {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => null);
    await page.waitForTimeout(5000);
  }

  const bodyText = (await page.locator('body').innerText().catch(() => '')).slice(0, 5000);
  const parsed = rawOrders.map((order) => parseTbwmOrder(order)).filter(Boolean);
  const seen = new Set();
  const orders = [];
  for (const order of parsed) {
    if (seen.has(order.platform_order_id)) continue;
    seen.add(order.platform_order_id);
    orders.push(order);
  }
  return {
    bodyText,
    rawCount: rawOrders.length,
    capturedResponses,
    clicks,
    orders,
    status: classifyPage(bodyText, orders.length, capturedResponses.length),
  };
}

async function main() {
  const { chromium } = require('playwright-core');
  if (!SHOP_ID || !PHONE || !CONTROL_SHOP_ID) {
    throw new Error('ZR_SYSTEM_SHOP_ID, ZR_SHOP_PHONE, and ZR_SHOP_ID are required');
  }
  const openUrl = `${CONTROL_URL.replace(/\/+$/, '')}/open?phone=${encodeURIComponent(PHONE)}&shopId=${encodeURIComponent(CONTROL_SHOP_ID)}&url=${encodeURIComponent(HOME_URL)}&width=1706&height=900&renderWidth=1706&renderHeight=900&renderScale=1&scale=1&mode=remote-backend`;
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
    platform: 'tbwm',
    generated_at: new Date().toISOString(),
    page_url: page.url(),
    page_title: await page.title(),
    raw_count: collection.rawCount,
    count: collection.orders.length,
    status: collection.status,
    captured_responses: collection.capturedResponses,
    clicks: collection.clicks,
    text_sample: collection.bodyText.replace(/\s+/g, ' ').slice(0, 1200),
    orders: collection.orders,
  };
  fs.writeFileSync(`${OUTPUT_DIR}/tbwm_orders_${SHOP_ID}_${ts}.json`, JSON.stringify(snapshot, null, 2));
  fs.writeFileSync(`${OUTPUT_DIR}/tbwm_orders_${SHOP_ID}.json`, JSON.stringify(snapshot, null, 2));

  const payload = { shopId: SHOP_ID, source: 'tbwm_orders', ingestBatchId: `tbwm-${SHOP_ID}-${ts}`, orders: collection.orders };
  const payloadPath = `${OUTPUT_DIR}/tbwm_ingest_payload.json`;
  fs.writeFileSync(payloadPath, JSON.stringify(payload, null, 2));
  if (!['ok', 'empty'].includes(collection.status)) {
    throw new Error(`TBWM order collection status=${collection.status} orders=${collection.orders.length} raw=${collection.rawCount}`);
  }
  console.log(JSON.stringify({ outputDir: OUTPUT_DIR, platform: 'tbwm', labels: collection.rawCount, orders: collection.orders.length, status: collection.status, payload: payloadPath }));
  await browser.close();
}

if (require.main === module) {
  main().catch((err) => {
    console.error(err.stack || err);
    process.exit(1);
  });
}

module.exports = {
  parseTbwmOrder,
  collectOrderObjects,
  classifyPage,
  money,
  sanitize,
};
