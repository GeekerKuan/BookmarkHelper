package pro.kisscat.www.bookmarkhelper.pojo.executor;

import lombok.Getter;
import lombok.Setter;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/11/24
 * Time:11:03
 */

public class Result {
    @Setter
    @Getter
    private boolean isComplete;
    @Setter
    @Getter
    private int successCount = 0;
    @Setter
    private String errorMsg;
    @Setter
    private String warnMsg;
    @Setter
    private String successMsg;

    public String getErrorMsg() {
        return errorMsg;
    }

    public String getWarnMsg() {
        return warnMsg;
    }

    public String getSuccessMsg() {
        return successMsg;
    }

    public Result() {

    }

    public Result(String warnMsg) {
        this.warnMsg = warnMsg;
    }
}
