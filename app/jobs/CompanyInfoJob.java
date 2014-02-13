package jobs;

import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.companyinfo.*;
import org.apache.commons.dbutils.handlers.BeanMapHandler;
import play.Logger;
import play.exceptions.JavaExecutionException;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.util.Map;

/**
 * 公司概况
 * User: wenzhihong
 * Date: 12-10-24
 * Time: 上午10:43
 */
@On("cron.CompanyInfoTrigger")
@JobDesc(desc = "公司信息定时任务, 把所有的公司信息取出来,重新放入到redis上")
public class CompanyInfoJob extends Job {
    private static final String[] redisKeys = {
            RedisKey.CompanyInfo.institutioninfo,
            RedisKey.CompanyInfo.eqIpoInfo,
            RedisKey.CompanyInfo.eqIpoResult,
            RedisKey.CompanyInfo.sharesStructureInfo,
            RedisKey.CompanyInfo.marketQuotation,
            RedisKey.CompanyInfo.ipoMarketQuotation,
            RedisKey.CompanyInfo.agencyOrg
    };

    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"公司概况");
        Gson gson = CommonUtils.createGson();
        StopWatch sw = new StopWatch("公司信息");

        sw.start("公司基本信息");
        institutioninfo(gson);
        sw.stop();

        sw.start("ipo基本信息");
        eqIpoInfo(gson);
        sw.stop();

        sw.start("ipoResult信息");
        eqIpoResult(gson);
        sw.stop();

        sw.start("股本信息");
        sharesStructureInfo(gson);
        sw.stop();

        sw.start("市场行情");
        marketQuotation(gson);
        sw.stop();

        sw.start("承销商,代理机构");
        agencyOrg(gson);
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

    //公司基本信息
    private void institutioninfo(Gson gson){
        String sql = SqlLoader.getSqlById("institutioninfo");
        Map<Long, InstitutionInfoDto> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMapHandler<Long, InstitutionInfoDto>(InstitutionInfoDto.class, "institutionId"));
        for (Map.Entry<Long, InstitutionInfoDto> entry : infoMap.entrySet()) {
            RedisUtil.set(RedisKey.CompanyInfo.institutioninfo + entry.getKey() , entry.getValue(), gson);
        }
    }

    //ipo基本信息
    private void eqIpoInfo(Gson gson){
        String sql = SqlLoader.getSqlById("eqIpoInfo");
        Map<Long, EqIpoInfoDto> infoDtoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMapHandler<Long, EqIpoInfoDto>(EqIpoInfoDto.class, "secId"));
        for (Map.Entry<Long, EqIpoInfoDto> entry : infoDtoMap.entrySet()) {
            RedisUtil.set(RedisKey.CompanyInfo.eqIpoInfo + entry.getKey(), entry.getValue(), gson);
        }
    }

    //ipoResult信息
    private void eqIpoResult(Gson gson){
        String sql = SqlLoader.getSqlById("eqIpoResult");
        Map<Long, EqIpoResultDto> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMapHandler<Long, EqIpoResultDto>(EqIpoResultDto.class, "secId"));
        for (Map.Entry<Long, EqIpoResultDto> entry : infoMap.entrySet()) {
            RedisUtil.set(RedisKey.CompanyInfo.eqIpoResult + entry.getKey(), entry.getValue(), gson);
        }
    }

    //股本信息
    private void sharesStructureInfo(Gson gson){
        String sql = SqlLoader.getSqlById("sharesStructureInfo");
        Map<Long, SharesStructureInfoDto> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMapHandler<Long, SharesStructureInfoDto>(SharesStructureInfoDto.class, "institutionId"));
        for (Map.Entry<Long, SharesStructureInfoDto> entry : infoMap.entrySet()) {
            RedisUtil.set(RedisKey.CompanyInfo.sharesStructureInfo + entry.getKey(), entry.getValue(), gson);
        }
    }

    //市场行情
    private void marketQuotation(Gson gson){
        //最新的
        String sql = SqlLoader.getSqlById("marketQuotation");
        Map<Long, MarketQuotationDto> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMapHandler<Long, MarketQuotationDto>(MarketQuotationDto.class, "secId"));
        for (Map.Entry<Long, MarketQuotationDto> entry : infoMap.entrySet()) {
            RedisUtil.set(RedisKey.CompanyInfo.marketQuotation + entry.getKey(), entry.getValue(), gson);
        }

        //ipo上市首日的
        sql = SqlLoader.getSqlById("ipoMaketQuotation");
        infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMapHandler<Long, MarketQuotationDto>(MarketQuotationDto.class, "secId"));
        for (Map.Entry<Long, MarketQuotationDto> entry : infoMap.entrySet()) {
            RedisUtil.set(RedisKey.CompanyInfo.ipoMarketQuotation + entry.getKey(), entry.getValue(), gson);
        }
    }

    //承销商, 代理机构
    private void agencyOrg(Gson gson){
        String sql = SqlLoader.getSqlById("agencyOrg");
        Map<Long, AgencyOrgDto> infoMap = ExtractDbUtil.queryExtractDbWithHandler(sql, new BeanMapHandler<Long, AgencyOrgDto>(AgencyOrgDto.class, "secId"));
        for (Map.Entry<Long, AgencyOrgDto> entry : infoMap.entrySet()) {
            RedisUtil.set(RedisKey.CompanyInfo.agencyOrg + entry.getKey(), entry.getValue(), gson);
        }
    }

}
