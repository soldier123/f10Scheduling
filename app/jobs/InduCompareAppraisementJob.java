package jobs;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.industryana.AppraisementFullIndu;
import dto.industryana.AppraisementFullItem;
import dto.industryana.AppraisementRankItem;
import org.apache.commons.beanutils.BeanComparator;
import org.apache.commons.dbutils.handlers.AbstractKeyedHandler;
import org.apache.commons.lang.reflect.FieldUtils;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 处理 行业分析-估值水平
 * User: wenzhihong
 * Date: 12-12-15
 * Time: 上午10:33
 */
@On("cron.InduCompareAppraisementTrigger")
@JobDesc(desc = "行业分析-估值水平")
public class InduCompareAppraisementJob extends Job {
     private static final String[] redisKeys = {
            RedisKey.IndustryAna.appraisement_sec,
            RedisKey.IndustryAna.appraisement,
            RedisKey.IndustryAna.appr_full_indu
     };
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"行业分析-估值水平");
        StopWatch sw = new StopWatch("行业分析-估值水平");
        sw.start();
        appraisement();
        sw.stop();
        Logger.info(sw.prettyPrint());
    }

    private void appraisement(){
        String allDataSql = SqlLoader.getSqlById("appraisementLatestData");
        List<AppraisementFullItem> fullItemList = ExtractDbUtil.queryExtractDBBeanList(allDataSql, AppraisementFullItem.class);

        String sql = SqlLoader.getSqlById("BVPSLatest"); //最新的每股净资产
        Map<Long,Double> navMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new AbstractKeyedHandler<Long, Double>() {
            @Override
            protected Long createKey(ResultSet rs) throws SQLException {
                return rs.getLong("institutionId");
            }

            @Override
            protected Double createRow(ResultSet rs) throws SQLException {
                return rs.getDouble("nav");
            }
        });

        //计算 pbCur ( 市净率(现值)) = closePrice(最新收盘价) / nav(每股净资产)
        for (AppraisementFullItem item : fullItemList) {
            BondSec sec = BondSec.fetchSecBySecId(item.secId);
            if (sec != null) {
                 Double nav = navMap.get(sec.institutionId);
                if (nav != null) {
                    item.pbCur = item.closePrice / nav.doubleValue();
                }else {
                    item.pbCur = 0;
                }
            }else {
                item.pbCur = 0;
            }
        }

        //按二级行业分组. 以二级行业代码做为key
        LinkedListMultimap<String, AppraisementFullItem> indu2MutiMap = LinkedListMultimap.create();

        for (AppraisementFullItem item : fullItemList) {
            String induCodeL2 = BondSec.fetchInduLevel2CodeBySecId(item.secId);
            if (induCodeL2 != null) {
                indu2MutiMap.put(induCodeL2, item);
            }
        }

        Gson gson = CommonUtils.createGson();

        for (String induCode : indu2MutiMap.keySet()) {
            //排序
            List<AppraisementFullItem> sameInduCodeList = indu2MutiMap.get(induCode); //同行业的
            Collections.sort(sameInduCodeList, new Comparator<AppraisementFullItem>() {
                @Override
                public int compare(AppraisementFullItem o1, AppraisementFullItem o2) {
                    return ComparisonChain.start()
                            .compare(o1.pe1a, o2.pe1a)
                            .compare(o1.pe1ttm, o2.pe1ttm)
                            .result();
                }
            });

            //设置到redis上.
            //设置简要数据
            List<AppraisementRankItem> pre6RankList = Lists.newArrayListWithCapacity(6);
            int rank = 1;
            for (AppraisementFullItem item : sameInduCodeList) {
                AppraisementRankItem rankItem = new AppraisementRankItem();
                rankItem.pe1a = item.pe1a;
                rankItem.secId = item.secId;
                rankItem.rank = rank;
                if(rank <= 6){
                    pre6RankList.add(rankItem);
                }

                item.rank = rank;

                rank++;
                RedisUtil.set(RedisKey.IndustryAna.appraisement_sec + rankItem.secId, rankItem, gson);
            }

            RedisUtil.set(RedisKey.IndustryAna.appraisement + induCode, pre6RankList, gson); //设置行业前6名

            //设置更多展开所要的数据
            if(sameInduCodeList != null && sameInduCodeList.size() > 0) {
                AppraisementFullItem avgObject = avgObj(sameInduCodeList);//平均值
                AppraisementFullItem middleObject = middleObj(sameInduCodeList); //中位值
                AppraisementFullIndu indu = new AppraisementFullIndu();
                indu.list = sameInduCodeList;
                indu.avg = avgObject;
                indu.middle = middleObject;

                RedisUtil.set(RedisKey.IndustryAna.appr_full_indu + induCode, indu, gson);
            }
        }
    }

    /**
     * 计算平均值
     */
    private AppraisementFullItem avgObj(List<AppraisementFullItem> items){
        //计算这个行业内的总的市值
        double totalMarketValue = 0;
        for (AppraisementFullItem it : items) {
            totalMarketValue += it.marketValue;
        }
        AppraisementFullItem avgItem = new AppraisementFullItem();
        calcAvgValWithField(items, avgItem, "pe1a", totalMarketValue);
        calcAvgValWithField(items, avgItem, "pe1ttm", totalMarketValue);
        calcAvgValWithField(items, avgItem, "psa", totalMarketValue);
        calcAvgValWithField(items, avgItem, "psttm", totalMarketValue);
        calcAvgValWithField(items, avgItem, "pbva", totalMarketValue);
        calcAvgValWithField(items, avgItem, "pbCur", totalMarketValue);
        calcAvgValWithField(items, avgItem, "pcfa", totalMarketValue);
        calcAvgValWithField(items, avgItem, "pcfttm", totalMarketValue);
        calcAvgValWithField(items, avgItem, "evtoebitdattm", totalMarketValue);
        calcAvgValWithField(items, avgItem, "evtoebitda", totalMarketValue);

        return avgItem;
    }

    private void calcAvgValWithField(List<AppraisementFullItem> items, AppraisementFullItem target, String fieldName, double totalMarketValue){
        try {
            Field field = FieldUtils.getField(AppraisementFullItem.class, fieldName, true);
            double total = 0;
            for (AppraisementFullItem it : items) {
                double fv = (Double)FieldUtils.readField(field, it, true);
                total += (it.marketValue / totalMarketValue) * fv;
            }

            double avg = total / items.size();

            FieldUtils.writeField(field, target, avg, true);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }


    }

    /**
     * 计算中值
     */
    private AppraisementFullItem middleObj(List<AppraisementFullItem> items){
        AppraisementFullItem middleItem = new AppraisementFullItem();
        calcMiddleValWithField(items, middleItem, "pe1a");
        calcMiddleValWithField(items, middleItem, "pe1ttm");
        calcMiddleValWithField(items, middleItem, "psa");
        calcMiddleValWithField(items, middleItem, "psttm");
        calcMiddleValWithField(items, middleItem, "pbva");
        calcMiddleValWithField(items, middleItem, "pbCur");
        calcMiddleValWithField(items, middleItem, "pcfa");
        calcMiddleValWithField(items, middleItem, "pcfttm");
        calcMiddleValWithField(items, middleItem, "evtoebitdattm");
        calcMiddleValWithField(items, middleItem, "evtoebitda");

        return middleItem;
    }

    private void calcMiddleValWithField(List<AppraisementFullItem> items, AppraisementFullItem target, String fieldName ){
        try {
            BeanComparator c = new BeanComparator(fieldName, doubleCompare);
            Collections.sort(items, c);
            Field field = FieldUtils.getField(AppraisementFullItem.class, fieldName, true);

            if (items.size() % 2 == 1) { //奇数个就是中间那个
                AppraisementFullItem src = items.get(items.size() / 2);
                FieldUtils.writeField(field, target, FieldUtils.readField(field, src, true), true);
            } else { //偶数个就是中间两个计算平均值
                AppraisementFullItem src1 = items.get(items.size() / 2);
                AppraisementFullItem src2 = items.get(items.size() / 2 - 1);
                double d1 = (Double) FieldUtils.readField(field, src1, true);
                double d2 = (Double) FieldUtils.readField(field, src2, true);

                FieldUtils.writeField(field, target, (d1 + d2)/2, true);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    static DoubleCompare doubleCompare = new DoubleCompare();

    private static class DoubleCompare implements Comparator<Double> {
        @Override
        public int compare(Double o1, Double o2) {
            return Doubles.compare(o1, o2);
        }
    }

}
