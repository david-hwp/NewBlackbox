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
                        "mt_shop_id",
                        "dp_shop_id",
                        "shopId",
                        "shop_id",
                        "poiId",
                        "poi_id",
                        "merchantId",
                        "wmPoiId",
                        "id"
                },
                new String[]{
                        "shop_name",
                        "showName",
                        "show_name",
                        "shopName",
                        "branchName",
                        "branch_name",
                        "poiName",
                        "poi_name",
                        "merchantName",
                        "name"
                }
        );
    }
}
