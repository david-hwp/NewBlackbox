const fs = require('fs');

const SHOP_ID = Number(process.env.ZR_SYSTEM_SHOP_ID || '0');
const PHONE = process.env.ZR_SHOP_PHONE || '';
const CONTROL_SHOP_ID = process.env.ZR_SHOP_ID || '';
const SHOP_NAME = process.env.ZR_SHOP_NAME || 'shop';
const OUTPUT_DIR = process.env.OUTPUT_DIR || '/tmp/order-output';
const CONTROL_URL = process.env.ZR_CONTROL_URL || 'http://127.0.0.1:14501';

function parseOrderMinute(value, label, status) {
  if (!value) return {};
  const year = new Date().getFullYear();
  const m = value.match(/(\d{2})-(\d{2})\s+(\d{2}:\d{2})/);
  if (!m) return {};
  const iso = `${year}-${m[1]}-${m[2]}T${m[3]}:00`;
  if (new Set(['用户已收餐', '已完成']).has(status)) return { completed_at: iso };
  if (label === '下单') return { ordered_at: iso };
  if (label === '前送达') return { expected_delivery_at: iso };
  return {};
}

function parseOrderCard(text) {
  const lines = text.split(/\n+/).map((line) => line.trim()).filter(Boolean);
  if (!lines.length) return null;
  const fullText = lines.join('|');
  const orderId = fullText.match(/订单编号[：:]\s*(\d+)/)?.[1];
  if (!orderId) return null;
  const orderNo = fullText.match(/#(\d+)/)?.[1] || null;
  const timeMatch = fullText.match(/(\d{2}-\d{2}\s+\d{2}:\d{2})(前送达|下单)?/);
  const orderTime = timeMatch?.[1] || null;
  const orderTimeLabel = timeMatch?.[2] || null;
  const statusCandidates = ['骑手已取餐', '用户已收餐', '已取消', '待接单', '待配送', '已完成', '已退款'];
  const status = statusCandidates.find((item) => fullText.includes(item)) || null;
  const parts = fullText.split('|').map((p) => p.trim()).filter(Boolean);
  let customerName = null;
  for (let i = 0; i < parts.length; i++) {
    if (['门店新客', '发起聊天', '美团顾客'].includes(parts[i]) || /^下单\d+次$/.test(parts[i])) {
      const candidate = parts[i - 1];
      if (/^[一-龥·]{1,6}$/.test(candidate || '')) {
        customerName = candidate;
        break;
      }
    }
  }
  const privacyPhone = fullText.match(/隐私号码\|(\d{11}\s*转\s*\d{4})/)?.[1]?.replace(/\s+/g, ' ') || null;
  const backupPhone = fullText.match(/备用号码\|(\d{11}\s*转\s*\d{4})/)?.[1]?.replace(/\s+/g, ' ') || null;
  const customerTail = fullText.match(/顾客电话\|手机尾号(\d{4})/)?.[1] || null;
  const address = fullText.match(/顾客地址\|([^|]+?)(?=\|(?:已出餐|骑手|订单已|备注|隐私号码|顾客电话|预计收入|顾客商品实付|订单编号|商品|餐品|$))/)?.[1]?.trim() || null;
  const amountMatch = fullText.match(/预计收入\|￥([\d.]+)/);
  const amount = amountMatch ? Number(amountMatch[1]) : null;
  const fetchedAt = new Date().toISOString().slice(0, 19);
  return Object.fromEntries(Object.entries({
    platform_order_id: orderId,
    platform_order_no: orderNo,
    order_sequence: orderNo,
    order_time_text: orderTime,
    ...parseOrderMinute(orderTime, orderTimeLabel, status),
    fetched_at: fetchedAt,
    status,
    status_text: status,
    estimated_income: amount,
    customer_name: customerName,
    privacy_phone: privacyPhone,
    backup_phone: backupPhone,
    customer_phone_tail: customerTail,
    address,
    recipient_address: address,
    raw_text: fullText.slice(0, 800),
    raw_payload: {
      order_no: orderNo,
      order_id: orderId,
      order_time: orderTime,
      order_time_label: orderTimeLabel,
      status,
      fetched_at: fetchedAt,
    },
  }).filter(([, v]) => v !== null && v !== undefined));
}

async function main() {
  const { chromium } = require('playwright-core');
  if (!SHOP_ID || !PHONE || !CONTROL_SHOP_ID) {
    throw new Error('ZR_SYSTEM_SHOP_ID, ZR_SHOP_PHONE, and ZR_SHOP_ID are required');
  }
  const orderUrl = 'https://waimaie.meituan.com/new_fe/orderbusiness#/order/history';
  const openUrl = `${CONTROL_URL.replace(/\/+$/, '')}/open?phone=${encodeURIComponent(PHONE)}&shopId=${encodeURIComponent(CONTROL_SHOP_ID)}&url=${encodeURIComponent(orderUrl)}&width=1706&height=775&renderWidth=1706&renderHeight=775&renderScale=1&scale=1&mode=remote-backend`;
  const openResp = await fetch(openUrl);
  const openJson = await openResp.json();
  if (!openJson.ok) throw new Error(`open failed: ${JSON.stringify(openJson)}`);

  const browser = await chromium.connectOverCDP(`http://127.0.0.1:${openJson.debugPort}`);
  const context = browser.contexts()[0] || await browser.newContext();
  const page = context.pages()[0] || await context.newPage();
  await page.goto(orderUrl, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => null);
  await page.waitForTimeout(5000);

  const labelLocators = await page.locator('text=/^#\\d+$/').all().catch(() => []);
  const labels = [];
  for (const loc of labelLocators) {
    const label = (await loc.innerText().catch(() => '')).trim();
    if (label && !labels.includes(label)) labels.push(label);
  }

  for (const label of labels) {
    await page.locator(`text=${label}`).first().evaluate((el) => {
      let cur = el;
      for (let i = 0; i < 10; i++) {
        cur = cur.parentElement;
        if (!cur) break;
        if ((cur.innerText || '').includes('顾客地址')) break;
      }
      if (!cur) return 0;
      cur.scrollIntoView({ block: 'center' });
      let clicked = 0;
      for (const b of cur.querySelectorAll('*')) {
        if ((b.innerText || '').trim() === '点击查看' && b.offsetParent !== null) {
          b.click();
          clicked++;
        }
      }
      return clicked;
    }).catch(() => null);
  }
  await page.waitForTimeout(1500);

  const orders = [];
  for (const label of labels) {
    const cardText = await page.locator(`text=${label}`).first().evaluate((el) => {
      let cur = el;
      for (let i = 0; i < 10; i++) {
        cur = cur.parentElement;
        if (!cur) break;
        if ((cur.innerText || '').includes('顾客地址')) break;
      }
      return cur ? cur.innerText : '';
    }).catch(() => '');
    const parsed = parseOrderCard(cardText);
    if (parsed) orders.push(parsed);
  }

  const bodyText = (await page.locator('body').innerText().catch(() => '')).slice(0, 5000);
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  const ts = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
  const snapshot = {
    shop_name: SHOP_NAME,
    system_shop_id: SHOP_ID,
    control_shop_id: CONTROL_SHOP_ID,
    generated_at: new Date().toISOString(),
    page_url: page.url(),
    page_title: await page.title(),
    labels,
    count: orders.length,
    empty_hint: bodyText.includes('暂无相关订单') || bodyText.includes('共 0 单'),
    orders,
  };
  fs.writeFileSync(`${OUTPUT_DIR}/meituan_orders_${SHOP_ID}_${ts}.json`, JSON.stringify(snapshot, null, 2));
  fs.writeFileSync(`${OUTPUT_DIR}/meituan_orders_${SHOP_ID}.json`, JSON.stringify(snapshot, null, 2));

  const payload = { shopId: SHOP_ID, source: 'mtwm_orders', ingestBatchId: `mtwm-${SHOP_ID}-${ts}`, orders };
  const payloadPath = `${OUTPUT_DIR}/mtwm_ingest_payload.json`;
  fs.writeFileSync(payloadPath, JSON.stringify(payload, null, 2));
  console.log(JSON.stringify({ outputDir: OUTPUT_DIR, labels: labels.length, orders: orders.length, emptyHint: snapshot.empty_hint, payload: payloadPath }));
  await browser.close();
}

if (require.main === module) {
  main().catch((err) => {
    console.error(err.stack || err);
    process.exit(1);
  });
}

module.exports = {
  parseOrderCard,
  parseOrderMinute,
};
