package jobs;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.industryana.SecEps;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import play.modules.redis.Redis;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 行业分析 -- 财务状况
 * User: wenzhihong
 * Date: 12-12-15
 * Time: 上午11:58
 */
@On("cron.InduCompareFinanceStatusTrigger")
@JobDesc(desc = "行业分析 -- 财务状况")
public class InduCompareFinanceStatusJob extends Job{
    private static final String[] redisKeys={RedisKey.IndustryAna.financeStatus,RedisKey.IndustryAna.financeStatus_sec};
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"行业分析 -- 财务状况");
        StopWatch sw = new StopWatch("行业分析 -- 财务状况");
        sw.start();
        String lastReportDateSql = SqlLoader.getSqlById("financeLatelyReportDate");
        String financeStatsSql = SqlLoader.getSqlById("financeStats");
        Gson gson = CommonUtils.createGson();
        for (String induCode : BondSec.fetchLevelTwoInduCodeList()) { //证监会行业二级代码
            Date maxDate = ExtractDbUtil.queryExtractDbWithHandler(lastReportDateSql, new ScalarHandler<Date>(), induCode);
            if(maxDate != null){
                String startDate = CommonUtils.calcReportDate(maxDate, -4); //往前计算3期
                ExtractHandler extract =  new ExtractHandler();
                ExtractDbUtil.queryExtractDbWithHandler(financeStatsSql, extract, startDate, induCode, startDate, induCode);
                HashMap<Long, SecEps> secEpsHashMap = extract.addRankAndSortItem();
                List<SecEps> pre6SecEpsList = extract.pre6(); //前6名

                //设置到redis上
                RedisUtil.set(RedisKey.IndustryAna.financeStatus + induCode, pre6SecEpsList, gson);

                for (Long institutionId : secEpsHashMap.keySet()) {
                    RedisUtil.set(RedisKey.IndustryAna.financeStatus_sec + institutionId, secEpsHashMap.get(institutionId), gson);
                }
            }
        }
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

    private class ExtractHandler implements ResultSetHandler<Object>{
        //公司排名. 公司id做为key, 排名做为value. 这里使用linkedHashMap这样就可以有顺序了
        LinkedHashMap<Long, Integer> rankMap = Maps.newLinkedHashMap();

        //公司的eps, 公司id做为key
        HashMap<Long, SecEps> secEpsMap = Maps.newHashMap();

        @Override
        public Object handle(ResultSet rs) throws SQLException {
            int rank = 1; //排名
            while (rs.next()){
                long institutionId = rs.getLong("INSTITUTIONID");
                Date endDate = rs.getDate("ENDDATE");
                double eps = rs.getDouble("EPS");
                if(! rankMap.containsKey(institutionId)){ //不包含这个公司
                    rankMap.put(institutionId, rank);
                    rank++;
                }

                SecEps secEps = secEpsMap.get(institutionId);
                if(secEps == null){
                    secEps = new SecEps();
                    secEpsMap.put(institutionId, secEps);
                }
                secEps.institutionId = institutionId;
                SecEps.Item item = new SecEps.Item();
                item.endDate = endDate;
                item.eps = eps;
                secEps.addItem(item);
            }

            return null;
        }

        //把排名数据增加进去. 并把里面的item项排序
        public HashMap<Long, SecEps> addRankAndSortItem(){
            for (Long id : secEpsMap.keySet()) {
                SecEps secEps =secEpsMap.get(id);
                secEps.rank = rankMap.get(id);
                secEps.sortItems();
            }

            return secEpsMap;
        }

        //返回前6名
        public List<SecEps> pre6(){
            List<SecEps> epsList = Lists.newLinkedList();
            int count = 1;
            for (Long id : rankMap.keySet()) {
                if(count > 6){ //已经有6名了, 跳出
                    break;
                }
                epsList.add(secEpsMap.get(id));
                count++;
            }
            return epsList;
        }
    }
}
