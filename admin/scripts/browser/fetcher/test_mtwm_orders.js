const assert = require('assert');
const { parseOrderCard, parseOrderMinute } = require('./mtwm_orders');

const ORDER_CARD_TEXT = `
#9
06-24 12:45前送达
用户已收餐
张三
门店新客
下单1次
隐私号码
13800000000 转 1234
备用号码
13900000000 转 5678
顾客电话
手机尾号1234
顾客地址
深圳市南山区科技园
预计收入
￥33.50
订单编号：1802176573697106215
`;

function testParseOrderCardExtractsBusinessFields() {
  const order = parseOrderCard(ORDER_CARD_TEXT);
  assert(order);
  assert.strictEqual(order.platform_order_no, '9');
  assert.strictEqual(order.platform_order_id, '1802176573697106215');
  assert.strictEqual(order.order_time_text, '06-24 12:45');
  assert.strictEqual(order.completed_at, `${new Date().getFullYear()}-06-24T12:45:00`);
  assert.strictEqual(order.customer_name, '张三');
  assert.strictEqual(order.privacy_phone, '13800000000 转 1234');
  assert.strictEqual(order.backup_phone, '13900000000 转 5678');
  assert.strictEqual(order.customer_phone_tail, '1234');
  assert.strictEqual(order.address, '深圳市南山区科技园');
  assert.strictEqual(order.status, '用户已收餐');
  assert.strictEqual(order.estimated_income, 33.5);
  assert.strictEqual(order.raw_payload.order_id, '1802176573697106215');
}

function testParseOrderMinuteMapsLabels() {
  const year = new Date().getFullYear();
  assert.deepStrictEqual(parseOrderMinute('06-24 12:45', '下单', '待配送'), {
    ordered_at: `${year}-06-24T12:45:00`,
  });
  assert.deepStrictEqual(parseOrderMinute('06-24 12:45', '前送达', '待配送'), {
    expected_delivery_at: `${year}-06-24T12:45:00`,
  });
  assert.deepStrictEqual(parseOrderMinute('06-24 12:45', '前送达', '用户已收餐'), {
    completed_at: `${year}-06-24T12:45:00`,
  });
}

function main() {
  testParseOrderCardExtractsBusinessFields();
  testParseOrderMinuteMapsLabels();
  console.log('mtwm_orders tests passed');
}

main();
