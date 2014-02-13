package jobs;

import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.financeana.DupontVal;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.util.List;

/**
 * 财务分析 -- 杜邦分析job. 把全部公司的最近一期财务数据里杜邦分析所要的数据提取出来
 * 每天 0:20 执行
 * User: wenzhihong
 * Date: 12-10-29
 * Time: 上午9:58
 */
@On("cron.FinanceDupontTrigger")
@JobDesc(desc = "杜邦分析定时任务, 把所有的公司杜邦数据取出来,重新放入到redis上")
public class FinanceDupontJob extends Job {

    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(new String[]{RedisKey.FinanceAna.dupont},"杜邦分析");
        StopWatch sw = new StopWatch("杜邦分析");
        sw.start("从db中取数据");
        String sql = SqlLoader.getSqlById("dupontAnalysis"); //杜绑分析sql
        String startReportDate = CommonUtils.calcReportDateByCurDate(-12);
        List<DupontVal> dupontValList = ExtractDbUtil.queryExtractDBBeanList(sql, DupontVal.class, startReportDate);
        Gson gson = CommonUtils.createGson();
        //循环取出,设置到redis上
        sw.stop();
        sw.start("设置到redis上");
        for (DupontVal val : dupontValList) {
           RedisUtil.set(RedisKey.FinanceAna.dupont + val.institutionId, val, gson);
        }
        sw.stop();

        Logger.info(sw.prettyPrint());
    }
}
