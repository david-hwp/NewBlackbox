package top.niunaijun.blackbox.core.system.pm;

public class CtripEBookingShopIdExtractor extends JsonSnippetShopIdExtractor {

    private static final String TARGET_PACKAGE = "com.Hotel.EBooking";
    private static final String TAG = "CtripEBookingShopIdExtractor";

    public CtripEBookingShopIdExtractor() {
        super(
                TARGET_PACKAGE,
                "xiezheng",
                TAG,
                new String[]{
                        "shared_prefs/comHotelEBooking.xml",
                        "files/mmkv/ctrip__ctstorage__ebk_login",
                        "files/mmkv/ctrip__ctstorage__ebk_CtripUser"
                },
                new String[]{
                        "hotelId",
                        "masterHotelId",
                        "loginName",
                        "uid",
                        "userId"
                },
                new String[]{
                        "hotelName",
                        "masterHotelName",
                        "userName",
                        "name"
                }
        );
    }
}
