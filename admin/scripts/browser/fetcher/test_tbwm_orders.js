const assert = require('assert');
const {
  parseTbwmOrder,
  collectOrderObjects,
  classifyPage,
  money,
  sanitize,
} = require('./tbwm_orders');

const SAMPLE_ORDER = {
  orderId: '501982734650271',
  orderNo: '18',
  shopId: '1184657317',
  statusText: '已送达',
  orderTime: '2026-06-28 11:32:15',
  promiseTime: '2026-06-28 12:02:00',
  finishTime: '2026-06-28 11:55:30',
  customerName: '王**',
  privacyPhone: '13600000000 转 1234',
  receiverAddress: '长沙市芙蓉区',
  deliveryType: '蜂鸟配送',
  riderName: '刘师傅',
  riderPhone: 'secret-rider',
  buyerRemark: '不要辣',
  merchantIncome: '¥18.60',
  userPayAmount: '23.80',
  orderAmount: '28.80',
  discountAmount: '5.00',
  packageFee: '1.00',
  deliveryFee: '3.00',
  goodsList: [
    { goodsName: '臭豆腐小份', price: '12.80', count: 1, totalPrice: '12.80' },
    { goodsName: '糖油粑粑', price: '8.00', count: 1, totalPrice: '8.00' },
  ],
  token: 'secret',
  headers: { cookie: 'secret' },
};

const FULFILL_ORDER = {
  id: '8075506179451027748',
  shopId: '1184657317',
  status: 'VALID',
  activeTime: '2026-06-28T20:37:35',
  settledTime: null,
  header: {
    daySn: '32',
    orderType: 'ORDER_NORMAL',
    orderPromptDesc: '21:25 前送达',
    orderLatestStatus: '商家已出餐',
    planDeliverTime: '1782653135000',
  },
  headerExtraInfo: { statusDesc: '' },
  userInfo: {
    consigneeName: '肖**',
    consigneeSecretPhones: ['收餐人 137****8809'],
    consigneeAddress: '开宇大厦(劳动西路)**D-**',
    phoneAlertDescription: '18620418421转786（分机号）',
  },
  deliveryInfo: {
    disDeliveryName: '蜂鸟专送',
    distTraceView: {
      timelines: [
        { time: '20:37', status: '待分配配送商', keyTimeForSorted: '2026-06-28T20:37:36' },
      ],
    },
  },
  foodInfo: {
    groups: [
      {
        name: '',
        items: [
          { name: '臭豆腐小份', price: '12.80', quantity: '1', total: '12.80' },
        ],
      },
    ],
    packageFee: '0',
    deliveryFee: '0',
  },
  remarkInfo: { remark: '多加配菜和汤' },
  settlementInfo: {
    merchantItemActivityInfo: { total: '3.42', totalSign: 'NEGATIVE' },
    expectedIncomeInfo: { total: '12.90' },
    customerPaidInfo: { total: '16.90' },
    orderTotalPrice: '20.32',
  },
};

function testParseTbwmOrderMapsBusinessFields() {
  const item = parseTbwmOrder(SAMPLE_ORDER, '1184657317');
  assert(item);
  assert.strictEqual(item.platform_order_id, '501982734650271');
  assert.strictEqual(item.platform_order_no, '18');
  assert.strictEqual(item.order_sequence, '18');
  assert.strictEqual(item.status, '已送达');
  assert.strictEqual(item.ordered_at, '2026-06-28T11:32:15');
  assert.strictEqual(item.expected_delivery_at, '2026-06-28T12:02:00');
  assert.strictEqual(item.completed_at, '2026-06-28T11:55:30');
  assert.strictEqual(item.estimated_income, 18.6);
  assert.strictEqual(item.customer_paid_amount, 23.8);
  assert.strictEqual(item.original_amount, 28.8);
  assert.strictEqual(item.discount_amount, 5);
  assert.strictEqual(item.delivery_fee, 3);
  assert.strictEqual(item.package_fee, 1);
  assert.strictEqual(item.customer_name, '王**');
  assert.strictEqual(item.customer_phone_tail, '1234');
  assert.strictEqual(item.address, '长沙市芙蓉区');
  assert.strictEqual(item.delivery_type, '蜂鸟配送');
  assert.strictEqual(item.rider_name, '刘师傅');
  assert.strictEqual(item.remark, '不要辣');
  assert.strictEqual(item.item_count, 2);
  assert.strictEqual(item.items_json.length, 2);
  const raw = JSON.stringify(item.raw_payload);
  assert(!raw.includes('token'));
  assert(!raw.includes('cookie'));
  assert(!raw.includes('secret-rider'));
}

function testParseTbwmFulfillOrderMapsBusinessFields() {
  const item = parseTbwmOrder(FULFILL_ORDER, '1184657317');
  assert(item);
  assert.strictEqual(item.platform_order_id, '8075506179451027748');
  assert.strictEqual(item.platform_order_no, '32');
  assert.strictEqual(item.order_sequence, '32');
  assert.strictEqual(item.status, '商家已出餐');
  assert.strictEqual(item.ordered_at, '2026-06-28T20:37:35');
  assert.strictEqual(item.customer_name, '肖**');
  assert.strictEqual(item.customer_phone_tail, '8809');
  assert.strictEqual(item.address, '开宇大厦(劳动西路)**D-**');
  assert.strictEqual(item.delivery_type, '蜂鸟专送');
  assert.strictEqual(item.remark, '多加配菜和汤');
  assert.strictEqual(item.estimated_income, 12.9);
  assert.strictEqual(item.customer_paid_amount, 16.9);
  assert.strictEqual(item.original_amount, 20.32);
  assert.strictEqual(item.discount_amount, 3.42);
  assert.strictEqual(item.item_count, 1);
  assert.strictEqual(item.items_json.length, 1);
}

function testParseTbwmOrderFiltersOtherShop() {
  assert.strictEqual(parseTbwmOrder(SAMPLE_ORDER, 'other-shop'), null);
}

function testCollectOrderObjectsFindsNestedOrderLists() {
  const payload = {
    code: 0,
    data: {
      page: {
        rows: [
          SAMPLE_ORDER,
          { orderId: '501982734650272', shopId: '1184657317', statusText: '待配送', goodsList: [] },
          FULFILL_ORDER,
        ],
      },
    },
  };
  const orders = collectOrderObjects(payload);
  assert.strictEqual(orders.length, 3);
  assert.strictEqual(orders[0].orderId, '501982734650271');
  assert.strictEqual(orders[2].id, '8075506179451027748');
}

function testClassifyPageAndMoney() {
  assert.strictEqual(classifyPage('账号登录 请输入手机号 获取验证码', 0, 0), 'login-expired');
  assert.strictEqual(classifyPage('暂无订单 共 0 单', 0, 0), 'empty');
  assert.strictEqual(classifyPage('饿了么商家中心 订单管理', 0, 1), 'parser-no-match');
  assert.strictEqual(classifyPage('订单管理', 2, 1), 'ok');
  assert.strictEqual(money({ amountCent: 1886 }), 18.86);
  assert.strictEqual(money('-¥3.85'), -3.85);
}

function testSanitizeRemovesSensitiveKeysDeeply() {
  const safe = sanitize({
    orderId: '1',
    auth: 'secret',
    nested: { sessionToken: 'secret', amount: '12.00' },
  });
  const serialized = JSON.stringify(safe);
  assert(serialized.includes('orderId'));
  assert(serialized.includes('amount'));
  assert(!serialized.includes('secret'));
  assert(!serialized.includes('sessionToken'));
}

function main() {
  testParseTbwmOrderMapsBusinessFields();
  testParseTbwmFulfillOrderMapsBusinessFields();
  testParseTbwmOrderFiltersOtherShop();
  testCollectOrderObjectsFindsNestedOrderLists();
  testClassifyPageAndMoney();
  testSanitizeRemovesSensitiveKeysDeeply();
  console.log('tbwm_orders tests passed');
}

main();
