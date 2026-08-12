package pro.kisscat.www.bookmarkhelper.converter.support;

import android.content.Context;

import java.util.LinkedList;
import java.util.List;

import pro.kisscat.www.bookmarkhelper.converter.support.impl.edge.EdgeBrowser;
import pro.kisscat.www.bookmarkhelper.converter.support.impl.via.ViaBrowserAble;
import pro.kisscat.www.bookmarkhelper.entry.rule.Rule;
import pro.kisscat.www.bookmarkhelper.util.appList.AppListUtil;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/10/12
 * Time:11:12
 */

public class ConverterMaster {
    private transient static List<Rule> supportRule;

    public static List<Rule> getSupportRule() {
        return supportRule;
    }

    public static void init(Context context) {
        AppListUtil.init(context);
        if (supportRule == null) {
            supportRule = new LinkedList<>();
            ViaBrowserAble viaBrowser = ViaBrowserAble.fetchViaBrowser();
            supportRule.add(new Rule(
                    supportRule.size() + 1,
                    context,
                    viaBrowser,
                    new EdgeBrowser()));

        }
    }
}
