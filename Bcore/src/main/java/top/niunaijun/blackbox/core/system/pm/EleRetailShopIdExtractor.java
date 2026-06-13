package top.niunaijun.blackbox.core.system.pm;

public class EleRetailShopIdExtractor extends JsonSnippetShopIdExtractor {

    private static final String TARGET_PACKAGE = "com.baidu.lbs.xinlingshou";
    private static final String TAG = "EleRetailShopIdExtractor";

    public EleRetailShopIdExtractor() {
        super(
                TARGET_PACKAGE,
                "tbsglsb",
                TAG,
                new String[]{
                        "shared_prefs/settings.xml"
                },
                new String[]{
                        "shopId",
                        "merchantId",
                        "eleId",
                        "storeId",
                        "sellerId"
                },
                new String[]{
                        "shopName",
                        "merchantName",
                        "loginMerchantName",
                        "storeName",
                        "sellerName"
                }
        );
    }
}
