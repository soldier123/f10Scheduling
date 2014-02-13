package jobs;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.BeanMultiMapHandler;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.predictprofit.*;
import org.apache.commons.lang.time.DateUtils;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 盈利预测
 * User: liangbing
 * Date: 12-10-29
 * Time: 上午10:59
 */
@On("cron.PredictProfitCtTrigger")
@JobDesc(desc = "盈利预测定时任务")
public class PredictProfitCtJob extends Job {
   private static final String[] redisKeys = {
            RedisKey.PredictProfit.ratingChange,
            RedisKey.PredictProfit.targetPrice,
            RedisKey.PredictProfit.actualPrice1Month,
            RedisKey.PredictProfit.last5YearEps,
            RedisKey.PredictProfit.last5YearNetProfit,
            RedisKey.PredictProfit.forecast3YearEps,
            RedisKey.PredictProfit.forecast3YearNetProfit,
            RedisKey.PredictProfit.f3yearEpsDetail,
            RedisKey.PredictProfit.f3yearNetProfitDetail
   };
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"盈利预测");
        StopWatch sw = new StopWatch("盈利预测");

        sw.start("评级变动");
        ratingChange();
        sw.stop();

        sw.start("预测目标价");
        forecastPrice();
        sw.stop();

        sw.start("近一个月的价格");
        price1Month();
        sw.stop();

        sw.start("近5年eps");
        last5YearEps();
        sw.stop();

        sw.start("近5年净利润");
        last5YearNetProfit();
        sw.stop();

        sw.start("预测3年的eps数据");
        forecast3YearEps();
        sw.stop();

        sw.start("预测3年的 净利润 数据");
        forecast3YearNetProfit();
        sw.stop();

        sw.start("预测三年的 eps 明细数据");
        f3yearEpsDetail();
        sw.stop();

        sw.start("预测三年的 净利润 明细数据");
        f3yearNetProfitDetail();
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

    /**
     * 评级变动
     */
    private void ratingChange(){
        String sqlTpl = SqlLoader.getSqlById("ratingChangeSql");
        Gson gson = CommonUtils.createGson();
        for (String secIdGroup : BondSec.secIdGroupArr) {
            String sql = sqlTpl.replaceAll("#secIdGroup#", secIdGroup);
            ListMultimap<Long,RatingChange> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, RatingChange>(RatingChange.class, "secId"));

            for (Long secId : infoMap.keySet()) {
                List<RatingChange> list = new ArrayList<RatingChange>(infoMap.get(secId));
                RedisUtil.set(RedisKey.PredictProfit.ratingChange + secId, list, gson);
            }
        }
    }

    /**
     * 预测目标价
     */
    private void forecastPrice() {
        String sqlTpl = SqlLoader.getSqlById("forecastPriceSql");
        Gson gson = CommonUtils.createGson();
        for (String secIdGroup : BondSec.secIdGroupArr) {
            String sql = sqlTpl.replaceAll("#secIdGroup#", secIdGroup);
            ListMultimap<Long,PriceItem> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, PriceItem>(PriceItem.class, "secId"));

            for (Long secId : infoMap.keySet()) {
                List<PriceItem> list = new ArrayList<PriceItem>(infoMap.get(secId));
                RedisUtil.set(RedisKey.PredictProfit.targetPrice + secId, list, gson);
            }
        }
    }

    /**
     * 近一个月的价格
     */
    private void price1Month(){
        String sqlTpl = SqlLoader.getSqlById("price1MonthSql");
        Gson gson = CommonUtils.createGson();
        for (String secIdGroup : BondSec.secIdGroupArr) {
            String sql = sqlTpl.replaceAll("#secIdGroup#", secIdGroup);
            ListMultimap<Long, PriceItem> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, PriceItem>(PriceItem.class, "secId"));

            for (Long secId : infoMap.keySet()) {
                List<PriceItem> list = new ArrayList<PriceItem>(infoMap.get(secId));
                RedisUtil.set(RedisKey.PredictProfit.actualPrice1Month + secId, list, gson);
            }
        }
    }

    /**
     * 近5年eps
     */
    private void last5YearEps(){
        String sqlTpl = SqlLoader.getSqlById("last5YearEpsSql");
        Gson gson = CommonUtils.createGson();
        for (String orgIdList : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgIdList#", orgIdList);
            ListMultimap<Long, PriceIt2> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, PriceIt2>(PriceIt2.class, "institutionId"));

            for (Long institutionId : infoMap.keySet()) {
                List<PriceIt2> list = new ArrayList<PriceIt2>(infoMap.get(institutionId));
                RedisUtil.set(RedisKey.PredictProfit.last5YearEps + institutionId, list, gson);
            }
        }
    }

    /**
     * 近5年净利润
     */
    private void last5YearNetProfit(){
        String sqlTpl = SqlLoader.getSqlById("last5YearNetProfitSql");
        Gson gson = CommonUtils.createGson();
        for (String orgIdList : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgIdList#", orgIdList);
            ListMultimap<Long, PriceIt2> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, PriceIt2>(PriceIt2.class, "institutionId"));

            for (Long institutionId : infoMap.keySet()) {
                List<PriceIt2> list = new ArrayList<PriceIt2>(infoMap.get(institutionId));
                RedisUtil.set(RedisKey.PredictProfit.last5YearNetProfit + institutionId, list, gson);
            }
        }
    }

    /**
     * 预测3年的eps数据
     */
    private void forecast3YearEps(){
        forecaststatistics("forecast3YearEpsSql", RedisKey.PredictProfit.forecast3YearEps);
    }

    /**
     * 预测3年的 净利润 数据
     */
    private void forecast3YearNetProfit(){
        forecaststatistics("forecast3YearNetProfitSql", RedisKey.PredictProfit.forecast3YearNetProfit);
    }

    /**
     * 预测三年的 eps 明细数据
     */
    private void f3yearEpsDetail() {
        forecastDetail("f3yearEpsDetailSql", RedisKey.PredictProfit.f3yearEpsDetail);
    }

    /**
     * 预测三年的 净利润 明细数据
     */
    private void f3yearNetProfitDetail(){
        forecastDetail("f3yearNetProfitDetailSql", RedisKey.PredictProfit.f3yearNetProfitDetail);
    }


    /**
     * 预测数据提取. 统计类的
     */
    private void forecaststatistics(String sqlId, String redisKeyId){
        String sqlTpl = SqlLoader.getSqlById(sqlId);
        Gson gson = CommonUtils.createGson();
        String sdate = CommonUtils.getFormatDate("yyyy-MM-dd", DateUtils.addYears(new Date(), -1));//开始日期, 往前推一年
        for (String scodeGroup : BondSec.secCodeGroupArr) {
            String sql = sqlTpl.replaceAll("#scodeGroup#", scodeGroup);
            ListMultimap<Long, PriceIt2> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, PriceIt2>(PriceIt2.class, "secId"), sdate);

            for (Long secId : infoMap.keySet()) {
                List<PriceIt2> list = Lists.newLinkedList(infoMap.get(secId));
                RedisUtil.set(redisKeyId + secId, list, gson);
            }
        }
    }

    /**
     * 预测数据提取. 明细类的
     */
    private  void forecastDetail(String sqlId, String redisKeyId){
        String sqlTpl = SqlLoader.getSqlById(sqlId);
        Gson gson = CommonUtils.createGson();
        String sdate = CommonUtils.getFormatDate("yyyy-MM-dd", DateUtils.addYears(new Date(), -1));//开始日期, 往前推一年
        for (String scodeGroup : BondSec.secCodeGroupArr) {
            String sql = sqlTpl.replaceAll("#scodeGroup#", scodeGroup);
            ListMultimap<Long, ForecastDetail> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, ForecastDetail>(ForecastDetail.class, "secId"), sdate);

            for (Long secId : infoMap.keySet()) {

                List<ForecastDetail> list = Lists.newLinkedList(infoMap.get(secId));
                //得到一个年度最小值
                int minYear = Integer.MAX_VALUE;
                for (ForecastDetail f : list) {
                    if(f.forecastYear < minYear){
                        minYear = f.forecastYear;
                    }
                }

                //用reportId做为key值
                Map<Long, DetailByReport> reportIdMap = Maps.newLinkedHashMap();
                //把它展开成按reportId进行合并还原
                for (ForecastDetail f : list) {
                    DetailByReport row = reportIdMap.get(f.reportId);
                    if (row == null) {
                        row = new DetailByReport();
                        row.analyst = f.analyst;
                        row.orgName = f.orgName;
                        row.rating = f.rating;
                        row.reportDate = f.reportDate;
                        row.reportId = f.reportId;
                        reportIdMap.put(f.reportId, row);
                    }

                    if (f.forecastYear == minYear) {
                        row.fprice1 = f.price;
                    } else if (f.forecastYear == (minYear + 1)) {
                        row.fprice2 = f.price;
                    } else if (f.forecastYear == (minYear + 2)) {
                        row.fprice3 = f.price;
                    }
                }

                List<DetailByReport> resultData = Lists.newArrayList();
                for (Long k : reportIdMap.keySet()) {
                    resultData.add(reportIdMap.get(k));
                }

                RedisUtil.set(redisKeyId + secId, resultData, gson);
            }
        }
    }
}
