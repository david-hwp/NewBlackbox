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

testJdBackendIsAuthorized();
testJdLoginIsUnauthorized();
console.log('zr-auth-probe tests passed');
