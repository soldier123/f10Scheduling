package jobs;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.BeanMultiMapHandler;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.IndexInfo;
import dto.newestinfo.*;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.AbstractListHandler;
import org.apache.commons.dbutils.handlers.BeanMapHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;
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
 * 最新动态
 * User: wenzhihong
 * Date: 12-11-23
 * Time: 上午10:18
 */
@On("cron.NewestInfoTrigger")
@JobDesc(desc = "处理最新动态内容,设置到redis里")
public class NewestInfoJob extends Job {
    private static final String[] redisKeys = {
        RedisKey.NewestInfo.markup_stock,
        RedisKey.NewestInfo.markup_index,
        RedisKey.NewestInfo.concept_plate,
        RedisKey.NewestInfo.invest_Mainpoint,
        RedisKey.NewestInfo.great_InventRemind,
        RedisKey.NewestInfo.top3MainBusiness,
        RedisKey.NewestInfo.financialRatios_short,
        RedisKey.NewestInfo.financial_draw_data,
        RedisKey.NewestInfo.report_rating
    };
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"最新动态");
        StopWatch sw = new StopWatch("最新动态");
        sw.start("投资要点");
        investMainpoint();
        sw.stop();

        sw.start("大事提醒");
        greatInventRemind();
        sw.stop();

        sw.start("三大主营业务");
        top3MainBusiness();
        sw.stop();

        sw.start("综合评级");
        reportRating();
        sw.stop();

        sw.start("财务比率");
        financialRatios();
        sw.stop();

        sw.start("财务画图数据");
        financeDrawData();
        sw.stop();

        sw.start("处理行情涨幅");
        quoteMarkup2();
        sw.stop();

        sw.start("处理股票概念板块");
        stockConceptPlate();
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

    //处理股票概念板块
    private void stockConceptPlate() {
        String sql = SqlLoader.getSqlById("stockConceptPlate");
        ListMultimap<String, PlateDto> itemMapList =
                ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<String, PlateDto>(PlateDto.class, "scode", BondSec.secMap.size()));

        Gson gson = CommonUtils.createGson();
        cleanDirtyData();
        for (String scode : itemMapList.keySet()) {
            List<PlateDto> plates = new ArrayList<PlateDto>(itemMapList.get(scode)); //这里要用ArrayList包装一下, 不然输出不了json
            RedisUtil.set(RedisKey.NewestInfo.concept_plate + scode, plates, gson);
        }
    }

    //处理行情涨幅, 不用自己计算, 直接取的
    private void quoteMarkup2() {
        String df = "yyyy-MM-dd";
        //上证指数的最新日期,以这个上证指数为准,这就不会错了.
        String maxDateSql = SqlLoader.getSqlById("index_shangzhen_maxdate_2");
        String fromDate = DateFormatUtils.format(DateUtils.addDays(new Date(), -20), df); //从今天往前数20天做为起始日期
        String endDate = "2112-12-21"; //hehe, 世界末日的100年后啊
        Date maxDate = ExtractDbUtil.queryExtractDbWithHandler(maxDateSql, new ScalarHandler<Date>(), fromDate, endDate);

        if (maxDate != null) {
            String lastDate = DateFormatUtils.format(maxDate, df); //最新的日期

            //计算指数类的
            String indexSql = SqlLoader.getSqlById("indexQuoteMarkIn_2").replaceAll("#index_list#", IndexInfo.index_code);
            List<QuoteMarkup> indexQuoteList = ExtractDbUtil.queryExtractDBBeanList(indexSql, QuoteMarkup.class, lastDate);

            Gson gson = CommonUtils.createGson();
            //把指数的set到redis上
            for (QuoteMarkup markup : indexQuoteList) {
                String scode = markup.scode;
                markup.scode = null;
                RedisUtil.set(RedisKey.NewestInfo.markup_index + scode, markup, gson);
            }

            //计算股票, 分成多个计算, 这样才能用上索引, 才会快
            String stockSqlTpl = SqlLoader.getSqlById("stockQuoteMarkInDate_2");
            for(String secIdGroup : BondSec.secIdGroupArr){
                String sql = stockSqlTpl.replaceAll("#secIdGroup#", secIdGroup);
                List<QuoteMarkup> quoteMarkupList = ExtractDbUtil.queryExtractDBBeanList(sql, QuoteMarkup.class, lastDate);
                for (QuoteMarkup q : quoteMarkupList) {
                    RedisUtil.set(RedisKey.NewestInfo.markup_stock + q.scode, q, gson);
                }
            }
        }

    }

    //综合评级
    private void reportRating(){
        String sql = SqlLoader.getSqlById("top1LastReportRatingStatisticWithSelfStatistic");
        //Map<Long, ReportRatingStatisticDto> ratingMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMapHandler<Long, ReportRatingStatisticDto>(ReportRatingStatisticDto.class, "secId"));

        //提取值
        Map<Long, ReportRatingStatisticDto> ratingMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new ResultSetHandler<Map<Long, ReportRatingStatisticDto>>(){

            @Override
            public Map<Long, ReportRatingStatisticDto> handle(ResultSet resultSet) throws SQLException {
                Map<Long, ReportRatingStatisticDto> map = Maps.newHashMap();
                long preSecId = Long.MAX_VALUE;
                long curSecId = Long.MAX_VALUE;
                String standardRating = "";
                ReportRatingStatisticDto reportRatingStatisticDto = null;
                int numCount = 0;
                while (resultSet.next()) {
                    curSecId = resultSet.getLong("secId");
                    standardRating = resultSet.getString("standardRating").trim();
                    numCount = resultSet.getInt("numCount");
                    if (curSecId != preSecId) {
                        reportRatingStatisticDto = new ReportRatingStatisticDto();
                        map.put(curSecId, reportRatingStatisticDto);
                    }

                    if ("买入".equals(standardRating)) {
                        reportRatingStatisticDto.buy = numCount;
                    }else if ("增持".equals(standardRating)) {
                        reportRatingStatisticDto.outperform = numCount;
                    } else if ("中性".equals(standardRating)) {
                        reportRatingStatisticDto.neutral = numCount;
                    }else if ("减持".equals(standardRating)) {
                        reportRatingStatisticDto.underperform = numCount;
                    }else if ("卖出".equals(standardRating)) {
                        reportRatingStatisticDto.sell = numCount;
                    }

                    preSecId = curSecId;
                }
                return map;
            }
        });

        Set<Long> secIdSet = Sets.newHashSet(BondSec.secIdToCodeMap.keySet()); //没有记录的
        Gson gson  = CommonUtils.createGson();
        //设置到redis上
        for (Long secId : ratingMap.keySet()) {
            RedisUtil.set(RedisKey.NewestInfo.report_rating + secId, ratingMap.get(secId), gson);
            secIdSet.remove(secId);
        }
        //去除掉没有匹配的记录
        RedisUtil.del(secIdSet, RedisKey.NewestInfo.report_rating);
    }

    //投资要点
    private void investMainpoint() {
        String sql = SqlLoader.getSqlById("investMainpoint");
        List<String[]> list = ExtractDbUtil.queryExtractDbWithHandler(sql, new AbstractListHandler<String[]>() {
            @Override
            protected String[] handleRow(ResultSet rs) throws SQLException {
                String[] objArr = new String[2];
                objArr[0] = rs.getString("scode");
                objArr[1] = rs.getString("mainBusiness");
                return objArr;
            }
        });

        for (String[] item : list) {
            RedisUtil.pureSet(RedisKey.NewestInfo.invest_Mainpoint + item[0], item[1]); //key对应的直接就是字符串的value
        }
    }

    //大事提醒
    private void greatInventRemind(){
        Gson gson = CommonUtils.createGson();
        String sqlTpl = SqlLoader.getSqlById("greatInventRemind");

        for (String secIdGroup : BondSec.secIdGroupArr) {
            String sql = sqlTpl.replaceAll("#secIdGroup#", secIdGroup);
            ListMultimap<Long, GreatInventRemind> itemMapList =
                    ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, GreatInventRemind>(GreatInventRemind.class, "secId", BondSec.secMap.size()));

            for (Long secId : itemMapList.keySet()) {
                List<GreatInventRemind> reminds = new ArrayList<GreatInventRemind>(itemMapList.get(secId)); //这里要用ArrayList包装一下, 不然输出不了json
                RedisUtil.set(RedisKey.NewestInfo.great_InventRemind + secId, reminds, gson);
            }
        }
    }

    //过滤掉名称中含有总计,合计的
    private boolean filterName(String itemName){
        if(itemName == null){
            return false;
        }else{
            boolean has = itemName.indexOf("总计") >= 0 || itemName.indexOf("合计") >= 0;
            return !has;
        }
    }

    /**
     * 三大主营业务
     */
    private void top3MainBusiness(){
        String sql = SqlLoader.getSqlById("top3MainBusiness");
        ListMultimap<Long, Top3MainBusinessDto> itemMapList =
                ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMultiMapHandler<Long, Top3MainBusinessDto>(Top3MainBusinessDto.class, "institutionId", BondSec.secMap.size()));

        Gson gson = CommonUtils.createGson();
        for (Long institutionId : itemMapList.keySet()) {
            List<Top3MainBusinessDto> itemList = itemMapList.get(institutionId);
            //过滤, 先取 category = 3的, 如果没有的话, 取 category=1的. 请结合sql一起查看
            List<Top3MainBusinessDto> fillterList = Lists.newArrayList();
            int filterCategory = 1; //筛选的类型
            if(itemList != null && itemList.size() > 0){
                filterCategory = itemList.get(0).category;
            }
            for (Top3MainBusinessDto t : itemList) {
                if(filterCategory == t.category && filterName(t.itemName)){
                    fillterList.add(t);
                }
            }

            List<Top3MainBusinessDto> top3MainBusinessDtos = null;
            if(fillterList.size() < 1){
                top3MainBusinessDtos = new ArrayList<Top3MainBusinessDto>(0);
            }else if(fillterList.size() > 3){
                top3MainBusinessDtos = new ArrayList<Top3MainBusinessDto>(fillterList.subList(0, 3));
            }else{
                top3MainBusinessDtos = new ArrayList<Top3MainBusinessDto>(fillterList);
            }

            RedisUtil.set(RedisKey.NewestInfo.top3MainBusiness + institutionId, top3MainBusinessDtos, gson);
        }
    }

    //财务比率
    private void financialRatios(){
        String startReportDate = CommonUtils.calcReportDate(new Date(), -16); //往前推16期的, 这里的要求是5期, 有这么大的间隔,应该是可以了

        String sql = SqlLoader.getSqlById("financialRatiosShort");
        Gson gson = CommonUtils.createGson();

        //按机构id进行分组来计算. 不按每个股票计算, 这样可以减少访问数据库的次数
        for (String instiIdGroup : BondSec.institutionIdGroupArr) {

            Object[] params = new Object[2]; //sql的参数
            params[0] = startReportDate;
            params[1] = startReportDate;

            String newSql = sql.replaceAll("#orgIdList#", instiIdGroup);

            ListMultimap<Long, FinancialRatiosDto> itemMapList =
                    ExtractDbUtil.queryExtractDbWithHandler(newSql, new BeanMultiMapHandler<Long, FinancialRatiosDto>(FinancialRatiosDto.class, "institutionId", 16 * BondSec.GROUP_SIZE), params);

            //设置到redis上
            for (Long institutionId : itemMapList.keySet()) {
                List<FinancialRatiosDto> dtoList = new ArrayList<FinancialRatiosDto>( itemMapList.get(institutionId) );
                RedisUtil.set(RedisKey.NewestInfo.financialRatios_short + institutionId, dtoList, gson);
            }
        }
    }

    //财务画图数据
    private void financeDrawData(){
        String sql = SqlLoader.getSqlById("lastPartFinanceDataShort");
        String eachStockFinanceDataSql = SqlLoader.getSqlById("eachStockFinanceData"); //每只股票
        List<FinanceDataShortDto> lastDataList = ExtractDbUtil.queryExtractDBBeanList(sql, FinanceDataShortDto.class);
        Gson gson = CommonUtils.createGson();
        for (FinanceDataShortDto dto : lastDataList) {
            if (dto.endDate != null) {
                long institutionId = dto.institutionId;
                StringBuilder sb = new StringBuilder();
                String reportDateStr;
                int year = Integer.parseInt(dto.endDate.substring(0,4));
                String monthDay = dto.endDate.substring(4);

                if("-12-31".equals(monthDay)){ //年报, 取5年
                    for(int i=0; i < 5; i++){
                        sb.append("'" + (year - i) + "-12-31',");
                    }
                }else{//取当期,并且每年的年报
                    for(int i=1; i < 5; i++){ //以前4年的年报及当期
                        sb.append("'" + (year - i) + "-12-31',");
                        sb.append("'" + (year - i) + monthDay + "'," );
                    }
                    //取最后一季度的数据 修复bugliujl
                    sb.append("'" + dto.endDate +"',");
                }

                reportDateStr = sb.substring(0, sb.length()-1);

                String newSql = eachStockFinanceDataSql.replaceAll("#reportDateStr#", reportDateStr);
                List<FinanceDataShortDto> dataList = ExtractDbUtil.queryExtractDBBeanList(newSql, FinanceDataShortDto.class, institutionId);
                RedisUtil.set(RedisKey.NewestInfo.financial_draw_data + institutionId, dataList, gson);
            }
        }
    }

    public void cleanDirtyData(){
        String keyPattern = RedisKey.NewestInfo.concept_plate;
        Set<String> keySet = Redis.keys(keyPattern + "*");
        for(String key : keySet){
            Redis.del(new String[]{key});
        }
    }
}
