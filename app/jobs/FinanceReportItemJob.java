package jobs;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.tom.springutil.StopWatch;
import dbutils.BeanMultiMapHandler;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.financeana.FinanceReportItem;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.RedisKey;
import util.RedisUtil;

/**
 * 财务报表任务
 * User: wenzhihong
 * Date: 13-1-19
 * Time: 上午10:59
 */
@On("cron.FinanceReportItemTrigger")
@JobDesc(desc = "财务分析- 财务报表")
public class FinanceReportItemJob extends Job {
    private static final String[] redisKeys={
            RedisKey.FinanceAna.reportItem1,
            RedisKey.FinanceAna.reportItem2,
            RedisKey.FinanceAna.reportItem3,
            RedisKey.FinanceAna.reportItem4
    };
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"财务分析- 财务报表");
        StopWatch sw = new StopWatch("财务分析-财务报表");

        sw.start("一季报");
        fetchData("financeReport1", RedisKey.FinanceAna.reportItem1);
        sw.stop();

        sw.start("中报");
        fetchData("financeReport2", RedisKey.FinanceAna.reportItem2);
        sw.stop();

        sw.start("三季报");
        fetchData("financeReport3", RedisKey.FinanceAna.reportItem3);
        sw.stop();

        sw.start("年报");
        fetchData("financeReport4", RedisKey.FinanceAna.reportItem4);
        sw.stop();


        Logger.info(sw.prettyPrint());
    }

    private void fetchData(String sqlId, String redisKey) {
        String sqlTpl = SqlLoader.getSqlById(sqlId);
        for (String secIdGroup : BondSec.secIdGroupArr) {
            String sql = sqlTpl.replaceAll("#secIdGroup#", secIdGroup);
            ListMultimap<String,FinanceReportItem> multimap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<String, FinanceReportItem>(FinanceReportItem.class, "secId"));
            for (String secId : multimap.keySet()) {
                RedisUtil.setGsonWithCompress(redisKey + secId, Lists.newArrayList(multimap.get(secId)));
            }
        }
    }
}
