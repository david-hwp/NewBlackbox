const assert = require('assert');
const {
  parseJdOrder,
  classifyPage,
  extractOrderList,
  extractOrderPageMeta,
  money,
} = require('./jdms_orders');

const SAMPLE_ORDER = {
  basicVo: {
    orderId: '3542401007390110',
    jdOrderId: 'JD3542401007390110',
    orderNo: '8',
    stationNo: '16081572',
    expectTimeVo: { expectTimeFmt: '06-27 20:49', expectSuffix: '前送达' },
    orderStatusText: '骑手已取餐',
    orderStatus: 33040,
  },
  userVo: {
    userName: '李**',
    lastDigit: '手机尾号2650',
    appAddress: '长沙市芙蓉区',
    appPhone: 'secret',
  },
  deliveryVo: {
    title: '骑手已取餐',
    deliverManName: '邓家发',
    deliverManPhone: 'secret',
    deliverTag: '达达专送',
    deliverList: [
      { time: '06-27 20:05', desc: '骑手已取餐' },
      { time: '06-27 20:04', desc: '骑手已到店' },
    ],
  },
  feeVo: {
    discountedPrice: '¥21.00',
    estimatedAmount: '¥15.95',
  },
  mealVo: {
    title: '已上报出餐完成，出餐用时 00:07:20',
    orderNum: '8',
  },
  productVo: {
    skuVoList: [
      { name: '【辣度选择】 微辣', price: { text: '¥0.00' }, count: { text: 'x1' }, totalCount: { text: '¥0.00' } },
      { name: '大份臭豆腐+老长沙手工糖油粑粑', price: { text: '¥45.80' }, count: { text: 'x1' }, totalCount: { text: '¥45.80' } },
    ],
  },
  statisticsVo: {
    userRemark: '餐具数量：商家按餐量提供',
    productStatistics: '2种商品，共2件',
    amountStatistics: '预计收入¥15.95',
    amount: '¥15.95',
  },
  overviewVo: {
    orderDescList: [
      '所属门店：长沙市 罗家臭豆腐（万家丽店）',
      '06-27 19:58下单',
    ],
    copyInfo: 'secret',
  },
};

function testParseJdOrderMapsBusinessFields() {
  const item = parseJdOrder(SAMPLE_ORDER, '16081572');
  assert(item);
  assert.strictEqual(item.platform_order_id, '3542401007390110');
  assert.strictEqual(item.platform_order_no, 'JD3542401007390110');
  assert.strictEqual(item.order_sequence, '8');
  assert.strictEqual(item.status, '骑手已取餐');
  assert.strictEqual(item.expected_delivery_at, `${new Date().getFullYear()}-06-27T20:49:00`);
  assert.strictEqual(item.ordered_at, `${new Date().getFullYear()}-06-27T19:58:00`);
  assert.strictEqual(item.estimated_income, 15.95);
  assert.strictEqual(item.customer_name, '李**');
  assert.strictEqual(item.customer_phone_tail, '2650');
  assert.strictEqual(item.address, '长沙市芙蓉区');
  assert.strictEqual(item.delivery_type, '达达专送');
  assert.strictEqual(item.rider_name, '邓家发');
  assert.strictEqual(item.item_summary, '2种商品，共2件');
  assert.strictEqual(item.item_count, 2);
  assert.strictEqual(item.items_json.length, 2);
  assert(!JSON.stringify(item.raw_payload).includes('appPhone'));
  assert(!JSON.stringify(item.raw_payload).includes('deliverManPhone'));
}

function testParseJdOrderFiltersOtherStation() {
  assert.strictEqual(parseJdOrder(SAMPLE_ORDER, '14395758'), null);
}

function testExtractOrderListAndClassifyPage() {
  assert.deepStrictEqual(extractOrderList({ result: { orderPage: { resultList: [SAMPLE_ORDER] } } }), [SAMPLE_ORDER]);
  assert.deepStrictEqual(
    extractOrderPageMeta({ result: { orderPage: { totalCount: 34, totalPage: 4, pageSize: 10 } } }),
    { totalCount: 34, totalPage: 4, pageSize: 10 },
  );
  assert.strictEqual(classifyPage('账号登录 请输入密码', 0), 'login-expired');
  assert.strictEqual(classifyPage('验证一下，购物无忧 快速验证', 0), 'verification-required');
  assert.strictEqual(classifyPage('暂无相关订单 共 0 单', 0), 'empty');
  assert.strictEqual(classifyPage('订单列表', 2), 'ok');
  assert.strictEqual(money('-¥3.85'), -3.85);
}

function main() {
  testParseJdOrderMapsBusinessFields();
  testParseJdOrderFiltersOtherStation();
  testExtractOrderListAndClassifyPage();
  console.log('jdms_orders tests passed');
}

main();
