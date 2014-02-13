package jobs;

import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.IndexInfo;
import dto.industryana.SecMarketPerfor;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

/**
 * 行业比较最新市场表现
 * User: wenzhihong
 * Date: 12-12-14
 * Time: 上午9:37
 */
@On("cron.InduCompareLastedMarketPerforTrigger")
@JobDesc(desc = "行业比较-最新市场表现")
public class InduCompareLastedMarketPerforJob extends Job{
    private static final String[] redisKeys = {RedisKey.IndustryAna.sec_lastedMarketPerfor,RedisKey.IndustryAna.idx_lastedMarketPerfor};
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"行业分析 -- 最新市场表现");
        StopWatch sw = new StopWatch("行业比较最新市场表现");
        Gson gson = CommonUtils.createGson();
        String df = "yyyy-MM-dd";
        //上证指数的最新日期,以这个上证指数为准,这就不会错了.
        String maxDateSql = SqlLoader.getSqlById("index_shangzhen_maxdate_2");
        String fromDate = DateFormatUtils.format(DateUtils.addDays(new Date(), -20), df); //从今天往前数20天做为起始日期
        String endDate = "2112-12-21"; //hehe, 世界末日的100年后啊
        sw.start("上证指数的最新日期");
        Date maxDate = ExtractDbUtil.queryExtractDbWithHandler(maxDateSql, new ScalarHandler<Date>(), fromDate, endDate);
        sw.stop();

        if (maxDate != null) {
            String lastDate = DateFormatUtils.format(maxDate, df); //最新的日期
            String secSql = SqlLoader.getSqlById("stockReturnDaily");//个股涨跌幅, 近三个月的

            sw.start("处理股票");
            for(Long secId : BondSec.secIdToCodeMap.keySet()){
                SecMarketPerfor perfor = ExtractDbUtil.queryExtractDbWithHandler(secSql, new StockExtract(), secId, lastDate);
                RedisUtil.set(RedisKey.IndustryAna.sec_lastedMarketPerfor + secId, perfor, gson);
            }
            sw.stop();

            String idxSql = SqlLoader.getSqlById("idxReturnDaily");
            sw.start("处理指数");
            for(Long secId : IndexInfo.id2codeMap.keySet()){//处理指数的
                SecMarketPerfor perfor = ExtractDbUtil.queryExtractDbWithHandler(idxSql, new IdxExtract(), secId, lastDate);
                RedisUtil.set(RedisKey.IndustryAna.idx_lastedMarketPerfor + secId, perfor, gson);
            }
            sw.stop();
        }

        Logger.info(sw.prettyPrint());
    }

    static class IdxExtract implements ResultSetHandler<SecMarketPerfor>{

        @Override
        public SecMarketPerfor handle(ResultSet rs) throws SQLException {
            SecMarketPerfor info = new SecMarketPerfor();
            double maxIncrease3Month = 0;
            double maxDecline3Month = 0;
            while (rs.next()) {
                SecMarketPerfor.Item item = new SecMarketPerfor.Item();
                item.tradingDate = rs.getDate("tradingDate");
                item.returnDaily1 = rs.getDouble("returnDaily1");
                info.addItem(item);
                info.return3Month1 = rs.getDouble("return3Month1"); //一直取到最后一个
                maxIncrease3Month = rs.getDouble("maxIncrease3Month");
                maxDecline3Month = rs.getDouble("maxDecline3Month");
            }

            info.maxReturnDaily1 = maxIncrease3Month;
            info.minReturnDaily1 = maxDecline3Month;

            return info;
        }
    }

    static class StockExtract implements ResultSetHandler<SecMarketPerfor>{
        @Override
        public SecMarketPerfor handle(ResultSet rs) throws SQLException {
            SecMarketPerfor info = new SecMarketPerfor();
            double return3Month1 = 0;
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            int count = 0;
            double returnDaily1;
            while (rs.next()){
                count ++;
                SecMarketPerfor.Item item = new SecMarketPerfor.Item();
                returnDaily1 = rs.getDouble("returnDaily1");
                item.tradingDate = rs.getDate("tradingDate");
                item.returnDaily1 = returnDaily1;
                info.addItem(item);
                return3Month1 = rs.getDouble("return3Month1"); //一直取到最后一个

                if(returnDaily1 > max){
                    max = returnDaily1;
                }

                if(returnDaily1 < min){
                    min = returnDaily1;
                }
            }

            if(count > 0){
                info.return3Month1 = return3Month1;
                info.maxReturnDaily1 = max;
                info.minReturnDaily1 = min;
            }

            return info;
        }
    }
}
