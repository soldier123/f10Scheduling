package jobs;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.BeanMultiMapHandler;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.dividend.*;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanMapHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 处理分红融资
 * User: wenzhihong
 */
@On("cron.DividentTrigger")
@JobDesc(desc = "取分红融资信息, 放入到redis上")
public class DividentJob extends Job {
    private static final String[] redisKey = {
             RedisKey.Dividend.cashBonusAndRaiseFund ,
             RedisKey.Dividend.allMarketCashBonusAndRaiseFund,
             RedisKey.Dividend.cashBonusDetail,
             RedisKey.Dividend.addIssuingDetail,
             RedisKey.Dividend.allotmentDetail
    };
    public void doJob() throws Exception {
        RedisUtil.clean(redisKey,"分红融资");
        Gson gson = CommonUtils.createGson();
        StopWatch sw = new StopWatch("分红融资任务");
        sw.start("分红融资图表数据");
        cashBonusAndRaiseFund(gson);
        sw.stop();

        sw.start("分红明细");
        cashBonusDetail(gson);
        sw.stop();

        sw.start("增发明细");
        addIssuingDetail(gson);
        sw.stop();

        sw.start("配股明细");
        allotmentDetail(gson);
        sw.stop();

        Logger.info(sw.prettyPrint());
    }


    //分红融资数据
    private void cashBonusAndRaiseFund(Gson gson){
        //配股
        Map<Long, RaiseFundItem> allotmentMap = secFundMap("allotmentSec");
        double allotmentSum = fundSumInMarket("allotmentMarketSum");

        //增发1
        Map<Long, RaiseFundItem> addIssuing1Map = secFundMap("addIssuing1Sec");
        double addIssuing1Sum = fundSumInMarket("addIssuing1MarketSum");

        //增发2
        Map<Long, RaiseFundItem> addIssuing2Map = secFundMap("addIssuing2Sec");
        double addIssuing2Sum = fundSumInMarket("addIssuing2MarketSum");

        //新股发行
        Map<Long, RaiseFundItem> firstIssuingMap = secFundMap("firstIssuingSec");
        double firstIssuingSum = fundSumInMarket("firstIssuingMarketSum");

        //分红
        Map<Long, RaiseFundItem> cashBonusMap = Maps.newHashMap();
        double cashBonusSum = 0;
        cashBonusMap = secFundMap("cashBonusSec");
        cashBonusSum = fundSumInMarket("cashBonusMarketSum");

        //转送股次数
        final Map<Long, Integer> transfersharesMap = Maps.newHashMap();

        ExtractDbUtil.queryExtractDbWithHandler(SqlLoader.getSqlById("transfershares"), new ResultSetHandler<Object>() {
            @Override
            public Object handle(ResultSet rs) throws SQLException {
                while (rs.next()){
                    transfersharesMap.put(rs.getLong("secId"), rs.getInt("sendCount"));
                }
                return null;
            }
        });


        //组合出来.
        List<RaiseFundOverall> allList = Lists.newArrayListWithCapacity(BondSec.secIdToCodeMap.size());
        for (Long secId : BondSec.secIdToCodeMap.keySet()) {
            RaiseFundOverall item = new RaiseFundOverall();
            allList.add(item);

            item.secId = secId;

            item.allotmentSum = allotmentMap.get(secId) == null ? 0 : allotmentMap.get(secId).raiseFundSum;
            item.allotmentCount = allotmentMap.get(secId) == null ? 0 : allotmentMap.get(secId).raiseFundCount;

            item.addIssuing1Sum = addIssuing1Map.get(secId) == null ? 0 : addIssuing1Map.get(secId).raiseFundSum;
            item.addIssuing1Count = addIssuing1Map.get(secId) == null ? 0 : addIssuing1Map.get(secId).raiseFundCount;

            item.addIssuing2Sum = addIssuing2Map.get(secId) == null ? 0 : addIssuing2Map.get(secId).raiseFundSum;
            item.addIssuing2Count = addIssuing2Map.get(secId) == null ? 0 : addIssuing2Map.get(secId).raiseFundCount;

            item.firstIssuingSum = firstIssuingMap.get(secId) == null ? 0 : firstIssuingMap.get(secId).raiseFundSum;

            item.cashBonusSum = cashBonusMap.get(secId) == null ? 0 : cashBonusMap.get(secId).raiseFundSum;
            item.cashBonusCount = cashBonusMap.get(secId) == null ? 0 : cashBonusMap.get(secId).raiseFundCount;

            item.sendCount = transfersharesMap.get(secId) == null ? 0 : transfersharesMap.get(secId);

            item.getRate(); //计算比率
        }

        //计算排名
        Collections.sort(allList, new Comparator<RaiseFundOverall>() {
            @Override
            public int compare(RaiseFundOverall o1, RaiseFundOverall o2) {
                //把排名倒过来. O2 在前面
                return Ordering.natural().reverse().nullsLast().compare(o1.rate, o2.rate);
                //return Doubles.compare(o2.rate, o1.rate);
            }
        });

        //设置到redis上
        int ranking = 1; //排名
        for (RaiseFundOverall item : allList) {
            item.rateRanking = ranking;
            ranking++;
            RedisUtil.set(RedisKey.Dividend.cashBonusAndRaiseFund + item.secId, item, gson);
        }

        //整个市场的
        RaiseFundOverall allMaket = new RaiseFundOverall();
        allMaket.allotmentSum = allotmentSum;
        allMaket.addIssuing1Sum = addIssuing1Sum;
        allMaket.addIssuing2Sum = addIssuing2Sum;
        allMaket.firstIssuingSum = firstIssuingSum;
        allMaket.cashBonusSum = cashBonusSum;
        allMaket.getRate();
        RedisUtil.set(RedisKey.Dividend.allMarketCashBonusAndRaiseFund, allMaket, gson);
    }

    //各股的
    private Map<Long, RaiseFundItem> secFundMap(String sqlId){
        String sql1 = SqlLoader.getSqlById(sqlId);
        Map<Long, RaiseFundItem> peiGuMap = ExtractDbUtil.queryExtractDbWithHandler(sql1,  new BeanMapHandler<Long, RaiseFundItem>(RaiseFundItem.class, "secId"));
        if(peiGuMap == null){
            return Maps.newHashMap();
        }else{
            return peiGuMap;
        }
    }

    //市场范围的合计
    private double fundSumInMarket(String sqlId){
        String sql = SqlLoader.getSqlById(sqlId);
        Number sum = ExtractDbUtil.queryExtractDbWithHandler(sql, new ScalarHandler<Number>("raiseFundSum"));
        if(sum != null){
            return sum.doubleValue();
        }else{
            return 0;
        }
    }

    //分红明细
    private void cashBonusDetail(Gson gson){
        String sqlTpl = SqlLoader.getSqlById("cashBonusDetail");
        for (String secIdGroup : BondSec.secIdGroupArr) {
            String sql = sqlTpl.replaceAll("#secIdGroup#", secIdGroup);
            ListMultimap<Long, CashBonusDetail> infoListMap = ExtractDbUtil.queryExtractDbWithHandler(sql,
                    new BeanMultiMapHandler<Long, CashBonusDetail>(CashBonusDetail.class, "secId"));

            for (Long secId : infoListMap.keySet()) {
                RedisUtil.set(RedisKey.Dividend.cashBonusDetail + secId, new ArrayList<CashBonusDetail>(infoListMap.get(secId)), gson);
            }
        }
    }

    //增发明细
    private void addIssuingDetail(Gson gson){
        String sqlTpl = SqlLoader.getSqlById("addIssuingDetail");
        for(String secIdGroup : BondSec.secIdGroupArr){
            String sql = sqlTpl.replaceAll("#secIdGroup#", secIdGroup);
            ListMultimap<Long, AddIssuingDetail> infoListMap = ExtractDbUtil.queryExtractDbWithHandler(sql,
                    new BeanMultiMapHandler<Long, AddIssuingDetail>(AddIssuingDetail.class, "secId"));

            for (Long secId : infoListMap.keySet()) {
                RedisUtil.set(RedisKey.Dividend.addIssuingDetail + secId, new ArrayList<AddIssuingDetail>(infoListMap.get(secId)), gson);
            }
        }
    }

    //配股明细
    private void allotmentDetail(Gson gson){
        String sqlTpl = SqlLoader.getSqlById("allotmentDetail");
        for(String secIdGroup : BondSec.secIdGroupArr) {
            String sql = sqlTpl.replaceAll("#secIdGroup#", secIdGroup);
            ListMultimap<Long, AllotmentDetail> infoListMap = ExtractDbUtil.queryExtractDbWithHandler(sql,
                    new BeanMultiMapHandler<Long, AllotmentDetail>(AllotmentDetail.class, "secId"));

            for (Long secId : infoListMap.keySet()) {
                RedisUtil.set(RedisKey.Dividend.allotmentDetail + secId, new ArrayList<AllotmentDetail>(infoListMap.get(secId)), gson);
            }
        }
    }

}
