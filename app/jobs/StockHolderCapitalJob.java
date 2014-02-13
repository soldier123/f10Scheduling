package jobs;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Longs;
import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.BeanMultiMapHandler;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.stockholdercapital.CapitalStructure;
import dto.stockholdercapital.LimitedAndLift;
import dto.stockholdercapital.OrgGroupSumHolder;
import dto.stockholdercapital.StockHolderDto;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanMapHandler;
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
 * 股本股东定时任务处理
 * User: wenzhihong
 * Date: 12-12-8
 * Time: 下午1:40
 */
@On("cron.StockHolderCapitalTrigger")
@JobDesc(desc = "股本股东处理")
public class StockHolderCapitalJob extends Job {
    private static final String[] redisKeys = {
            RedisKey.StockHolderCapital.stockHolderTop10,
            RedisKey.StockHolderCapital.orgStockHolder,
            RedisKey.StockHolderCapital.stockFlowHolderTop10,
            RedisKey.StockHolderCapital.orgStockFlowHolder,
            RedisKey.StockHolderCapital.capitalStructure,
            RedisKey.StockHolderCapital.limitAndLift
    };
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"股本股东");
        StopWatch sw = new StopWatch("股本股东处理定时任务");
        Gson gson = CommonUtils.createGson();
        sw.start("处理10大股东");
        stockHolderTop10(gson);
        sw.stop();

        sw.start("10大流通股东");
        stockFlowHolderTop10(gson);
        sw.stop();

        sw.start("机构类型合计持股");
        stockOrgGroupSumHolder(gson);
        sw.stop();

        sw.start("机构股东");
        orgStockHolder(gson);
        sw.stop();

        sw.start("机构流通股东");
        //orgStockFlowHolder(gson);
        sw.stop();
        
        sw.start("股本结构");
        stockStruct2(gson);
        sw.stop();

        sw.start("限售解禁");
        limitAndLift(gson);
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

    //限售解禁
    private void limitAndLift(Gson gson) {
        String sqlTpl = SqlLoader.getSqlById("limitAndLift");
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgGroup#", orgGroup);
            ListMultimap<Long, LimitedAndLift> infoListMultimap = ExtractDbUtil.queryExtractDbWithHandler(sql,
                    new BeanMultiMapHandler<Long, LimitedAndLift>(LimitedAndLift.class, "institutionId"));
            for (Long institutionId : infoListMultimap.keySet()) {
                //处理每一个公司
                List<LimitedAndLift> lifts = infoListMultimap.get(institutionId);
                if (lifts != null && lifts.size() > 0) {
                    List<LimitedAndLift> limitedList = Lists.newLinkedList(); //这个是限售解禁的list
                    if (lifts.size() == 1) {
                        LimitedAndLift[] dataArr = lifts.toArray(new LimitedAndLift[1]);
                        dataArr[0].liftNum=dataArr[0].tradeSharesTotal;
                        limitedList.add(dataArr[0]);
                    } else {

                        LimitedAndLift[] dataArr = lifts.toArray(new LimitedAndLift[lifts.size()]);
                        for (int i = 1; i <dataArr.length; i++) {
                            if (dataArr[i - 1].tradeSharesTotal != dataArr[i].tradeSharesTotal) { //前一期跟本期不等,也就是有发生变化,放入进来
                                dataArr[i - 1].liftNum = dataArr[i - 1].tradeSharesTotal - dataArr[i].tradeSharesTotal;
                                limitedList.add(dataArr[i - 1]);
                            }
                        }
                        //最后一次没有对比,把自己的数据保存进去
                        dataArr[dataArr.length-1].liftNum = dataArr[dataArr.length-1].tradeSharesTotal;
                        limitedList.add(dataArr[dataArr.length-1]);//最后面一次 , 没有对比,把自己添加进去

                    }
                    RedisUtil.set(RedisKey.StockHolderCapital.limitAndLift + institutionId, limitedList, gson);
                }
            }
        }
    }

    private void stockStruct2(Gson gson) {
        String sql = SqlLoader.getSqlById("stockStruct2");
        Map<Long, CapitalStructure> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMapHandler<Long, CapitalStructure>(CapitalStructure.class, "institutionId"));
        for (Map.Entry<Long, CapitalStructure> entry : infoMap.entrySet()) {
            RedisUtil.set(RedisKey.StockHolderCapital.capitalStructure + entry.getKey(), entry.getValue(), gson);
        }
    }

    //机构流通股东
    private void orgStockFlowHolder(Gson gson) {
        String sqlTpl = SqlLoader.getSqlById("orgStockFlowHolder").replaceAll("#typeIdGroup#", OrgGroupSumHolder.typeGroup);
        String endDate = CommonUtils.calcReportDate(new Date(), -4); //往前取4期
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgGroup#", orgGroup);
            ListMultimap<Long, StockHolderDto> holderListMultimap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, StockHolderDto>(StockHolderDto.class, "institutionId"), endDate, endDate);
            //设置到redis上
            for (Long institutionId : holderListMultimap.keySet()) {
                List<StockHolderDto> stockHolderDtos = filterAndCalcList(holderListMultimap.get(institutionId));
                RedisUtil.set(RedisKey.StockHolderCapital.orgStockFlowHolder + institutionId, stockHolderDtos, gson);
            }
        }
    }



    //机构股东
    private void orgStockHolder(Gson gson) {
        String sqlTpl = SqlLoader.getSqlById("orgStockHolder").replaceAll("#typeIdGroup#", OrgGroupSumHolder.typeGroup);
        String endDate = CommonUtils.calcReportDate(new Date(), -4); //往前取4期

        cleanDirtyData(RedisKey.StockHolderCapital.orgStockHolder);
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgGroup#", orgGroup);
            ListMultimap<Long, StockHolderDto> holderListMultimap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, StockHolderDto>(StockHolderDto.class, "institutionId"), endDate, endDate);
            //设置到redis上
            for (Long institutionId : holderListMultimap.keySet()) {
                List<StockHolderDto> stockHolderDtos = filterAndCalcList(holderListMultimap.get(institutionId));
                RedisUtil.set(RedisKey.StockHolderCapital.orgStockHolder + institutionId, stockHolderDtos, gson);
            }
        }
    }

    public void cleanDirtyData(String rediskey){
        String keyPattern = rediskey;
        Set<String> keySet = Redis.keys(keyPattern + "*");
        for(String key : keySet){
            Redis.del(new String[]{key});
        }
    }

    //筛选并计算变化值
    private List<StockHolderDto> filterAndCalcList(List<StockHolderDto> stockHolderDtos) {
        if(stockHolderDtos == null || stockHolderDtos.size() == 0){
            return Lists.newArrayList();
        }

        Date firstDate = null; //最大日期
        Date secondDate = null; //第二大日期
        TreeSet<Date> dates = Sets.newTreeSet();
        for (StockHolderDto dto : stockHolderDtos) {
            dates.add(dto.endDate);
        }
        firstDate = dates.pollLast();
        secondDate = dates.pollLast();

        List<StockHolderDto> firstDateItemList = Lists.newArrayListWithCapacity(10);
        List<StockHolderDto> secondDateItemList = Lists.newArrayListWithCapacity(10);

        for (StockHolderDto dto : stockHolderDtos) {
            if(dto.endDate.equals(firstDate)){
                firstDateItemList.add(dto);
            }else if(dto.endDate.equals(secondDate)){
                secondDateItemList.add(dto);
            }
        }

        //计算变化
        for (StockHolderDto dto : firstDateItemList) {
            StockHolderDto findItem = null;
            for (StockHolderDto sh : secondDateItemList) {
                //先比较 holderId
                if(dto.holderId != 0 && sh.holderId != 0 && dto.holderId == sh.holderId){//找到
                    findItem = sh;
                    break;
                }

                if(dto.holderName != null && dto.holderName.equals(sh.holderName)){ //找到
                    findItem = sh;
                    break;
                }
            }
            if(findItem != null){
                dto.change = dto.holdNum - findItem.holdNum;
            }


            //先给这里吧, 就造一点冗余的数据. 不然只设置一个比较麻烦
            dto.firstDate = firstDate;
            dto.secondDate = secondDate;
        }

        //排序,按持股数降序排列
        Collections.sort(firstDateItemList, new Comparator<StockHolderDto>() {
            @Override
            public int compare(StockHolderDto o1, StockHolderDto o2) {
                return Longs.compare(o2.holdNum, o1.holdNum);
            }
        });

        return firstDateItemList;
    }

    //10大股东
    private void stockHolderTop10(Gson gson){
        String sqlTpl = SqlLoader.getSqlById("stockHolderTop10");
        String endDate = CommonUtils.calcReportDate(new Date(), -4); //往前取4期

        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgGroup#", orgGroup);
            ListMultimap<Long, StockHolderDto> holderListMultimap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, StockHolderDto>(StockHolderDto.class, "institutionId"), endDate, endDate);
            //设置到redis上
            for (Long institutionId : holderListMultimap.keySet()) {
                List<StockHolderDto> stockHolderDtos = filterAndCalcList(holderListMultimap.get(institutionId));
                RedisUtil.set(RedisKey.StockHolderCapital.stockHolderTop10 + institutionId, stockHolderDtos, gson);
            }
        }
    }

    //10大流通股东
    private void stockFlowHolderTop10(Gson gson){
        String sqlTpl = SqlLoader.getSqlById("stockFlowHolderTop10");
        String endDate = CommonUtils.calcReportDate(new Date(), -4); //往前取4期
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgGroup#", orgGroup);
            ListMultimap<Long, StockHolderDto> holderListMultimap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, StockHolderDto>(StockHolderDto.class, "institutionId"), endDate, endDate);
            //设置到redis上
            for (Long institutionId : holderListMultimap.keySet()) {
                List<StockHolderDto> stockHolderDtos = filterAndCalcList(holderListMultimap.get(institutionId));
                RedisUtil.set(RedisKey.StockHolderCapital.stockFlowHolderTop10 + institutionId, stockHolderDtos, gson);
            }
        }
    }

    //机构类型合计持股
    private void stockOrgGroupSumHolder(Gson gson){
        String sqlTpl = SqlLoader.getSqlById("stockTypeSumHold").replaceAll("#typeIdGroup#", OrgGroupSumHolder.typeGroup);
        String sdate = CommonUtils.calcReportDate(new Date(), -6); //取最近6期
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            String sql = sqlTpl.replaceAll("#orgGroup#", orgGroup);
            //公司id作为主键,每种机构都做为一项. 画图数据
            HashMap<Long, List<OrgGroupSumHolder>> orgHoldListMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new StockTypeSumHoldResultSetHandler(), sdate, sdate);
            for (Map.Entry<Long, List<OrgGroupSumHolder>> entry : orgHoldListMap.entrySet()) {
                RedisUtil.set(RedisKey.StockHolderCapital.stockOrgGroupSumHolder + entry.getKey(), entry.getValue(), gson);
            }
        }
    }

    private static class StockTypeSumHoldResultSetHandler implements ResultSetHandler<HashMap<Long, List<OrgGroupSumHolder>>>{
        @Override
        public HashMap<Long, List<OrgGroupSumHolder>> handle(ResultSet rs) throws SQLException {
            HashMap<Long, List<OrgGroupSumHolder>> orgHoldListMap = Maps.newHashMap();
            long preInstitutionId = Long.MAX_VALUE;
            Date preEndDate = CommonUtils.parseDate("1911-10-10"); //看看你的历史学得怎么样, 猜猜这是什么时间
            int perCompanyStageCount = 0; //每个公司期数记数器
            while (rs.next()) {
                long institutionId = rs.getLong("institutionId");
                Date endDate = rs.getDate("endDate");
                if (preInstitutionId != institutionId) { //新的一家公司
                    perCompanyStageCount = 1;
                } else { //同一家公司
                    if (!preEndDate.equals(endDate)) { //日期不同
                        perCompanyStageCount++;
                    }
                }
                if (perCompanyStageCount > 4) { //已够4期
                    preInstitutionId = institutionId;
                    preEndDate = endDate;
                    continue;
                }

                OrgGroupSumHolder tmp = new OrgGroupSumHolder(); //临时用一下
                tmp.endDate = endDate;
                tmp.typeId = rs.getString("typeId");
                tmp.sumRatio = rs.getDouble("sumRatio");
                tmp.orgCount = rs.getInt("orgCount");

                List<OrgGroupSumHolder> orgHolders = orgHoldListMap.get(institutionId);
                if (orgHolders == null) {
                    orgHolders = Lists.newArrayList();
                    orgHoldListMap.put(institutionId, orgHolders);
                }
                orgHolders.add(tmp);

                preInstitutionId = institutionId;
                preEndDate = endDate;
            }
            return orgHoldListMap;
        }
    }
}
