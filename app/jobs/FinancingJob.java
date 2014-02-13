package jobs;

import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.financing.Financing;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import play.Logger;
import play.exceptions.JavaExecutionException;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.util.List;

/**
 * 融资融券
 * User: wenzhihong
 */
@On("cron.FinancingTrigger")
@JobDesc(desc = "融资融券定时任务")
public class FinancingJob extends Job {
  private static final String[] redisKeys = {RedisKey.Financing.financing,RedisKey.Financing.allFinancingSecList};
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"融资融券");
        StopWatch sw = new StopWatch("融资融券定时任务");
        sw.start();
        List<Long> secIdList = allFinancingSecList();
        financingData(secIdList);
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

    private void financingData(List<Long> secIdList) {
        Gson gson = CommonUtils.createGson();
        String sql = SqlLoader.getSqlById("financingSql");
        for (Long secId : secIdList) {
            List<Financing> financingList = ExtractDbUtil.queryExtractDBBeanList(sql, Financing.class, secId);
            String key = RedisKey.Financing.financing + secId;
            RedisUtil.rpushWithDel(key, financingList, gson);

        }
    }

    private List<Long> allFinancingSecList() {
        String sql = SqlLoader.getSqlById("allFinancingSecList");
        List<Long> secIdList = ExtractDbUtil.queryExtractDbWithHandler(sql, new ColumnListHandler<Long>());
        RedisUtil.set(RedisKey.Financing.allFinancingSecList, secIdList);
        return secIdList;
    }
}
