const assert = require('assert');
const { decide, platformPageSignals } = require('./zr-auth-probe');

function signals(url, textSample, extra = {}) {
  return {
    url,
    page: {
      title: extra.title || '',
      url,
      textSample,
      hasConsoleText: !!extra.hasConsoleText,
      hasLogoutText: false,
      hasLoginText: !!extra.hasLoginText,
      visibleInputCount: extra.visibleInputCount || 0,
    },
    cookies: {
      matchingCount: extra.matchingCount || 0,
    },
    storage: {
      localStorageCount: extra.localStorageCount || 0,
      localStorageSignalKeys: extra.localStorageSignalKeys || [],
      sessionStorageSignalKeys: extra.sessionStorageSignalKeys || [],
    },
  };
}

function testJdBackendIsAuthorized() {
  const sample = '全部门店 商家首页 订单管理 商品管理 经营罗盘 今日有效订单 22 单 罗家臭豆腐（万家丽店）';
  const result = decide(signals('https://store.jddj.com/', sample, {
    title: '京东秒送商家端',
    matchingCount: 29,
    localStorageCount: 3,
  }));

  assert.strictEqual(result.status, 'AUTHORIZED');
  assert.strictEqual(result.confidence, 'HIGH');
}

function testJdLoginIsUnauthorized() {
  const sample = '电脑端下载 手机客户端 账号登录 验证码登录 请输入用户名 请输入密码 忘记密码 登录';
  const page = signals('https://store.jddj.com/base/login', sample, {
    title: '京东秒送商家端',
    matchingCount: 29,
    hasLoginText: true,
    visibleInputCount: 2,
  });
  const platform = platformPageSignals(page);
  const result = decide(page);

  assert.strictEqual(platform.hasConsoleText, false);
  assert.strictEqual(platform.hasLoginText, true);
  assert.strictEqual(result.status, 'UNAUTHORIZED');
  assert.strictEqual(result.confidence, 'HIGH');
}

function testTbwmBackendIsAuthorized() {
  const sample = '饿了么商家中心 工作台 订单管理 商品管理 门店管理 经营数据 罗家臭豆腐';
  const result = decide(signals('https://melody.shop.ele.me/', sample, {
    title: '饿了么商家中心',
    matchingCount: 12,
    localStorageCount: 4,
  }));

  assert.strictEqual(result.status, 'AUTHORIZED');
  assert.strictEqual(result.confidence, 'HIGH');
}

function testTbwmLoginIsUnauthorized() {
  const sample = '饿了么商家中心 账号登录 验证码登录 请输入手机号 请输入密码 获取验证码 登录 支付宝登录';
  const page = signals('https://melody.shop.ele.me/login', sample, {
    title: '饿了么商家登录',
    matchingCount: 12,
    hasLoginText: true,
    visibleInputCount: 2,
  });
  const platform = platformPageSignals(page);
  const result = decide(page);

  assert.strictEqual(platform.hasConsoleText, false);
  assert.strictEqual(platform.hasLoginText, true);
  assert.strictEqual(result.status, 'UNAUTHORIZED');
  assert.strictEqual(result.confidence, 'HIGH');
}

function testTbwmCookieBackedRootIsAuthorized() {
  const page = signals('https://melody.shop.ele.me/', 'melody.shop.ele.me', {
    title: 'melody.shop.ele.me',
    matchingCount: 8,
  });
  const result = decide(page);

  assert.strictEqual(result.status, 'AUTHORIZED');
  assert.strictEqual(result.confidence, 'MEDIUM');
}

testJdBackendIsAuthorized();
testJdLoginIsUnauthorized();
testTbwmBackendIsAuthorized();
testTbwmLoginIsUnauthorized();
testTbwmCookieBackedRootIsAuthorized();
console.log('zr-auth-probe tests passed');
