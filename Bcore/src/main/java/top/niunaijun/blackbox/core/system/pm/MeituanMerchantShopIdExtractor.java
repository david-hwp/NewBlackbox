package top.niunaijun.blackbox.core.system.pm;

public class MeituanMerchantShopIdExtractor extends JsonSnippetShopIdExtractor {

    private static final String TARGET_PACKAGE = "com.sankuai.meituan.merchant";
    private static final String TAG = "MeituanMerchantShopIdExtractor";

    public MeituanMerchantShopIdExtractor() {
        super(
                TARGET_PACKAGE,
                "mtjyb",
                TAG,
                new String[]{
                        "files/cips/common/shop_info/kv",
                        "files/cips/common/merchant_biz_accounts/kv",
                        "files/cips/common/merchantaccount/kv",
                        "shared_prefs/com.sankuai.meituan.merchant_preferences.xml"
                },
                new String[]{
                        "shopId",
                        "poiId",
                        "poi_id",
                        "merchantId",
                        "wmPoiId",
                        "id"
                },
                new String[]{
                        "shopName",
                        "poiName",
                        "poi_name",
                        "merchantName",
                        "name"
                }
        );
    }
}
